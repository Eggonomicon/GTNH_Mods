package com.valier.tweaks;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerPickupXpEvent;
import net.minecraftforge.event.world.BlockEvent;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Spectator mode (2.3.9, config {@code spectator/spectatorMode}). Minecraft 1.7.10 has none; {@code /spectate} gives a
 * player one: flying everywhere, through blocks, untouchable and unseen, with hands off the world.
 * <ul>
 * <li><b>Through blocks:</b> 1.7.10 already lets an entity with {@code noClip} move through blocks
 * ({@code Entity.moveEntity}), skips pushing such a player out of blocks on their computer, and the server's "moved
 * wrongly" check lets them through. So the flag is set every tick on the server and, by the player's own Valier Tweaks
 * (2.3.9 or newer, which says so when it joins), on their computer, which also hides the "inside a block" overlay.</li>
 * <li><b>The rest is the server's:</b> flying forced on, no damage, night vision (to see inside rock), hidden with
 * ServerUtilities' vanish (its own command, so everything vanish does applies), and no breaking, placing, using,
 * attacking or picking up. Mobs drop them as a target.</li>
 * <li><b>Who:</b> ops and the server console, and players with the ServerUtilities permission
 * {@code valiertweaks.spectate}. The console and ops can switch others: {@code /spectate <player> [on|off]}.</li>
 * <li><b>Back:</b> {@code /spectate} again restores the player's flying, damage and vanish as they were; if they stop
 * inside a block they're lifted to the first spot above where they can stand. Saved with the player
 * ({@code ValierSpectator} in their persisted data), so it survives logging out.</li>
 * </ul>
 */
public final class Spectators {

    static final String TAG = "ValierSpectator";
    public static final String NODE = "valiertweaks.spectate";

    /** Players in spectator mode (server). */
    static final Set<UUID> active = new HashSet<>();
    /** Players whose game can pass through blocks (their Valier Tweaks said so); filled on netty's thread. */
    static final Set<UUID> capable = ConcurrentHashMap.newKeySet();
    private int ticks;

    public static boolean isSpectating(EntityPlayer p) {
        return p != null && !p.worldObj.isRemote && active.contains(p.getUniqueID());
    }

