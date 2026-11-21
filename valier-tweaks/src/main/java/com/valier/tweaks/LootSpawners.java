package com.valier.tweaks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkDataEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Loot spawners: sneak-right-click a mob spawner with the conversion item (a diamond block by default) and it stops
 * spawning mobs. Every spawner cycle (its own delay, 10 to 40 seconds for a vanilla spawner) it makes the loot of the
 * mobs it would have spawned ({@link MobLoot}) and puts it into the chests, barrels, drawers, machines and pipes touching
 * it. When nothing can take any more it holds what it has and waits. Sneak-right-click it with an empty hand to see how
 * it is doing, with an emerald block to have it throw damaged items away, with a wrench to pick it up as an item
 * (placing that item puts it back). See {@link LootSpawnerTile} for how it is stored.
 */
public final class LootSpawners {

    enum Status {
        STARTING("just made"),
        WORKING("working"),
        FULL("waiting: everything touching it is full"),
        NO_OUTPUT("waiting: no chest, inventory or pipe touches it"),
        NO_PLAYER("waiting: no player within 16 blocks"),
        NOTHING("it makes nothing: that mob has no loot or is blocked in the config");

        final String words;

        Status(String words) {
            this.words = words;
        }
    }

    // Optional pipe APIs, found by name: EnderIO conduits (CoFH IItemDuct) and BuildCraft pipes (IInjectable).
    private static final Class<?> ITEM_DUCT = find("cofh.api.transport.IItemDuct");
    /** GregTech's pipe tile (item pipes are inventories that pass on what they get). */
    private static final Class<?> GT_PIPE = find("gregtech.api.metatileentity.BaseMetaPipeEntity");
    private static final Method DUCT_INSERT = method(ITEM_DUCT, "insertItem", ForgeDirection.class, ItemStack.class);
    private static final Class<?> INJECTABLE = find("buildcraft.api.transport.IInjectable");
    private static final Class<?> BC_COLOR = find("buildcraft.api.core.EnumColor");
    private static final Method INJECT = BC_COLOR == null ? null
        : method(INJECTABLE, "injectItem", ItemStack.class, boolean.class, ForgeDirection.class, BC_COLOR);

    // Wrenches, found by name: BuildCraft's IToolWrench (GregTech's tools, EnderIO's Yeta wrench, AE2's quartz wrenches,
    // BuildCraft's own all have it; GregTech's say yes only for its wrenches), AE2's own, and IC2's.
    private static final Class<?> BC_WRENCH = find("buildcraft.api.tools.IToolWrench");
    private static final Method BC_CAN_WRENCH = method(BC_WRENCH, "canWrench", EntityPlayer.class, int.class, int.class, int.class);
    private static final Method BC_WRENCH_USED = method(BC_WRENCH, "wrenchUsed", EntityPlayer.class, int.class, int.class, int.class);
    private static final Class<?> AE_WRENCH = find("appeng.api.implementations.items.IAEWrench");
    private static final Method AE_CAN_WRENCH = method(AE_WRENCH, "canWrench", ItemStack.class, EntityPlayer.class, int.class,
        int.class, int.class);
    private static final Class<?> IC2_WRENCH = find("ic2.core.item.tool.ItemToolWrench");

    /** The tag in a Valier Loot Spawner item holding the spawner (see {@link LootSpawnerTile#saveForItem}). */
    static final String ITEM_TAG = "ValierLootSpawner";

    private int tick;

    // ---- turning a spawner into a loot spawner -------------------------------------------------------------------

    /**
     * Sneak-right-click on a spawner. Low priority, and not for cancelled clicks, so claim protection that stops the
     * click wins; a break event at the spawner is asked as well before anything changes.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClick(PlayerInteractEvent event) {
        if (!TweaksConfig.lootSpawners) return;
        boolean hit = event.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK;
        if (!hit && event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        World world = event.world;
        if (world.isRemote || !player.isSneaking()) return;
        int x = event.x, y = event.y, z = event.z;
        Block block = world.getBlock(x, y, z);
        if (block != Blocks.mob_spawner && block != ModBlocks.lootSpawner) return;
        TileEntity te = world.getTileEntity(x, y, z);
        ItemStack held = player.getHeldItem();
        if (hit) { // sneak-hit with an empty hand: its upgrade blocks come back out
            if (te instanceof LootSpawnerTile && held == null) {
                event.setCanceled(true);
                takeBack(player, (LootSpawnerTile) te);
            }
            return;
        }
        if (te instanceof LootSpawnerTile) {
            if (TweaksConfig.lootSpawnerWrench && isWrench(held, player, x, y, z)) {
                event.setCanceled(true);
                pickUp(player, (LootSpawnerTile) te, held);
            } else if (TweaksConfig.lootSpawnerSpawnEggs && held != null && held.getItem() == Items.spawn_egg) {
                event.setCanceled(true);
                changeMob(player, (LootSpawnerTile) te, held);
            } else if (isLootingItem(held)) {
                event.setCanceled(true);
                addBlock(player, (LootSpawnerTile) te, Kind.LOOTING, held);
            } else if (isSpeedItem(held)) {
                event.setCanceled(true);
                addBlock(player, (LootSpawnerTile) te, Kind.SPEED, held);
            } else if (isXpItem(held)) {
                event.setCanceled(true);
                toggleXp(player, (LootSpawnerTile) te, held);
            } else if (isFilterItem(held)) {
                event.setCanceled(true);
                toggleFilter(player, (LootSpawnerTile) te, held);
            } else if (held == null || isConvertItem(held)) {
                event.setCanceled(true);
                tell(player, status((LootSpawnerTile) te));
            }
            return;
        }
        if (te == null || te.getClass() != TileEntityMobSpawner.class) return; // another mod's spawner: leave it
        TileEntityMobSpawner spawner = (TileEntityMobSpawner) te;
        if (LootSpawnerTile.isMarked(spawner)) { // a loot spawner the scan hasn't picked up yet
            event.setCanceled(true);
            tell(player, status(swap(world, spawner)));
            return;
        }
        if (isLootingItem(held) && !isConvertItem(held)) {
            event.setCanceled(true); // don't place the lapis block
            tell(player, text(EnumChatFormatting.GRAY, "Make it a loot spawner first (sneak-right-click with ")
                .appendSibling(costText(TweaksConfig.lootSpawnerItem, TweaksConfig.lootSpawnerItemCount))
                .appendSibling(new ChatComponentText("); then ").appendSibling(tiersText(Kind.LOOTING, false))));
            return;
        }
        if (isSpeedItem(held) && !isConvertItem(held)) {
            event.setCanceled(true); // don't place the cursed earth
            tell(player, text(EnumChatFormatting.GRAY, "Make it a loot spawner first (sneak-right-click with ")
                .appendSibling(costText(TweaksConfig.lootSpawnerItem, TweaksConfig.lootSpawnerItemCount))
                .appendSibling(new ChatComponentText("); then ").appendSibling(tiersText(Kind.SPEED, false))));
            return;
        }
        if (isXpItem(held) && !isConvertItem(held)) {
            event.setCanceled(true); // don't place the gold block
            tell(player, text(EnumChatFormatting.GRAY, "Make it a loot spawner first (sneak-right-click with ")
                .appendSibling(costText(TweaksConfig.lootSpawnerItem, TweaksConfig.lootSpawnerItemCount))
                .appendSibling(new ChatComponentText("); then ")
                    .appendSibling(costText(TweaksConfig.lootSpawnerXpItem, TweaksConfig.lootSpawnerXpItemCount))
                    .appendSibling(new ChatComponentText(" turns its XP into bottles o' enchanting."))));
            return;
        }
        if (isFilterItem(held) && !isConvertItem(held)) {
            event.setCanceled(true); // don't place the emerald block
            tell(player, text(EnumChatFormatting.GRAY, "Make it a loot spawner first (sneak-right-click with ")
                .appendSibling(costText(TweaksConfig.lootSpawnerItem, TweaksConfig.lootSpawnerItemCount))
                .appendSibling(new ChatComponentText("); then ")
                    .appendSibling(costText(TweaksConfig.lootSpawnerFilterItem, TweaksConfig.lootSpawnerFilterItemCount))
                    .appendSibling(new ChatComponentText(" makes it throw damaged items away."))));
            return;
        }
        if (costUnknown()) {
            event.setCanceled(true);
            tell(player, text(EnumChatFormatting.RED, "Loot spawners can't be made right now: the item set in the config ("
                + TweaksConfig.lootSpawnerItem + ") doesn't exist. Ask an op."));
            return;
        }
        boolean free = TweaksConfig.lootSpawnerItem.trim().isEmpty();
        if (held == null && !free) {
            event.setCanceled(true);
            tell(player, hint(spawner));
            return;
        }
        if (!(free ? held == null : isConvertItem(held))) return;
        event.setCanceled(true); // don't place the diamond block
        String type = spawner.func_145881_a()
            .getEntityNameToSpawn();
        if (MobLoot.blocked(type)) {
            tell(player, text(EnumChatFormatting.RED, "A ").appendSibling(mobName(type))
                .appendSibling(new ChatComponentText(" spawner can't be made into a loot spawner on this server.")));
            return;
        }
        if (!mayChangeSpawner(player, spawner)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this spawner: it's protected."));
            return;
        }
        int cost = free ? 0 : TweaksConfig.lootSpawnerItemCount;
        if (!player.capabilities.isCreativeMode && held != null && held.stackSize < cost) {
            tell(player, text(EnumChatFormatting.RED, "That takes " + cost + " of them."));
            return;
        }
        LootSpawnerTile.mark(spawner);
        LootSpawnerTile loot = swap(world, spawner);
        loot.timer = nextDelay(loot, world);
        if (!player.capabilities.isCreativeMode && cost > 0) {
            held.stackSize -= cost;
            if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
        }
        ValierTweaks.LOG.info("{} made the {} spawner at {} a loot spawner", player.getCommandSenderName(), type,
            loot.where());
        tell(player, text(EnumChatFormatting.GREEN, "Loot spawner made. ").appendSibling(status(loot)));
    }

    /**
     * Sneak-right-click on a loot spawner with the filter item (an emerald block by default): damaged items are thrown
     * away from now on, and the item is kept in the spawner. Again: switched off, and the item is given back.
     */
    static void toggleFilter(EntityPlayer player, LootSpawnerTile t, ItemStack held) {
        if (!mayChange(player, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this spawner: it's protected."));
            return;
        }
        if (t.noDamaged) {
            t.noDamaged = false;
            ItemStack back = t.filterPaid;
            t.filterPaid = null;
            t.markDirty();
            IChatComponent msg = text(EnumChatFormatting.GREEN, "Damaged items come out of this loot spawner again.");
            if (back != null) {
                msg.appendSibling(new ChatComponentText(" Here is your "))
                    .appendSibling(new ChatComponentTranslation(back.getUnlocalizedName() + ".name"))
                    .appendSibling(new ChatComponentText(back.stackSize > 1 ? " × " + back.stackSize + " back." : " back."));
                give(player, back);
            }
            ValierTweaks.LOG.info("{} let damaged items out of the loot spawner at {} again", player.getCommandSenderName(),
                t.where());
            tell(player, msg);
            return;
        }
        int cost = TweaksConfig.lootSpawnerFilterItemCount;
        boolean creative = player.capabilities.isCreativeMode;
        if (!creative && held.stackSize < cost) {
            tell(player, text(EnumChatFormatting.RED, "That takes " + cost + " of them."));
            return;
        }
        if (!creative) {
            t.filterPaid = held.splitStack(cost);
            if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
        }
        t.noDamaged = true;
        int thrown = 0;
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) {
            if (damaged(t.items[i])) {
                t.items[i] = null;
                thrown++;
            }
        }
        t.markDirty();
        ValierTweaks.LOG.info("{} made the loot spawner at {} throw damaged items away", player.getCommandSenderName(),
            t.where());
        tell(player, text(EnumChatFormatting.GREEN, "This loot spawner throws damaged items away now (worn tools, weapons "
            + "and armour): only undamaged loot comes out" + (thrown > 0 ? ", and the " + thrown + " damaged it was keeping are gone" : "")
            + ". Do it again to switch that off and get the item back."));
    }

