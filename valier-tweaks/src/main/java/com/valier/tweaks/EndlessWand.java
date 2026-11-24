package com.valier.tweaks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Endless wands (2.3.5, config {@code thaumcraft/endlessWands}). The server console's {@code /valier wand <player>}
 * makes a Thaumcraft wand (the pack's biggest plain rod with its cheapest caps) that belongs to that player; if they're
 * offline it waits for their next login. While the wand is in its owner's inventory, or in a menu they have open (an
 * Arcane Worktable's wand slot, a chest), the server keeps its six primal vis full. Anyone else holding it has a plain
 * wand that runs dry.
 * <p>
 * The vis is only ever filled to the wand's own capacity: Thaumcraft draws the vis bars at their true height, so an
 * over-full wand would paint them across the screen. Server side only: clients see a normal Thaumcraft wand with a name
 * and two lines of lore.
 */
public final class EndlessWand { // public: Forge calls its handlers from another class loader

    static final String TAG = "ValierEndless", OWNER = "Owner", OWNER_NAME = "OwnerName";
    /** How many times it topped a wand up since the server started (the self-test reads it). */
    static int filled;
    private int ticks;

    /** Every 4 ticks: each online player's own endless wands, in their inventory and in a menu they have open. */
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !TweaksConfig.endlessWands || ++ticks % 4 != 0) return;
        List<EntityPlayerMP> players = MinecraftServer.getServer()
            .getConfigurationManager().playerEntityList;
        for (int i = 0; i < players.size(); i++) refill(players.get(i));
    }

    /** A wand made while its owner was offline goes to them as they log in. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) handOver((EntityPlayerMP) e.player);
    }

    /** Tops up the player's own endless wands; true if it filled any. */
    static boolean refill(EntityPlayer p) {
        String id = p.getUniqueID()
            .toString();
        boolean any = false;
        for (ItemStack s : p.inventory.mainInventory) if (owned(s, id) && Tc.fill(s)) any = true;
        Container open = p.openContainer;
        if (open == null || open == p.inventoryContainer) return any;
        Slot changed = null;
        for (Object o : open.inventorySlots) {
            Slot slot = (Slot) o;
            if (slot.inventory == p.inventory) continue; // done above
            ItemStack s = slot.getStack();
            if (owned(s, id) && Tc.fill(s)) {
                slot.onSlotChanged();
                changed = slot;
            }
        }
        if (changed == null) return any;
        open.onCraftMatrixChanged(changed.inventory); // an Arcane Worktable shows its result again
        return true;
    }

    /** An endless wand that belongs to the player with this UUID. */
    static boolean owned(ItemStack s, String uuid) {
        if (s == null || !s.hasTagCompound()) return false;
        NBTTagCompound t = s.getTagCompound();
        return t.hasKey(TAG, 10) && uuid.equals(t.getCompoundTag(TAG)
            .getString(OWNER)) && thaumcraft() && Tc.isWand(s);
    }

    static boolean thaumcraft() {
        return cpw.mods.fml.common.Loader.isModLoaded("Thaumcraft");
    }

    /**
     * {@code /valier wand <player>}, from the console only. Online: the wand goes in their inventory (at their feet if
     * it's full). Offline but known to the server: it waits for their next login. Returns what to tell the console.
     */
    static String give(String name) {
        if (!TweaksConfig.endlessWands) return "Endless wands are off (thaumcraft/endlessWands in valiertweaks.cfg).";
        if (!thaumcraft()) return "Thaumcraft isn't loaded.";
        MinecraftServer server = MinecraftServer.getServer();
        EntityPlayerMP online = server.getConfigurationManager()
            .func_152612_a(name); // getPlayerByUsername
        if (online != null) {
            deliver(online);
            return "Gave " + online.getCommandSenderName() + " an Endless Wand.";
        }
        GameProfile gp = server.func_152358_ax() // getPlayerProfileCache
            .func_152655_a(name); // getGameProfileForUsername
        if (gp == null || gp.getId() == null) return "No player called " + name + " is known to this server.";
        Pending p = Pending.get();
        if (p == null) return "The overworld isn't loaded.";
        p.add(gp.getId()
            .toString(), gp.getName());
        return gp.getName() + " is offline: they get the Endless Wand at their next login.";
    }

    /** Hands over a wand waiting for this player, if there is one. */
    static void handOver(EntityPlayerMP player) {
        Pending p = Pending.get();
        if (p == null || !p.take(player.getUniqueID()
            .toString())) return;
        if (thaumcraft()) deliver(player);
    }

    static void deliver(EntityPlayerMP player) {
        ItemStack s = Tc.make(player.getUniqueID()
            .toString(), player.getCommandSenderName());
        if (!player.inventory.addItemStackToInventory(s)) player.dropPlayerItemWithRandomChoice(s, false);
        player.inventoryContainer.detectAndSendChanges();
        ChatComponentText m = new ChatComponentText("You have an Endless Wand: its vis stays full while you carry it.");
        m.getChatStyle()
            .setColor(EnumChatFormatting.AQUA);
        player.addChatMessage(m);
        ValierTweaks.LOG.info("Endless wand given to {} ({}, {})", player.getCommandSenderName(), Tc.describe(s),
            player.getUniqueID());
    }

    /** Endless wands made for players who were offline, by UUID (with the name, for reading the file). */
    public static final class Pending extends WorldSavedData {

        public static final String NAME = "valiertweaks_wands";
        final Map<String, String> waiting = new LinkedHashMap<>();
        private static Pending instance;
        private static World instanceWorld;

        public Pending(String name) {
            super(name);
        }

        static Pending get() {
            World w = DimensionManager.getWorld(0);
            if (w == null) return null;
            if (instance != null && instanceWorld == w) return instance;
            Pending d = (Pending) w.mapStorage.loadData(Pending.class, NAME);
            if (d == null) {
                d = new Pending(NAME);
                w.mapStorage.setData(NAME, d);
            }
            instance = d;
            instanceWorld = w;
            return d;
        }

        void add(String uuid, String name) {
            waiting.put(uuid, name);
            markDirty();
        }

        boolean take(String uuid) {
            if (waiting.remove(uuid) == null) return false;
            markDirty();
            return true;
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            waiting.clear();
            NBTTagList list = tag.getTagList("Waiting", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound e = list.getCompoundTagAt(i);
                waiting.put(e.getString(OWNER), e.getString(OWNER_NAME));
            }
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, String> e : waiting.entrySet()) {
                NBTTagCompound c = new NBTTagCompound();
                c.setString(OWNER, e.getKey());
                c.setString(OWNER_NAME, e.getValue());
                list.appendTag(c);
            }
            tag.setTag("Waiting", list);
        }
    }

    /** The only place that names Thaumcraft's classes, so the rest loads without it (only used when it's loaded). */
    static final class Tc {

        static boolean isWand(ItemStack s) {
            return s.getItem() instanceof thaumcraft.common.items.wands.ItemWandCasting;
        }

        /** Fills the six primal vis to the wand's capacity (never above); true if any was below it. */
        static boolean fill(ItemStack s) {
            thaumcraft.common.items.wands.ItemWandCasting w = (thaumcraft.common.items.wands.ItemWandCasting) s.getItem();
            int max = w.getMaxVis(s);
            boolean any = false;
            for (thaumcraft.api.aspects.Aspect a : thaumcraft.api.aspects.Aspect.getPrimalAspects()) {
                if (w.getVis(s, a) >= max) continue;
                w.storeVis(s, a, max);
                any = true;
            }
            if (any) filled++;
            return any;
        }

        static ItemStack make(String uuid, String name) {
            Item item = GameRegistry.findItem("Thaumcraft", "WandCasting");
            thaumcraft.common.items.wands.ItemWandCasting w = (thaumcraft.common.items.wands.ItemWandCasting) item;
            ItemStack s = new ItemStack(item, 1, 0);
            w.setRod(s, bestRod());
            w.setCap(s, bestCap());
            NBTTagCompound owner = new NBTTagCompound();
            owner.setString(OWNER, uuid);
            owner.setString(OWNER_NAME, name);
            s.getTagCompound()
                .setTag(TAG, owner);
            s.setStackDisplayName(EnumChatFormatting.AQUA + "Endless Wand");
            NBTTagList lore = new NBTTagList();
            lore.appendTag(new NBTTagString(EnumChatFormatting.GRAY + "Endless vis for " + name));
            lore.appendTag(new NBTTagString(EnumChatFormatting.DARK_GRAY + "Fills up only while its owner has it"));
            s.getTagCompound()
                .getCompoundTag("display")
                .setTag("Lore", lore);
            fill(s);
            return s;
        }

        /**
         * The plain (not staff) rod with the most vis, among rods that do nothing on their own: some add-on rods
         * recharge from the holder's blood or similar, which an endless wand doesn't need.
         */
        static thaumcraft.api.wands.WandRod bestRod() {
            thaumcraft.api.wands.WandRod best = thaumcraft.api.wands.WandRod.rods.get("silverwood");
            for (thaumcraft.api.wands.WandRod r : thaumcraft.api.wands.WandRod.rods.values()) {
                if (r instanceof thaumcraft.api.wands.StaffRod || r.getOnUpdate() != null || r.getTag()
                    .contains("staff")) continue;
                if (best == null || r.getCapacity() > best.getCapacity()) best = r;
            }
            return best;
        }

        /** The caps that make every spell and craft cheapest. */
        static thaumcraft.api.wands.WandCap bestCap() {
            thaumcraft.api.wands.WandCap best = null;
            for (thaumcraft.api.wands.WandCap c : thaumcraft.api.wands.WandCap.caps.values())
                if (best == null || c.getBaseCostModifier() < best.getBaseCostModifier()) best = c;
            return best;
        }

        static String describe(ItemStack s) {
            thaumcraft.common.items.wands.ItemWandCasting w = (thaumcraft.common.items.wands.ItemWandCasting) s.getItem();
            return w.getRod(s)
                .getTag() + " rod, "
                + w.getCap(s)
                    .getTag()
                + " caps, " + w.getMaxVis(s) / 100 + " vis of each";
        }
    }
}