    static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** Ops, the console, and players with {@value #NODE}. */
    static boolean mayUse(ICommandSender s) {
        if (!(s instanceof EntityPlayerMP)) return s.canCommandSenderUseCommand(2, "spectate");
        EntityPlayerMP p = (EntityPlayerMP) s;
        if (PdimCommand.isOp(p)) return true;
        return Loader.isModLoaded("serverutilities") && Su.has(p, NODE);
    }

    // ---- on and off ------------------------------------------------------------------------------------------------

    static void enable(EntityPlayerMP p) {
        if (active.contains(p.getUniqueID())) {
            say(p, "You're already in spectator mode. /spectate again to stop.");
            return;
        }
        if (p.ridingEntity != null) p.mountEntity(null);
        NBTTagCompound t = new NBTTagCompound();
        t.setBoolean("On", true);
        t.setBoolean("Fly", p.capabilities.allowFlying);
        t.setBoolean("Flying", p.capabilities.isFlying);
        t.setBoolean("NoDamage", p.capabilities.disableDamage);
        t.setInteger("Dim", p.dimension);
        t.setDouble("X", p.posX);
        t.setDouble("Y", p.posY);
        t.setDouble("Z", p.posZ);
        boolean wasVanished = persisted(p).getBoolean("vanish");
        t.setBoolean("Vanished", !wasVanished && vanish(p, true));
        persisted(p).setTag(TAG, t);
        active.add(p.getUniqueID());
        apply(p, true);
        if (capable.contains(p.getUniqueID())) SpectatorMessage.send(p, true);
        say(p, "Spectator mode on: fly anywhere, through blocks; nothing can hurt or see you, and you can't touch anything."
            + " /spectate again to stop.");
        if (!capable.contains(p.getUniqueID())) say(p, "Your game's Valier Tweaks is older than 2.3.9, so you can fly but not pass"
            + " through blocks. Put the new valiertweaks.jar in your mods folder for that.");
        ValierTweaks.LOG.info("[spectate] {} is spectating (from {},{},{} in dimension {})", p.getCommandSenderName(), (int) p.posX, (int) p.posY,
            (int) p.posZ, p.dimension);
    }

    static void disable(EntityPlayerMP p) {
        if (!active.remove(p.getUniqueID())) {
            say(p, "You aren't in spectator mode.");
            return;
        }
        NBTTagCompound t = persisted(p).getCompoundTag(TAG);
        p.noClip = false;
        if (t.getBoolean("Vanished")) vanish(p, false);
        if (t.getBoolean("Night")) p.removePotionEffect(Potion.nightVision.id);
        boolean creative = p.capabilities.isCreativeMode;
        p.capabilities.allowFlying = t.getBoolean("Fly") || creative;
        p.capabilities.isFlying = t.getBoolean("Flying") && p.capabilities.allowFlying;
        p.capabilities.disableDamage = t.getBoolean("NoDamage") || creative;
        abilities(p);
        persisted(p).removeTag(TAG);
        String where = outOfBlocks(p);
        if (capable.contains(p.getUniqueID())) SpectatorMessage.send(p, false);
        say(p, "Spectator mode off." + (where != null ? " You were inside a block, so you're " + where + "." : ""));
        ValierTweaks.LOG.info("[spectate] {} stopped spectating{}", p.getCommandSenderName(), where != null ? " (" + where + ")" : "");
    }

    /** What spectator mode keeps true every tick. */
    static void apply(EntityPlayerMP p, boolean force) {
        p.noClip = true;
        p.fallDistance = 0;
        if (p.isBurning()) p.extinguish();
        boolean changed = force || !p.capabilities.allowFlying || !p.capabilities.isFlying || !p.capabilities.disableDamage;
        p.capabilities.allowFlying = true;
        p.capabilities.isFlying = true;
        p.capabilities.disableDamage = true;
        if (changed) abilities(p);
        if (p.posY < 1) move(p, p.posX, 1, p.posZ); // the void hurts even them
    }

    /** If the player is inside blocks, lifts them to the first spot above where they can stand; where, or null. */
    static String outOfBlocks(EntityPlayerMP p) {
        World w = p.worldObj;
        if (w.getCollidingBoundingBoxes(p, p.boundingBox.copy().contract(0.05, 0.05, 0.05)).isEmpty()) return null;
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        for (int y = Math.max(1, MathHelper.floor_double(p.posY)); y < 254; y++) {
            if (passable(w, x, y, z) && passable(w, x, y + 1, z) && !passable(w, x, y - 1, z)) {
                move(p, x + 0.5, y, z + 0.5);
                return "lifted to y " + y;
            }
        }
        int top = Math.max(1, w.getTopSolidOrLiquidBlock(x, z));
        move(p, x + 0.5, top, z + 0.5);
        return "put on top at y " + top;
    }

    private static boolean passable(World w, int x, int y, int z) {
        return w.getBlock(x, y, z).getCollisionBoundingBoxFromPool(w, x, y, z) == null;
    }

    static void move(EntityPlayerMP p, double x, double y, double z) {
        if (p.playerNetServerHandler != null) p.playerNetServerHandler.setPlayerLocation(x, y, z, p.rotationYaw, p.rotationPitch);
        else p.setPosition(x, y, z); // a fake player (the self-test)
    }

    static void abilities(EntityPlayerMP p) {
        if (p.playerNetServerHandler != null) p.sendPlayerAbilities();
    }

    /** Hides or shows the player with ServerUtilities' own vanish; true if that worked. */
    static boolean vanish(EntityPlayerMP p, boolean on) {
        if (!TweaksConfig.spectatorVanish || !Loader.isModLoaded("serverutilities")) return false;
        try {
            if (!Su.vanish(p, on)) return false;
            return persisted(p).getBoolean("vanish") == on;
        } catch (Throwable t) {
            ValierTweaks.LOG.warn("[spectate] ServerUtilities' vanish didn't work for {} ({})", p.getCommandSenderName(), t.toString());
            return false;
        }
    }

    static void say(ICommandSender s, String text) {
        s.addChatMessage(new ChatComponentText(text));
    }

    // ---- every tick, joining, leaving ------------------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.player.worldObj.isRemote || active.isEmpty()) return;
        if (!(event.player instanceof EntityPlayerMP) || !active.contains(event.player.getUniqueID())) return;
        EntityPlayerMP p = (EntityPlayerMP) event.player;
        apply(p, false);
        if (TweaksConfig.spectatorNightVision && (p.getActivePotionEffect(Potion.nightVision) == null
            || p.getActivePotionEffect(Potion.nightVision).getDuration() < 220)) {
            p.addPotionEffect(new PotionEffect(Potion.nightVision.id, 400, 0, true));
            persisted(p).getCompoundTag(TAG).setBoolean("Night", true);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (persisted(event.player).getCompoundTag(TAG).getBoolean("On")) {
            active.add(event.player.getUniqueID());
            say(event.player, "You're still in spectator mode. /spectate to stop.");
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        active.remove(event.player.getUniqueID());
        capable.remove(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        resend(event.player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resend(event.player);
    }

    private static void resend(EntityPlayer p) {
        if (p instanceof EntityPlayerMP && active.contains(p.getUniqueID()) && capable.contains(p.getUniqueID()))
            SpectatorMessage.send((EntityPlayerMP) p, true);
    }

    /** The player's Valier Tweaks said it can pass through blocks (server thread). */
    static void helloArrived(EntityPlayerMP p) {
        resend(p);
    }

    // ---- hands off ---------------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isSpectating(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (isSpectating(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (isSpectating(event.player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(EntityInteractEvent event) {
        if (isSpectating(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        if (isSpectating(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPickup(EntityItemPickupEvent event) {
        if (isSpectating(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onXp(PlayerPickupXpEvent event) {
        if (isSpectating(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onTarget(LivingSetAttackTargetEvent event) {
        if (event.target instanceof EntityPlayer && isSpectating((EntityPlayer) event.target) && event.entityLiving instanceof EntityLiving
            && ((EntityLiving) event.entityLiving).getAttackTarget() == event.target)
            ((EntityLiving) event.entityLiving).setAttackTarget(null);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingAttackEvent event) {
        if (event.entityLiving instanceof EntityPlayer && isSpectating((EntityPlayer) event.entityLiving)) event.setCanceled(true);
    }

    // ---- the command ----------------------------------------------------------------------------------------------------

    /** {@code /spectate [player] [on|off]}. */
    public static final class Command extends CommandBase {

        private static final String USAGE = "/spectate  |  /spectate <player> [on|off] (ops and the console)";

        @Override
        public String getCommandName() {
            return "spectate";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return USAGE;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if (!TweaksConfig.spectatorMode) {
                say(sender, "Spectator mode is turned off in the server's config.");
                return;
            }
            if (!mayUse(sender)) {
                say(sender, "You can't use spectator mode (it needs the permission " + NODE + ").");
                return;
            }
            if (args.length > 2) throw new WrongUsageException(USAGE);
            EntityPlayerMP target;
            if (args.length == 0) {
                if (!(sender instanceof EntityPlayerMP)) throw new WrongUsageException(USAGE);
                target = (EntityPlayerMP) sender;
            } else {
                boolean opLike = !(sender instanceof EntityPlayerMP) || PdimCommand.isOp(sender);
                target = getPlayer(sender, args[0]);
                if (target != sender && !opLike) {
                    say(sender, "Only ops and the console can switch spectator mode for someone else.");
                    return;
                }
            }
            boolean on;
            if (args.length == 2) {
                if ("on".equalsIgnoreCase(args[1])) on = true;
                else if ("off".equalsIgnoreCase(args[1])) on = false;
                else throw new WrongUsageException(USAGE);
            } else on = !active.contains(target.getUniqueID());
            if (on) enable(target);
            else disable(target);
            if (target != sender) say(sender, target.getCommandSenderName() + " is " + (active.contains(target.getUniqueID()) ? "now" : "no longer")
                + " spectating.");
        }

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
            if (args.length == 1) return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, "on", "off");
            return Collections.emptyList();
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return index == 0;
        }
    }

    /** ServerUtilities' permissions, only touched when it's loaded. */
    static final class Su {

        static boolean has(EntityPlayerMP p, String node) {
            return serverutils.ranks.Ranks.isActive() && serverutils.lib.util.permission.PermissionAPI.hasPermission(p, node);
        }

        /**
         * ServerUtilities' own {@code /vanish toggle on|off}, run as the player: its subcommand directly, because the
         * command tree checks the player's permission first.
         */
        static boolean vanish(EntityPlayerMP p, boolean on) {
            Object cmd = MinecraftServer.getServer().getCommandManager().getCommands().get("vanish");
            if (!(cmd instanceof serverutils.lib.command.CommandTreeBase)) return false;
            ICommand toggle = ((serverutils.lib.command.CommandTreeBase) cmd).getSubCommand("toggle");
            if (toggle == null) return false;
            toggle.processCommand(p, new String[] { on ? "on" : "off" });
            return true;
        }

        static void register() {
            try {
                serverutils.lib.util.permission.PermissionAPI.registerNode(NODE, serverutils.lib.util.permission.DefaultPermissionLevel.OP,
                    "Valier Tweaks: /spectate (spectator mode)");
            } catch (Throwable t) {
                ValierTweaks.LOG.warn("[spectate] couldn't register {} with ServerUtilities ({})", NODE, t.toString());
            }
        }
    }
}