    // ---- picking one up with a wrench, and putting it back ----------------------------------------------------------

    /** A wrench, as the mods that make them say: GregTech's tools are all BuildCraft wrenches, but only wrenches say yes. */
    public static boolean isWrench(ItemStack held, EntityPlayer player, int x, int y, int z) {
        if (held == null || held.getItem() == null) return false;
        Item item = held.getItem();
        try {
            if (BC_WRENCH != null && BC_WRENCH.isInstance(item))
                return BC_CAN_WRENCH == null || (Boolean) BC_CAN_WRENCH.invoke(item, player, x, y, z);
            if (AE_WRENCH != null && AE_WRENCH.isInstance(item))
                return AE_CAN_WRENCH == null || (Boolean) AE_CAN_WRENCH.invoke(item, held, player, x, y, z);
        } catch (Throwable e) {
            ValierTweaks.LOG.debug("Loot spawner: couldn't ask {} if it is a wrench: {}", item.getClass()
                .getName(), e);
            return false;
        }
        if (IC2_WRENCH != null && IC2_WRENCH.isInstance(item)) return true;
        return item.getToolClasses(held)
            .contains("wrench");
    }

    /** Lets the wrench know it was used (some take durability for it). */
    public static void wrenchUsed(ItemStack held, EntityPlayer player, int x, int y, int z) {
        if (held == null || BC_WRENCH == null || BC_WRENCH_USED == null || !BC_WRENCH.isInstance(held.getItem())) return;
        try {
            BC_WRENCH_USED.invoke(held.getItem(), player, x, y, z);
        } catch (Throwable e) {
            ValierTweaks.LOG.debug("Loot spawner: {} failed as a wrench: {}", held.getItem()
                .getClass()
                .getName(), e);
        }
    }

    /**
     * Sneak-right-click on a loot spawner with a wrench: it comes off as a Valier Loot Spawner item ({@link #toItem})
     * carrying its mob, settings and the damaged-items filter with what was paid for it. The loot it was keeping drops.
     */
    static void pickUp(EntityPlayer player, LootSpawnerTile t, ItemStack wrench) {
        World world = t.getWorldObj();
        int x = t.xCoord, y = t.yCoord, z = t.zCoord;
        if (!mayChange(player, world, x, y, z)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this spawner: it's protected."));
            return;
        }
        String type = t.logic()
            .getEntityNameToSpawn();
        boolean filtered = t.noDamaged;
        ItemStack item = toItem(t);
        t.xpPaid = null; // it goes with the item too
        t.looting.paid = 0; // and its Looting and Speed items
        t.speed.paid = 0;
        int stacks = 0;
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) {
            if (t.items[i] != null) {
                drop(world, x, y, z, t.items[i]);
                stacks++;
            }
            t.items[i] = null;
        }
        t.filterPaid = null; // it goes with the item
        t.markDirty();
        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(ModBlocks.lootSpawner)); // the sound and bits of a break
        world.setBlockToAir(x, y, z); // it holds nothing now, so nothing else drops
        give(player, item);
        wrenchUsed(wrench, player, x, y, z);
        ValierTweaks.LOG.info("{} picked up the {} loot spawner at dim {} {} {} {} with a wrench", player.getCommandSenderName(),
            type, world.provider.dimensionId, x, y, z);
        IChatComponent msg = text(EnumChatFormatting.GREEN, "Picked up the loot spawner (").appendSibling(mobName(type))
            .appendSibling(new ChatComponentText("). Place it anywhere and it is a loot spawner again, with the same mob and "
                + "settings" + (filtered ? ", still throwing damaged items away" : "") + "."));
        if (stacks > 0) msg.appendSibling(new ChatComponentText(" The loot it was keeping dropped."));
        tell(player, msg);
    }

    /**
     * A Valier Loot Spawner item named "Valier Loot Spawner (Zombie)", holding the spawner in {@value #ITEM_TAG}.
     * Before 2.0.0 it was a vanilla spawner item; those still place a loot spawner ({@link #savedSpawner}).
     */
    static ItemStack toItem(LootSpawnerTile t) {
        String type = t.logic()
            .getEntityNameToSpawn();
        String mob = mobWords(type);
        ItemStack stack = new ItemStack(ModBlocks.lootSpawner);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(ITEM_TAG, t.saveForItem());
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", displayName(type, t.looting.level(), t.speedLevel()));
        String grey = EnumChatFormatting.RESET + "" + EnumChatFormatting.GRAY;
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString(grey + "Makes the loot of each " + mob + ", no mobs."));
        if (t.noDamaged) lore.appendTag(new NBTTagString(grey + "Throws damaged items away."));
        if (t.xpBottles) lore.appendTag(new NBTTagString(grey + "Turns its XP into bottles o' enchanting."));
        int looting = t.looting.level(), speed = t.speed.level();
        if (looting > 0) lore.appendTag(new NBTTagString(grey + "Looting " + roman(looting) + "."));
        if (speed > 0) lore.appendTag(new NBTTagString(grey + "Speed " + roman(speed) + ", " + (speed + 1) + "× as fast."));
        lore.appendTag(new NBTTagString(grey + "Place it and it is a loot spawner again."));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
        return stack;
    }

    /** "Valier Loot Spawner (Zombie, Looting II, Speed I)", in gold: the item's name, and what players' WAILA shows. */
    static String displayName(String type, int looting, int speed) {
        return EnumChatFormatting.RESET + "" + EnumChatFormatting.GOLD + "Valier Loot Spawner" + EnumChatFormatting.RESET + " ("
            + mobWords(type) + (looting > 0 ? ", Looting " + roman(looting) : "") + (speed > 0 ? ", Speed " + roman(speed) : "")
            + ")";
    }

    /**
     * The spawner a Valier Loot Spawner item holds (this mod's item, or the vanilla spawner item a wrench gave before
     * 2.0.0), or null for any other item.
     */
    static NBTTagCompound savedSpawner(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        if (stack.getItem() != Item.getItemFromBlock(Blocks.mob_spawner) && stack.getItem() != Item.getItemFromBlock(ModBlocks.lootSpawner))
            return null;
        NBTTagCompound tag = stack.getTagCompound();
        return tag.hasKey(ITEM_TAG, 10) ? tag.getCompoundTag(ITEM_TAG) : null;
    }

    /**
     * A Valier Loot Spawner item from before 2.0.0 (a vanilla spawner item) placed: the spawner vanilla just made (a pig
     * spawner) gets what the item holds and becomes a loot spawner block. Lowest priority and not for cancelled
     * placements, so claims that stop it win. This mod's own item does it in {@link ItemBlockLootSpawner}.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || event.placedBlock != Blocks.mob_spawner || event.itemInHand == null
            || event.itemInHand.getItem() != Item.getItemFromBlock(Blocks.mob_spawner)) return; // not ours: it did it already
        NBTTagCompound saved = savedSpawner(event.itemInHand);
        if (saved != null) place(event.world, event.x, event.y, event.z, saved, event.player);
    }

    static TileEntity place(World world, int x, int y, int z, NBTTagCompound saved, EntityPlayer player) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || te.getClass() != TileEntityMobSpawner.class) return te;
        NBTTagCompound tag = (NBTTagCompound) saved.copy();
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        tag.setString("id", "MobSpawner");
        TileEntityMobSpawner spawner = (TileEntityMobSpawner) te;
        spawner.readFromNBT(tag);
        if (!LootSpawnerTile.isMarked(spawner)) return spawner;
        if (!TweaksConfig.lootSpawners) { // switched off: a plain spawner of that mob, its emerald block dropped
            revert(world, spawner);
            return spawner;
        }
        LootSpawnerTile loot = swap(world, spawner);
        loot.timer = nextDelay(loot, world);
        ValierTweaks.LOG.info("{} placed a {} loot spawner at {}", player == null ? "Something" : player.getCommandSenderName(),
            loot.logic()
                .getEntityNameToSpawn(),
            loot.where());
        return loot;
    }

    /**
     * This mod's item placed ({@link ItemBlockLootSpawner}): the new block's tile gets what the item holds (nothing: a
     * pig loot spawner).
     */
    static void placeInto(World world, LootSpawnerTile t, NBTTagCompound saved, EntityPlayer player) {
        NBTTagCompound tag;
        if (saved != null) tag = (NBTTagCompound) saved.copy();
        else {
            tag = new NBTTagCompound();
            t.writeAll(tag); // a fresh tile: marks it (a pig spawner, dressed as vanilla dresses its mobs)
        }
        tag.setInteger("x", t.xCoord);
        tag.setInteger("y", t.yCoord);
        tag.setInteger("z", t.zCoord);
        t.readFromNBT(tag);
        if (!TweaksConfig.lootSpawners) { // switched off: a plain spawner of that mob, what was paid dropped
            TileEntityMobSpawner plain = toVanilla(world, t);
            if (plain != null) revert(world, plain);
            return;
        }
        t.timer = nextDelay(t, world);
        t.markDirty();
        world.markBlockForUpdate(t.xCoord, t.yCoord, t.zCoord);
        ValierTweaks.LOG.info("{} placed a {} loot spawner at {}", player == null ? "Something" : player.getCommandSenderName(),
            t.logic()
                .getEntityNameToSpawn(),
            t.where());
    }

    /** The mob's global entity id, or 0 for mobs without one. */
    static int entityId(String type) {
        Object cls = EntityList.stringToClassMapping.get(type);
        if (cls == null) return 0;
        for (Object o : EntityList.IDtoClassMapping.entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            if (e.getValue() == cls && e.getKey() instanceof Integer) return (Integer) e.getKey();
        }
        return 0;
    }

    /** The mob's name as the server's language has it ("Zombie"), else its entity name without the mod's prefix. */
    static String mobWords(String type) {
        if (type == null) return "nothing";
        String key = "entity." + type + ".name";
        if (StatCollector.canTranslate(key)) return StatCollector.translateToLocal(key);
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    /**
     * Sneak-right-click on a loot spawner with a spawn egg ({@link TweaksConfig#lootSpawnerSpawnEggs}, off by default):
     * it makes that mob's loot from now on, dressed as a vanilla spawner dresses its mobs (no spawn data of its own).
     * The egg is used up (not in creative). What it was keeping, its timer and its emerald block stay.
     */
    static void changeMob(EntityPlayer player, LootSpawnerTile t, ItemStack egg) {
        int id = egg.getItemDamage();
        String type = EntityList.entityEggs.containsKey(id) ? EntityList.getStringFromID(id) : null;
        if (type == null) {
            tell(player, text(EnumChatFormatting.RED, "That spawn egg has no mob."));
            return;
        }
        if (t.candidates.size() == 1 && type.equals(t.candidates.get(0).type)) {
            tell(player, text(EnumChatFormatting.GRAY, "It already makes the loot of ").appendSibling(mobName(type))
                .appendSibling(new ChatComponentText(".")));
            return;
        }
        if (MobLoot.blocked(type)) {
            tell(player, text(EnumChatFormatting.RED, "A ").appendSibling(mobName(type))
                .appendSibling(new ChatComponentText(" loot spawner isn't allowed on this server.")));
            return;
        }
        World world = t.getWorldObj();
        if (!mayChange(player, world, t.xCoord, t.yCoord, t.zCoord)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this spawner: it's protected."));
            return;
        }
        String was = t.logic()
            .getEntityNameToSpawn();
        NBTTagCompound nbt = new NBTTagCompound();
        t.writeAll(nbt);
        NBTTagCompound mark = (NBTTagCompound) nbt.getCompoundTag("SpawnData")
            .getCompoundTag(LootSpawnerTile.MARK)
            .copy();
        mark.setBoolean("Egg", true); // no spawn data of its own: dressed as a vanilla spawner would dress it
        NBTTagCompound data = new NBTTagCompound();
        data.setTag(LootSpawnerTile.MARK, mark);
        nbt.setTag("SpawnData", data);
        nbt.removeTag("SpawnPotentials");
        nbt.setString("EntityId", type);
        t.readFromNBT(nbt);
        if (!player.capabilities.isCreativeMode) {
            egg.stackSize--;
            if (egg.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
        }
        t.markDirty();
        world.markBlockForUpdate(t.xCoord, t.yCoord, t.zCoord); // clients: the hover and the cage
        ValierTweaks.LOG.info("{} changed the loot spawner at {} from {} to {} with a spawn egg", player.getCommandSenderName(),
            t.where(), was, type);
        tell(player, text(EnumChatFormatting.GREEN, "This loot spawner makes the loot of ").appendSibling(mobName(type))
            .appendSibling(new ChatComponentText(" now.")));
    }

    /**
     * Sneak-right-click on a loot spawner with the XP item (a gold block by default): the XP its mobs would have
     * dropped comes out as bottles o' enchanting from now on, and the item is kept in the spawner. Again: switched off,
     * and the item is given back.
     */
    static void toggleXp(EntityPlayer player, LootSpawnerTile t, ItemStack held) {
        if (!mayChange(player, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this spawner: it's protected."));
            return;
        }
        if (t.xpBottles) {
            t.xpBottles = false;
            ItemStack back = t.xpPaid;
            t.xpPaid = null;
            t.markDirty();
            IChatComponent msg = text(EnumChatFormatting.GREEN, "This loot spawner makes no more bottles o' enchanting.");
            if (back != null) {
                msg.appendSibling(new ChatComponentText(" Here is your "))
                    .appendSibling(new ChatComponentTranslation(back.getUnlocalizedName() + ".name"))
                    .appendSibling(new ChatComponentText(back.stackSize > 1 ? " × " + back.stackSize + " back." : " back."));
                give(player, back);
            }
            ValierTweaks.LOG.info("{} stopped the loot spawner at {} making bottles o' enchanting", player.getCommandSenderName(),
                t.where());
            tell(player, msg);
            return;
        }
        int cost = TweaksConfig.lootSpawnerXpItemCount;
        boolean creative = player.capabilities.isCreativeMode;
        if (!creative && held.stackSize < cost) {
            tell(player, text(EnumChatFormatting.RED, "That takes " + cost + " of them."));
            return;
        }
        if (!creative) {
            t.xpPaid = held.splitStack(cost);
            if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
        }
        t.xpBottles = true;
        t.markDirty();
        ValierTweaks.LOG.info("{} made the loot spawner at {} turn its XP into bottles o' enchanting", player.getCommandSenderName(),
            t.where());
        tell(player, text(EnumChatFormatting.GREEN, "The XP this loot spawner's mobs would have dropped comes out as bottles "
            + "o' enchanting now, one per " + TweaksConfig.lootSpawnerXpPerBottle + " XP. Do it again to switch that off and "
            + "get the item back."));
    }

    /**
     * Sneak-right-click on a loot spawner with a Looting item (a lapis block by default) or a Speed item (cursed earth):
     * one goes in, and how many it holds sets the level ({@link Kind#tiers}). For Speed one right under it counts too.
     */
    static void addBlock(EntityPlayer player, LootSpawnerTile t, Kind kind, ItemStack held) {
        LootSpawnerTile.Upgrade u = kind.of(t);
        int[] tiers = kind.tiers();
        int below = kind == Kind.SPEED && speedBelow(t) ? 1 : 0;
        int level = kind.level(u.blocks + below);
        if (level >= tiers.length) {
            tell(player, text(EnumChatFormatting.GRAY, "It already has " + kind.word + " " + roman(level) + ", the most it can have."));
            return;
        }
        if (u.paid > 0 && u.item != null && !MobLoot.sameItem(u.item, copyOne(held))) {
            tell(player, text(EnumChatFormatting.RED, "It holds ").appendSibling(itemName(u.item))
                .appendSibling(new ChatComponentText(" for its " + kind.word + "; only more of those go in.")));
            return;
        }
        if (!mayChange(player, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this spawner: it's protected."));
            return;
        }
        int factor = t.speedFactor();
        u.blocks++;
        if (!player.capabilities.isCreativeMode) {
            u.paid++;
            if (u.item == null) u.item = copyOne(held);
            held.stackSize--;
            if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            player.inventoryContainer.detectAndSendChanges();
        }
        int now = kind.level(u.blocks + below);
        if (t.timer > 0) t.timer = Math.max(1, t.timer * factor / t.speedFactor()); // the batch counting down speeds up too
        t.markDirty();
        t.getWorldObj()
            .markBlockForUpdate(t.xCoord, t.yCoord, t.zCoord); // the hover names its level
        ValierTweaks.LOG.info("{} put a {} item into the loot spawner at {}: {} now, {} {}", player.getCommandSenderName(),
            kind.word.toLowerCase(), t.where(), u.blocks, kind.word, now);
        IChatComponent msg = text(EnumChatFormatting.GREEN, now > level ? kind.word + " " + roman(now) + " now" : now > 0
            ? "Still " + kind.word + " " + roman(now) : "No " + kind.word + " yet");
        if (kind == Kind.SPEED && now > 0) msg.appendSibling(new ChatComponentText(", " + (now + 1) + "× as fast"));
        msg.appendSibling(new ChatComponentText(" (" + u.blocks + " in it" + (below > 0 ? " and one under it" : "") + ")."));
        if (now < tiers.length) msg.appendSibling(new ChatComponentText(" " + (tiers[now] - u.blocks - below) + " more for "
            + kind.word + " " + roman(now + 1) + "."));
        else msg.appendSibling(new ChatComponentText(" That's the most it can have."));
        tell(player, msg);
    }

    /**
     * Sneak-hit (left-click) a loot spawner with an empty hand: every block put into it comes back (its Looting blocks,
     * the gold block for bottles, the emerald block for the damaged-items filter), and those switch off.
     */
    static void takeBack(EntityPlayer player, LootSpawnerTile t) {
        if (t.looting.blocks == 0 && t.speed.blocks == 0 && !t.xpBottles && !t.noDamaged) {
            tell(player, text(EnumChatFormatting.GRAY, "It has no blocks in it to take back."));
            return;
        }
        if (!mayChange(player, t.getWorldObj(), t.xCoord, t.yCoord, t.zCoord)) {
            tell(player, text(EnumChatFormatting.RED, "You can't change this spawner: it's protected."));
            return;
        }
        List<ItemStack> back = t.looting.refund();
        back.addAll(t.speed.refund());
        if (t.xpPaid != null) back.add(t.xpPaid);
        if (t.filterPaid != null) back.add(t.filterPaid);
        IChatComponent msg = text(EnumChatFormatting.GREEN, "");
        List<String> off = new ArrayList<>();
        if (t.looting.blocks > 0) off.add("Looting");
        if (t.speed.blocks > 0) off.add("Speed");
        if (t.xpBottles) off.add("bottles o' enchanting");
        if (t.noDamaged) off.add("throwing damaged items away");
        int factor = t.speedFactor();
        t.looting.clear();
        t.speed.clear();
        if (t.timer > 0) t.timer = Math.max(1, t.timer * factor / t.speedFactor()); // the batch counting down slows again
        t.xpBottles = false;
        t.xpPaid = null;
        t.xp = 0;
        t.noDamaged = false;
        t.filterPaid = null;
        t.markDirty();
        t.getWorldObj()
            .markBlockForUpdate(t.xCoord, t.yCoord, t.zCoord); // the hover drops its Looting and Speed
        msg.appendSibling(new ChatComponentText(joinWords(off) + " switched off."));
        if (!back.isEmpty()) {
            msg.appendSibling(new ChatComponentText(" Back to you: "));
            for (int i = 0; i < back.size(); i++) {
                ItemStack s = back.get(i);
                if (i > 0) msg.appendSibling(new ChatComponentText(", "));
                if (s.stackSize > 1) msg.appendSibling(new ChatComponentText(s.stackSize + " × "));
                msg.appendSibling(itemName(s));
            }
            msg.appendSibling(new ChatComponentText("."));
        }
        for (ItemStack s : back) give(player, s);
        ValierTweaks.LOG.info("{} took the upgrade blocks out of the loot spawner at {}", player.getCommandSenderName(), t.where());
        tell(player, msg);
    }

    private static String joinWords(List<String> words) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) s.append(i == words.size() - 1 ? " and " : ", ");
            s.append(words.get(i));
        }
        String out = s.toString();
        return out.isEmpty() ? out : Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    /** The upgrades that take blocks one per click, the count setting a level: Looting and Speed. */
    enum Kind {

        LOOTING("Looting"),
        SPEED("Speed");

        /** Its name, as players see it and in the mark's keys ("LootingBlocks", "SpeedPaid", ...). */
        final String word;

        Kind(String word) {
            this.word = word;
        }

        /** The config setting naming its item. */
        String item() {
            return this == LOOTING ? TweaksConfig.lootSpawnerLootingItem : TweaksConfig.lootSpawnerSpeedItem;
        }

        /** How many blocks each level takes, in order. */
        int[] tiers() {
            return this == LOOTING ? TweaksConfig.lootSpawnerLootingBlocks : TweaksConfig.lootSpawnerSpeedBlocks;
        }

        /** The level so many blocks give. */
        int level(int blocks) {
            int level = 0;
            for (int need : tiers()) {
                if (blocks < need) break;
                level++;
            }
            return level;
        }

        LootSpawnerTile.Upgrade of(LootSpawnerTile t) {
            return this == LOOTING ? t.looting : t.speed;
        }
    }

    /** The Looting level so many Looting items give ({@link TweaksConfig#lootSpawnerLootingBlocks}). */
    static int lootingLevel(int blocks) {
        return Kind.LOOTING.level(blocks);
    }

    static String roman(int n) {
        String[] r = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
    }

    /**
     * "Sneak-right-click it with a Lapis Lazuli Block, one per click, for Looting: 1 for Looting I, 3 for II and 6 for
     * III." Speed adds "(2×, 3× and 4× as fast)".
     */
    static IChatComponent tiersText(Kind kind, boolean capital) {
        Item item = itemOf(kind.item());
        IChatComponent msg = new ChatComponentText(capital ? "Sneak-right-click it with a " : "sneak-right-click with a ");
        if (item != null) msg.appendSibling(new ChatComponentTranslation(
            new ItemStack(item, 1, Math.max(0, metaOf(kind.item()))).getUnlocalizedName() + ".name"));
        StringBuilder s = new StringBuilder(", one per click, for " + kind.word + ": ");
        int[] tiers = kind.tiers();
        for (int i = 0; i < tiers.length; i++) {
            if (i > 0) s.append(i == tiers.length - 1 ? " and " : ", ");
            s.append(tiers[i])
                .append(" for ")
                .append(i == 0 ? kind.word + " " : "")
                .append(roman(i + 1));
        }
        if (kind == Kind.SPEED) {
            s.append(" (");
            for (int i = 0; i < tiers.length; i++) {
                if (i > 0) s.append(i == tiers.length - 1 ? " and " : ", ");
                s.append(i + 2)
                    .append('×');
            }
            s.append(" as fast)");
        }
        return msg.appendSibling(new ChatComponentText(s.append('.')
            .toString()));
    }

    /** Items paid into it, as stacks (what drops when it breaks). */
    static List<ItemStack> refund(ItemStack item, int count) {
        List<ItemStack> out = new ArrayList<>();
        if (item == null || count <= 0) return out;
        int max = Math.max(1, item.getMaxStackSize());
        while (count > 0) {
            ItemStack s = item.copy();
            s.stackSize = Math.min(count, max);
            count -= s.stackSize;
            out.add(s);
        }
        return out;
    }

    static ItemStack copyOne(ItemStack stack) {
        ItemStack one = stack.copy();
        one.stackSize = 1;
        return one;
    }

    static IChatComponent itemName(ItemStack stack) {
        return new ChatComponentTranslation(stack.getUnlocalizedName() + ".name");
    }

    static boolean isLootingItem(ItemStack stack) {
        return matches(TweaksConfig.lootSpawnerLootingItem, stack);
    }

    static boolean isSpeedItem(ItemStack stack) {
        return matches(TweaksConfig.lootSpawnerSpeedItem, stack);
    }

    /**
     * The speed item placed as a block right under it ({@link TweaksConfig#lootSpawnerSpeedBelow}): Extra Utilities'
     * cursed earth under a spawner speeds it up, so under a loot spawner it counts as one put in.
     */
    static boolean speedBelow(LootSpawnerTile t) {
        World w = t.getWorldObj();
        if (!TweaksConfig.lootSpawnerSpeedBelow || w == null || t.yCoord <= 0) return false;
        Item item = itemOf(TweaksConfig.lootSpawnerSpeedItem);
        Block want = item == null ? null : Block.getBlockFromItem(item);
        int x = t.xCoord, y = t.yCoord - 1, z = t.zCoord;
        if (want == null || want == Blocks.air || !w.blockExists(x, y, z) || w.getBlock(x, y, z) != want) return false;
        int meta = metaOf(TweaksConfig.lootSpawnerSpeedItem);
        return meta < 0 || want.damageDropped(w.getBlockMetadata(x, y, z)) == meta;
    }

    /** The item that makes a loot spawner turn its XP into bottles (empty or not an item: none does). */
    static boolean isXpItem(ItemStack stack) {
        return matches(TweaksConfig.lootSpawnerXpItem, stack);
    }

    /** Worn: a tool, weapon or piece of armour that has lost durability (mobs' gear drops worn). */
    static boolean damaged(ItemStack stack) {
        return stack != null && stack.getItem() != null && stack.isItemDamaged();
    }

    /** Into the player's inventory, or dropped at their feet if it doesn't fit. */
    static void give(EntityPlayer player, ItemStack stack) {
        player.inventory.addItemStackToInventory(stack);
        if (stack.stackSize > 0) player.dropPlayerItemWithRandomChoice(stack, false);
        player.inventoryContainer.detectAndSendChanges();
    }

    /** Whether the player could break the spawner (claims and other protection decide), or is an op. */
    public static boolean mayChange(EntityPlayer player, World world, int x, int y, int z) {
        if (player instanceof EntityPlayerMP && MinecraftServer.getServer() != null
            && MinecraftServer.getServer()
                .getConfigurationManager()
                .func_152596_g(((EntityPlayerMP) player).getGameProfile()))
            return true;
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(x, y, z, world, world.getBlock(x, y, z),
            world.getBlockMetadata(x, y, z), player);
        asking = true;
        try {
            return !MinecraftForge.EVENT_BUS.post(event);
        } finally {
            asking = false;
        }
    }

    /**
     * {@link #mayChange} for a plain spawner, made inert while it is asked: EnderIO runs a spawner up to 6 times on a
     * player's break event, even one that is only asked, which would spawn real mobs at the click.
     */
    static boolean mayChangeSpawner(EntityPlayer player, TileEntityMobSpawner spawner) {
        World world = spawner.getWorldObj();
        int x = spawner.xCoord, y = spawner.yCoord, z = spawner.zCoord;
        if (spawner instanceof LootSpawnerTile) return mayChange(player, world, x, y, z); // never active anyway
        NBTTagCompound own = new NBTTagCompound();
        spawner.writeToNBT(own);
        spawner.readFromNBT(LootSpawnerTile.inert(own));
        try {
            return mayChange(player, world, x, y, z);
        } finally {
            spawner.readFromNBT((NBTTagCompound) own.copy());
        }
    }

    // ---- breaking one spills what it holds ------------------------------------------------------------------------

    private static boolean asking; // our own permission check above, not a real break

    /**
     * The block is going (creative players, a Matter Manipulator, another mod; see {@link BlockLootSpawner#breakBlock}):
     * the loot it holds and what was paid into it drop where it was. A wrench empties it first, so nothing drops then.
     */
    static void spillAll(World w, LootSpawnerTile t) {
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) {
            ItemStack s = t.items[i];
            t.items[i] = null;
            if (s != null) drop(w, t.xCoord, t.yCoord, t.zCoord, s);
        }
        if (t.filterPaid != null) drop(w, t.xCoord, t.yCoord, t.zCoord, t.filterPaid);
        t.filterPaid = null;
        if (t.xpPaid != null) drop(w, t.xCoord, t.yCoord, t.zCoord, t.xpPaid);
        t.xpPaid = null;
        for (LootSpawnerTile.Upgrade u : new LootSpawnerTile.Upgrade[] { t.looting, t.speed }) {
            for (ItemStack s : u.refund()) drop(w, t.xCoord, t.yCoord, t.zCoord, s);
            u.paid = 0;
        }
    }

    /** Forgets what it holds and was paid, which a copy of it carries on (see {@link #toVanilla}): nothing spills. */
    static void empty(LootSpawnerTile t) {
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) t.items[i] = null;
        t.filterPaid = null;
        t.xpPaid = null;
        t.looting.paid = 0;
        t.speed.paid = 0;
    }

    /** Drops a stack in the middle of a block (pipes, crafters). */
    public static void dropAt(World w, int x, int y, int z, ItemStack s) {
        if (s != null && s.stackSize > 0) drop(w, x, y, z, s);
    }

    static void drop(World w, int x, int y, int z, ItemStack s) {
        net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(w, x + 0.5, y + 0.5, z + 0.5, s);
        item.delayBeforeCanPickup = 10;
        w.spawnEntityInWorld(item);
    }

    private static String warnedItem;

    /** A conversion item is set in the config but no such item exists (a typo): then none can be made, not free ones. */
    static boolean costUnknown() {
        if (TweaksConfig.lootSpawnerItem.trim().isEmpty() || convertItem() != null) return false;
        if (!TweaksConfig.lootSpawnerItem.equals(warnedItem)) {
            warnedItem = TweaksConfig.lootSpawnerItem;
            ValierTweaks.LOG.warn("lootSpawnerItem '{}' is not an item; no loot spawners can be made until it is fixed",
                warnedItem);
        }
        return true;
    }

    static Item convertItem() {
        return itemOf(TweaksConfig.lootSpawnerItem);
    }

    static int convertMeta() {
        return metaOf(TweaksConfig.lootSpawnerItem);
    }

    static boolean isConvertItem(ItemStack stack) {
        return matches(TweaksConfig.lootSpawnerItem, stack);
    }

    /** The item that makes a loot spawner throw damaged items away (empty or not an item: none does). */
    static boolean isFilterItem(ItemStack stack) {
        return matches(TweaksConfig.lootSpawnerFilterItem, stack);
    }

    /** The item a config setting names ("mod:item" or "mod:item:meta"), or null. */
    static Item itemOf(String setting) {
        String name = setting.trim();
        if (name.isEmpty()) return null;
        int colon = name.lastIndexOf(':');
        if (colon > name.indexOf(':')) name = name.substring(0, colon); // "mod:item:meta"
        Object item = Item.itemRegistry.getObject(name);
        return item instanceof Item && Item.itemRegistry.containsKey(name) ? (Item) item : null;
    }

    static int metaOf(String setting) {
        String name = setting.trim();
        int first = name.indexOf(':'), last = name.lastIndexOf(':');
        if (last <= first) return -1;
        try {
            return Integer.parseInt(name.substring(last + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static boolean matches(String setting, ItemStack stack) {
        Item item = itemOf(setting);
        if (stack == null || item == null || stack.getItem() != item) return false;
        int meta = metaOf(setting);
        return meta < 0 || stack.getItemDamage() == meta;
    }

    /**
     * A marked spawner (a vanilla spawner just marked, or a loot spawner from before 2.0.0, whose block is the vanilla
     * one) becomes the Valier Loot Spawner block where it stands, with the same data, and the neighbours are told (pipes
     * check again and connect to its inventory). On the block already: the tile just takes the data.
     */
    public static LootSpawnerTile swap(World world, TileEntityMobSpawner old) {
        NBTTagCompound tag = new NBTTagCompound();
        if (old instanceof LootSpawnerTile) ((LootSpawnerTile) old).writeAll(tag);
        else old.writeToNBT(tag);
        int x = old.xCoord, y = old.yCoord, z = old.zCoord;
        if (world.getBlock(x, y, z) != ModBlocks.lootSpawner) {
            if (old instanceof LootSpawnerTile) empty((LootSpawnerTile) old); // the copy carries it on
            world.setBlock(x, y, z, ModBlocks.lootSpawner, 0, 3); // the vanilla block and its tile go, nothing drops
        }
        TileEntity te = world.getTileEntity(x, y, z);
        LootSpawnerTile loot;
        if (te instanceof LootSpawnerTile) loot = (LootSpawnerTile) te;
        else {
            loot = new LootSpawnerTile();
            world.setTileEntity(x, y, z, loot);
        }
        loot.readFromNBT(tag);
        loot.markDirty();
        world.markBlockForUpdate(x, y, z);
        world.notifyBlocksOfNeighborChange(x, y, z, ModBlocks.lootSpawner);
        return loot;
    }

    /**
     * The other way: a loot spawner block becomes a marked vanilla spawner with the same data (for {@link #revert}, and
     * before the mod is removed). Null if the vanilla block didn't take.
     */
    static TileEntityMobSpawner toVanilla(World world, LootSpawnerTile t) {
        NBTTagCompound tag = new NBTTagCompound();
        t.writeAll(tag);
        tag.setString("id", "MobSpawner");
        int x = t.xCoord, y = t.yCoord, z = t.zCoord;
        empty(t); // the copy carries it on: nothing spills
        world.setBlock(x, y, z, Blocks.mob_spawner, 0, 3);
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityMobSpawner)) return null;
        if (te.getClass() != TileEntityMobSpawner.class) { // ours, or another mod's, must not handle it
            te = new TileEntityMobSpawner();
            world.setTileEntity(x, y, z, te);
        }
        te.readFromNBT(tag);
        te.markDirty();
        world.markBlockForUpdate(x, y, z);
        return (TileEntityMobSpawner) te;
    }

    /** Loot spawners of the old kind found as their chunks loaded, made the block at the end of the tick. */
    private static final List<Object[]> pending = new ArrayList<>();

    static void convertPending() {
        if (pending.isEmpty() || RevertBlocks.reverting) return;
        List<Object[]> now = new ArrayList<>(pending);
        pending.clear();
        for (Object[] p : now) {
            World world = (World) p[0];
            int x = (Integer) p[1], y = (Integer) p[2], z = (Integer) p[3];
            if (!world.blockExists(x, y, z) || world.getBlock(x, y, z) != Blocks.mob_spawner) continue;
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof LootSpawnerTile) {
                swap(world, (TileEntityMobSpawner) te);
                ValierTweaks.LOG.info("The {} loot spawner at {} is a Valier Loot Spawner block now",
                    ((LootSpawnerTile) world.getTileEntity(x, y, z)).logic()
                        .getEntityNameToSpawn(),
                    ((LootSpawnerTile) world.getTileEntity(x, y, z)).where());
            }
        }
    }

    // ---- keeping them loot spawners -------------------------------------------------------------------------------

    /**
     * A loot spawner from before 2.0.0 is saved as a marked vanilla spawner. As its chunk loads (its tiles are made, not
     * yet running) its tile is swapped for a loot spawner tile before anything sees the vanilla one, and at the end of
     * the tick it becomes the block ({@link #convertPending}: no block changes while a chunk is still loading).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChunkLoad(ChunkDataEvent.Load event) {
        if (!TweaksConfig.lootSpawners || event.world == null || event.world.isRemote) return;
        Chunk chunk = event.getChunk();
        List<TileEntityMobSpawner> marked = null;
        for (Object te : chunk.chunkTileEntityMap.values()) {
            if (te.getClass() == TileEntityMobSpawner.class && LootSpawnerTile.isMarked((TileEntityMobSpawner) te)) {
                if (marked == null) marked = new ArrayList<>();
                marked.add((TileEntityMobSpawner) te);
            }
        }
        if (marked == null) return;
        for (TileEntityMobSpawner old : marked) {
            NBTTagCompound tag = new NBTTagCompound();
            old.writeToNBT(tag);
            LootSpawnerTile loot = new LootSpawnerTile();
            loot.readFromNBT(tag);
            chunk.func_150812_a(old.xCoord & 15, old.yCoord, old.zCoord & 15, loot); // replaces (and invalidates) the old
            pending.add(new Object[] { event.world, old.xCoord, old.yCoord, old.zCoord });
        }
    }

    /**
     * Every 2 seconds: any marked vanilla spawner that turned up some other way (moved with a dolly, pasted by a
     * tool) becomes a loot spawner again. Checking a tile's class is all it costs for the rest.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        convertPending();
        if (++tick % 40 == 0) scan();
    }

    /**
     * Turns the old kind into the block (marked vanilla spawners, and loot spawner tiles still on the vanilla block);
     * with loot spawners switched off, every loot spawner becomes a plain spawner instead. Returns how many changed.
     */
    static int scan() {
        int swapped = 0;
        for (WorldServer world : DimensionManager.getWorlds()) {
            List<TileEntityMobSpawner> found = null;
            for (Object o : world.loadedTileEntityList) {
                TileEntity te = (TileEntity) o;
                if (te.isInvalid() || !(te instanceof TileEntityMobSpawner)) continue;
                boolean old = te.getClass() == TileEntityMobSpawner.class && LootSpawnerTile.isMarked((TileEntityMobSpawner) te)
                    || te instanceof LootSpawnerTile && world.getBlock(te.xCoord, te.yCoord, te.zCoord) == Blocks.mob_spawner;
                boolean off = !TweaksConfig.lootSpawners && te instanceof LootSpawnerTile;
                if (old || off) {
                    if (found == null) found = new ArrayList<>();
                    found.add((TileEntityMobSpawner) te);
                }
            }
            if (found == null) continue;
            for (TileEntityMobSpawner t : found) {
                if (world.getTileEntity(t.xCoord, t.yCoord, t.zCoord) != t) continue;
                if (TweaksConfig.lootSpawners) {
                    if (RevertBlocks.reverting) continue; // before the mod is removed: they stay vanilla
                    swap(world, t);
                } else if (t instanceof LootSpawnerTile && world.getBlock(t.xCoord, t.yCoord, t.zCoord) == ModBlocks.lootSpawner) {
                    TileEntityMobSpawner plain = toVanilla(world, (LootSpawnerTile) t);
                    if (plain != null) revert(world, plain);
                } else revert(world, t);
                swapped++;
            }
        }
        return swapped;
    }

    /**
     * With loot spawners switched off: a marked spawner becomes a plain spawner of the same mob again, as it was before
     * (no spawn data if it had none, so its mobs are dressed again; the mark gone from its spawn list), ready to spawn
     * in a second. What it held drops on top of it.
     */
    static void revert(World world, TileEntityMobSpawner spawner) {
        NBTTagCompound tag = new NBTTagCompound();
        spawner.writeToNBT(tag);
        NBTTagCompound data = tag.getCompoundTag("SpawnData");
        NBTTagCompound mark = data.getCompoundTag(LootSpawnerTile.MARK);
        net.minecraft.nbt.NBTTagList items = mark.getTagList("Items", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            ItemStack s = ItemStack.loadItemStackFromNBT(items.getCompoundTagAt(i));
            if (s != null) drop(world, spawner.xCoord, spawner.yCoord + 1, spawner.zCoord, s);
        }
        ItemStack paid = mark.hasKey("Paid", 10) ? ItemStack.loadItemStackFromNBT(mark.getCompoundTag("Paid")) : null;
        if (paid != null) drop(world, spawner.xCoord, spawner.yCoord + 1, spawner.zCoord, paid);
        ItemStack xpPaid = mark.hasKey("XpPaid", 10) ? ItemStack.loadItemStackFromNBT(mark.getCompoundTag("XpPaid")) : null;
        if (xpPaid != null) drop(world, spawner.xCoord, spawner.yCoord + 1, spawner.zCoord, xpPaid);
        for (Kind kind : Kind.values()) {
            LootSpawnerTile.Upgrade u = new LootSpawnerTile.Upgrade(kind);
            u.read(mark);
            for (ItemStack s : u.refund()) drop(world, spawner.xCoord, spawner.yCoord + 1, spawner.zCoord, s);
        }
        data.removeTag(LootSpawnerTile.MARK);
        if (mark.getBoolean("Egg")) {
            tag.removeTag("SpawnData");
            tag.removeTag("SpawnPotentials");
        } else {
            net.minecraft.nbt.NBTTagList potentials = tag.getTagList("SpawnPotentials", 10);
            for (int i = 0; i < potentials.tagCount(); i++) potentials.getCompoundTagAt(i)
                .getCompoundTag("Properties")
                .removeTag(LootSpawnerTile.MARK);
        }
        tag.setShort("Delay", (short) 20);
        spawner.readFromNBT(tag);
        spawner.markDirty();
        world.markBlockForUpdate(spawner.xCoord, spawner.yCoord, spawner.zCoord);
        ValierTweaks.LOG.info("Loot spawners are off: the {} spawner at {} {} {} is a plain spawner again",
            spawner.func_145881_a().getEntityNameToSpawn(), spawner.xCoord, spawner.yCoord, spawner.zCoord);
    }

    // ---- making loot ----------------------------------------------------------------------------------------------

    /** Once a second per loot spawner: hand out what it holds, count down, and roll the next batch when it's time. */
    static void cycle(LootSpawnerTile t) {
        World world = t.getWorldObj();
        if (!TweaksConfig.lootSpawners || !(world instanceof WorldServer)) return;
        if (TweaksConfig.lootSpawnerNeedsPlayer
            && world.getClosestPlayer(t.xCoord + 0.5, t.yCoord + 0.5, t.zCoord + 0.5, 16.0) == null) {
            t.status = Status.NO_PLAYER;
            return;
        }
        if (t.status == Status.NO_PLAYER) t.status = Status.WORKING;
        boolean outputs = flush(t);
        if (t.timer < 0) t.timer = nextDelay(t, world);
        if (t.timer > 0) {
            t.timer -= 20;
            if (t.timer > 0) {
                if (t.status == Status.STARTING) t.status = outputs ? Status.WORKING : Status.NO_OUTPUT;
                return;
            }
        }
        if (!t.isEmpty()) {
            t.status = Status.FULL;
            return;
        }
        if (!outputs) {
            t.status = Status.NO_OUTPUT;
            return;
        }
        // One mob at a time: its loot goes out (what doesn't fit is kept). Stop early if what it keeps gets near full.
        boolean any = false;
        int[] xp = t.xpBottles ? new int[1] : null;
        int looting = t.looting.level();
        for (int i = 0; i < Math.max(1, t.spawnCount) && t.freeSlots() >= 9; i++) {
            List<ItemStack> loot = MobLoot.roll((WorldServer) world, t.xCoord + 0.5, t.yCoord + 1.0, t.zCoord + 0.5,
                MobLoot.pick(t.candidates, world.rand), xp, looting);
            any |= !loot.isEmpty();
            for (ItemStack stack : MobLoot.merge(loot)) {
                if (t.noDamaged && damaged(stack)) continue; // thrown away
                ItemStack left = insertAround(world, t.xCoord, t.yCoord, t.zCoord, stack);
                if (left != null) left = t.addToBuffer(left);
                if (left != null) drop(world, t.xCoord, t.yCoord + 1, t.zCoord, left); // more than 27 stacks: never lose it
            }
        }
        if (xp != null) { // the XP as bottles o' enchanting, the rest kept for the next batch
            int per = Math.max(1, TweaksConfig.lootSpawnerXpPerBottle);
            t.xp += xp[0];
            int bottles = t.xp / per;
            t.xp -= bottles * per;
            any |= bottles > 0;
            while (bottles > 0) {
                ItemStack stack = new ItemStack(Items.experience_bottle, Math.min(bottles, 64));
                bottles -= stack.stackSize;
                ItemStack left = insertAround(world, t.xCoord, t.yCoord, t.zCoord, stack);
                if (left != null) left = t.addToBuffer(left);
                if (left != null) drop(world, t.xCoord, t.yCoord + 1, t.zCoord, left);
            }
        }
        t.rolls++;
        t.timer = nextDelay(t, world);
        t.status = !any && allBlocked(t) ? Status.NOTHING : t.isEmpty() ? Status.WORKING : Status.FULL;
        t.markDirty();
        world.markBlockForUpdate(t.xCoord, t.yCoord, t.zCoord); // clients' spin speed follows the new timer
    }

    private static boolean allBlocked(LootSpawnerTile t) {
        for (MobLoot.Candidate c : t.candidates) if (!MobLoot.blocked(c.type)) return false;
        return true;
    }

    static int nextDelay(LootSpawnerTile t, World world) {
        int min = Math.max(1, t.minDelay), max = t.maxDelay;
        int ticks = max <= min ? min : min + world.rand.nextInt(max - min);
        return Math.max(20, (int) Math.round(ticks / (Math.max(0.01, TweaksConfig.lootSpawnerSpeed) * t.speedFactor())));
    }

    /** Pushes what the spawner holds to its neighbours. Returns whether anything that can take items touches it. */
    static boolean flush(LootSpawnerTile t) {
        World world = t.getWorldObj();
        boolean any = false;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (accepts(world, t.xCoord + d.offsetX, t.yCoord + d.offsetY, t.zCoord + d.offsetZ, d.getOpposite())) {
                any = true;
                break;
            }
        }
        if (!any) return false;
        boolean changed = false;
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) {
            ItemStack s = t.items[i];
            if (s == null) continue;
            int before = s.stackSize;
            ItemStack left = insertAround(world, t.xCoord, t.yCoord, t.zCoord, s);
            if (left != s || (left != null && left.stackSize != before)) changed = true;
            t.items[i] = left;
        }
        if (changed) t.markDirty();
        return true;
    }

    private static TileEntity neighbour(World world, int x, int y, int z) {
        if (y < 0 || y >= world.getHeight() || !world.blockExists(x, y, z)) return null; // never load a chunk
        TileEntity te = world.getTileEntity(x, y, z);
        return te == null || te.isInvalid() || te instanceof LootSpawnerTile ? null : te;
    }

    /** Something that can take items touches the spawner here (from side {@code from}, its side facing the spawner). */
    static boolean accepts(World world, int x, int y, int z, ForgeDirection from) {
        TileEntity te = neighbour(world, x, y, z);
        if (te == null) return false;
        if (te instanceof com.valier.tweaks.pipe.TilePipe) // a Valier pipe pulling from it takes items (2.0.0)
            return ((com.valier.tweaks.pipe.TilePipe) te).pullsFrom(from.ordinal());
        if (te instanceof IInventory) {
            // GregTech cables and the like are inventories with no slots.
            if (te instanceof net.minecraft.inventory.ISidedInventory)
                return ((net.minecraft.inventory.ISidedInventory) te).getAccessibleSlotsFromSide(from.ordinal()).length > 0;
            return ((IInventory) te).getSizeInventory() > 0;
        }
        return (ITEM_DUCT != null && ITEM_DUCT.isInstance(te) && DUCT_INSERT != null)
            || (INJECTABLE != null && INJECTABLE.isInstance(te) && INJECT != null);
    }

    /** Offers a stack to the six neighbours in turn (down, up, north, south, west, east). Returns what is left. */
    static ItemStack insertAround(World world, int x, int y, int z, ItemStack stack) {
        // Storage first (chests, barrels, drawers, machines), then pipes and conduits (1.14.0): a GregTech item pipe
        // takes items from any side, so one that brings berries to an XP crafter would otherwise get its output first
        // and carry it into other crafters' tanks.
        for (int pass = 0; pass < 2; pass++) {
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                if (stack == null || stack.stackSize <= 0) return null;
                TileEntity te = neighbour(world, x + d.offsetX, y + d.offsetY, z + d.offsetZ);
                if (te == null || isPipe(te) != (pass == 1)) continue;
                stack = insertInto(world, x + d.offsetX, y + d.offsetY, z + d.offsetZ, d.getOpposite(), stack);
            }
        }
        return stack == null || stack.stackSize <= 0 ? null : stack;
    }

    /** Something that carries items on (a GregTech pipe, a conduit or pipe that isn't an inventory), not storage. */
    static boolean isPipe(TileEntity te) {
        return !(te instanceof IInventory) || (GT_PIPE != null && GT_PIPE.isInstance(te));
    }

    /**
     * Into one neighbour, entering from side {@code from} (its side facing the spawner): an inventory (a double chest
     * as one, sided inventories by their own rules, the way a hopper does it), else an EnderIO conduit, else a
     * BuildCraft pipe.
     */
    static ItemStack insertInto(World world, int x, int y, int z, ForgeDirection from, ItemStack stack) {
        TileEntity te = neighbour(world, x, y, z);
        if (te == null) return stack;
        try {
            if (te instanceof IInventory) {
                // A double chest as one, like a hopper does, unless that would mean loading the chunk next to it.
                boolean around = world.blockExists(x + 1, y, z) && world.blockExists(x - 1, y, z)
                    && world.blockExists(x, y, z + 1) && world.blockExists(x, y, z - 1);
                IInventory inv = te instanceof TileEntityChest && around ? TileEntityHopper.func_145893_b(world, x, y, z) : null;
                if (inv == null) inv = (IInventory) te;
                return TileEntityHopper.func_145889_a(inv, stack, from.ordinal());
            }
            if (ITEM_DUCT != null && DUCT_INSERT != null && ITEM_DUCT.isInstance(te)) {
                ItemStack left = (ItemStack) DUCT_INSERT.invoke(te, from, stack.copy());
                return left == null || left.stackSize <= 0 ? null : left;
            }
            if (INJECTABLE != null && INJECT != null && INJECTABLE.isInstance(te)) {
                int taken = (Integer) INJECT.invoke(te, stack.copy(), true, from, null);
                if (taken <= 0) return stack;
                ItemStack left = stack.copy();
                left.stackSize -= taken;
                return left.stackSize > 0 ? left : null;
            }
        } catch (Throwable e) {
            ValierTweaks.LOG.debug("Loot spawner: {} at {} {} {} wouldn't take items: {}", te.getClass()
                .getName(), x, y, z, e);
        }
        return stack;
    }

    // ---- messages -------------------------------------------------------------------------------------------------

    static IChatComponent status(LootSpawnerTile t) {
        String mobs = t.candidates.size() == 1 ? null : t.candidates.size() + " kinds of mob";
        if (t.candidates.isEmpty()) mobs = "nothing";
        IChatComponent msg = text(EnumChatFormatting.GOLD, "Loot spawner: ");
        if (mobs != null) msg.appendSibling(new ChatComponentText(mobs));
        else msg.appendSibling(mobName(t.candidates.get(0).type));
        int count = Math.max(1, t.spawnCount);
        double speed = Math.max(0.01, TweaksConfig.lootSpawnerSpeed) * t.speedFactor();
        msg.appendSibling(new ChatComponentText(". Every " + seconds(t.minDelay / speed) + "–" + seconds(t.maxDelay / speed)
            + " s it makes the loot of " + count + (count == 1 ? " mob" : " mobs")
            + " and puts it into the chests, inventories and pipes touching it. Now: " + t.status.words));
        int held = t.stacksHeld();
        if (held > 0) msg.appendSibling(new ChatComponentText(" (holding " + held + (held == 1 ? " stack" : " stacks") + ")"));
        if (t.timer > 0 && t.status != Status.NO_PLAYER) msg.appendSibling(new ChatComponentText(", next batch in " + seconds(t.timer) + " s"));
        msg.appendSibling(new ChatComponentText("."));
        if (t.noDamaged) msg.appendSibling(new ChatComponentText(" Damaged items are thrown away."));
        else if (itemOf(TweaksConfig.lootSpawnerFilterItem) != null) msg.appendSibling(new ChatComponentText(" Sneak-right-click it with ")
            .appendSibling(costText(TweaksConfig.lootSpawnerFilterItem, TweaksConfig.lootSpawnerFilterItemCount))
            .appendSibling(new ChatComponentText(" to throw damaged items away.")));
        if (t.xpBottles) msg.appendSibling(new ChatComponentText(" Its XP comes out as bottles o' enchanting (" + t.xp + " of "
            + Math.max(1, TweaksConfig.lootSpawnerXpPerBottle) + " XP towards the next)."));
        else if (itemOf(TweaksConfig.lootSpawnerXpItem) != null) msg.appendSibling(new ChatComponentText(" Sneak-right-click it with ")
            .appendSibling(costText(TweaksConfig.lootSpawnerXpItem, TweaksConfig.lootSpawnerXpItemCount))
            .appendSibling(new ChatComponentText(" to turn its XP into bottles o' enchanting.")));
        for (Kind kind : Kind.values()) {
            LootSpawnerTile.Upgrade u = kind.of(t);
            int below = kind == Kind.SPEED && speedBelow(t) ? 1 : 0;
            int level = kind.level(u.blocks + below);
            int[] tiers = kind.tiers();
            if (level > 0) msg.appendSibling(new ChatComponentText(" " + kind.word + " " + roman(level)
                + (kind == Kind.SPEED ? ", " + (level + 1) + "× as fast" : "") + " (" + u.blocks + " in it"
                + (below > 0 ? " and one under it" : "") + (level < tiers.length ? "; " + (tiers[level] - u.blocks - below)
                    + " more for " + kind.word + " " + roman(level + 1) : "; the most")
                + ")."));
            else if (itemOf(kind.item()) != null) msg.appendSibling(new ChatComponentText(" "))
                .appendSibling(tiersText(kind, true));
        }
        if (t.looting.blocks > 0 || t.speed.blocks > 0 || t.xpBottles || t.noDamaged)
            msg.appendSibling(new ChatComponentText(" Sneak and hit it with an empty hand to take its blocks back."));
        if (TweaksConfig.lootSpawnerWrench) msg.appendSibling(new ChatComponentText(" A wrench picks it up."));
        if (TweaksConfig.lootSpawnerSpawnEggs) msg.appendSibling(new ChatComponentText(" A spawn egg changes its mob."));
        return msg;
    }

    /** "a Block of Diamond", "2 × Block of Emerald", or "an empty hand" when the setting names no item. */
    static IChatComponent costText(String setting, int count) {
        Item item = itemOf(setting);
        if (item == null) return new ChatComponentText("an empty hand");
        ItemStack shown = new ItemStack(item, 1, Math.max(0, metaOf(setting)));
        return new ChatComponentText(count != 1 ? count + " × " : "a ")
            .appendSibling(new ChatComponentTranslation(shown.getUnlocalizedName() + ".name"));
    }

    static IChatComponent hint(TileEntityMobSpawner spawner) {
        IChatComponent msg = text(EnumChatFormatting.GRAY, "Sneak-right-click this spawner with ")
            .appendSibling(costText(TweaksConfig.lootSpawnerItem, TweaksConfig.lootSpawnerItemCount));
        msg.appendSibling(new ChatComponentText(" to make it a loot spawner: no more mobs, just the loot of each "))
            .appendSibling(mobName(spawner.func_145881_a()
                .getEntityNameToSpawn()))
            .appendSibling(new ChatComponentText(" it would have spawned, put into the chests and pipes touching it."));
        return msg;
    }

    private static String seconds(double ticks) {
        return Integer.toString((int) Math.round(ticks / 20.0));
    }

    private static IChatComponent mobName(String type) {
        return new ChatComponentTranslation("entity." + type + ".name");
    }

    private static IChatComponent text(EnumChatFormatting colour, String s) {
        IChatComponent c = new ChatComponentText(s);
        c.getChatStyle()
            .setColor(colour);
        return c;
    }

    private static void tell(EntityPlayer player, IChatComponent msg) {
        player.addChatMessage(msg);
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, LootSpawners.class.getClassLoader());
        } catch (Throwable e) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... args) {
        if (owner == null) return null;
        try {
            return owner.getMethod(name, args);
        } catch (Throwable e) {
            return null;
        }
    }
}
