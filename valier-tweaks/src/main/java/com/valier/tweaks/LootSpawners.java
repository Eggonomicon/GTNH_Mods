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
        if (!TweaksConfig.lootSpawners || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        EntityPlayer player = event.entityPlayer;
        World world = event.world;
        if (world.isRemote || !player.isSneaking()) return;
        int x = event.x, y = event.y, z = event.z;
        if (world.getBlock(x, y, z) != Blocks.mob_spawner) return;
        TileEntity te = world.getTileEntity(x, y, z);
        ItemStack held = player.getHeldItem();
        if (te instanceof LootSpawnerTile) {
            if (TweaksConfig.lootSpawnerWrench && isWrench(held, player, x, y, z)) {
                event.setCanceled(true);
                pickUp(player, (LootSpawnerTile) te, held);
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
    static boolean isWrench(ItemStack held, EntityPlayer player, int x, int y, int z) {
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
    static void wrenchUsed(ItemStack held, EntityPlayer player, int x, int y, int z) {
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
        world.playAuxSFX(2001, x, y, z, Block.getIdFromBlock(Blocks.mob_spawner)); // the sound and bits of a break
        world.setBlockToAir(x, y, z); // no break or harvest event: nothing else drops
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
     * A vanilla spawner item named "Valier Loot Spawner (Zombie)", holding the spawner in {@value #ITEM_TAG}; its damage
     * is the mob's id, so clients with NEI draw the right mob in it.
     */
    static ItemStack toItem(LootSpawnerTile t) {
        String type = t.logic()
            .getEntityNameToSpawn();
        String mob = mobWords(type);
        ItemStack stack = new ItemStack(Blocks.mob_spawner, 1, entityId(type));
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag(ITEM_TAG, t.saveForItem());
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", EnumChatFormatting.RESET + "" + EnumChatFormatting.GOLD + "Valier Loot Spawner"
            + EnumChatFormatting.RESET + " (" + mob + ")");
        String grey = EnumChatFormatting.RESET + "" + EnumChatFormatting.GRAY;
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString(grey + "Makes the loot of each " + mob + ", no mobs."));
        if (t.noDamaged) lore.appendTag(new NBTTagString(grey + "Throws damaged items away."));
        lore.appendTag(new NBTTagString(grey + "Place it and it is a loot spawner again."));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
        return stack;
    }

    /** The spawner a Valier Loot Spawner item holds, or null for any other item. */
    static NBTTagCompound savedSpawner(ItemStack stack) {
        if (stack == null || stack.getItem() != Item.getItemFromBlock(Blocks.mob_spawner) || !stack.hasTagCompound()) return null;
        NBTTagCompound tag = stack.getTagCompound();
        return tag.hasKey(ITEM_TAG, 10) ? tag.getCompoundTag(ITEM_TAG) : null;
    }

    /**
     * A Valier Loot Spawner item placed: the spawner vanilla just made (a pig spawner) gets what the item holds and
     * becomes a loot spawner. Lowest priority and not for cancelled placements, so claims that stop it win.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (event.world.isRemote || event.placedBlock != Blocks.mob_spawner) return;
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

    /** The mob's global entity id (what NEI's spawner item uses), or 0 for mobs without one. */
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
    static boolean mayChange(EntityPlayer player, World world, int x, int y, int z) {
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
    private static final List<LootSpawnerTile> spills = new ArrayList<>();

    /**
     * A player breaking a loot spawner: what it holds is dropped next tick, once the block is really gone (a break
     * event can still be cancelled, or be only a permission check). A dolly moving it fires no break, and its loot
     * travels with it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (asking || event.world.isRemote || event.block != Blocks.mob_spawner) return;
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (te instanceof LootSpawnerTile && ((LootSpawnerTile) te).hasDrops()) {
            ((LootSpawnerTile) te).savedSinceBreak = false;
            spills.add((LootSpawnerTile) te);
        }
    }

    /**
     * Blocks that drop before they are removed (explosions, the Wither, a mod breaking blocks with drops): the tile is
     * still there, so what it holds joins the drops.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHarvest(BlockEvent.HarvestDropsEvent event) {
        if (event.world.isRemote || event.block != Blocks.mob_spawner) return;
        TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(te instanceof LootSpawnerTile)) return;
        LootSpawnerTile t = (LootSpawnerTile) te;
        for (int i = 0; i < LootSpawnerTile.SLOTS; i++) {
            if (t.items[i] != null) event.drops.add(t.items[i]);
            t.items[i] = null;
        }
        if (t.filterPaid != null) event.drops.add(t.filterPaid);
        t.filterPaid = null;
        t.markDirty();
    }

    static void spill() {
        if (spills.isEmpty()) return;
        for (LootSpawnerTile t : spills) {
            World w = t.getWorldObj();
            // Still there (the break was cancelled or only a check), or saved since (a mover took it, loot and all).
            if (w == null || !t.isInvalid() || t.savedSinceBreak) continue;
            if (w.blockExists(t.xCoord, t.yCoord, t.zCoord) && w.getBlock(t.xCoord, t.yCoord, t.zCoord) == Blocks.mob_spawner)
                continue;
            for (int i = 0; i < LootSpawnerTile.SLOTS; i++) {
                ItemStack s = t.items[i];
                t.items[i] = null;
                if (s != null) drop(w, t.xCoord, t.yCoord, t.zCoord, s);
            }
            if (t.filterPaid != null) drop(w, t.xCoord, t.yCoord, t.zCoord, t.filterPaid);
            t.filterPaid = null;
        }
        spills.clear();
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
     * Replaces a marked vanilla spawner tile with a loot spawner tile holding the same data, and tells the neighbours
     * (pipes check again and connect to its inventory).
     */
    static LootSpawnerTile swap(World world, TileEntityMobSpawner old) {
        NBTTagCompound tag = new NBTTagCompound();
        old.writeToNBT(tag);
        LootSpawnerTile loot = new LootSpawnerTile();
        loot.readFromNBT(tag);
        int x = old.xCoord, y = old.yCoord, z = old.zCoord;
        world.setTileEntity(x, y, z, loot);
        loot.markDirty();
        world.markBlockForUpdate(x, y, z);
        world.notifyBlocksOfNeighborChange(x, y, z, Blocks.mob_spawner);
        return loot;
    }

    // ---- keeping them loot spawners -------------------------------------------------------------------------------

    /**
     * A loot spawner is saved as a marked vanilla spawner. As its chunk loads (its tiles are made, not yet running),
     * swap it back before anything sees the vanilla tile.
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
        }
    }

    /**
     * Every 2 seconds: any marked vanilla spawner that turned up some other way (moved with a dolly, pasted by a
     * tool) becomes a loot spawner again. Checking a tile's class is all it costs for the rest.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        spill();
        if (++tick % 40 == 0) scan();
    }

    static int scan() {
        int swapped = 0;
        for (WorldServer world : DimensionManager.getWorlds()) {
            List<TileEntityMobSpawner> marked = null;
            for (Object te : world.loadedTileEntityList) {
                if (te.getClass() == TileEntityMobSpawner.class && !((TileEntity) te).isInvalid()
                    && LootSpawnerTile.isMarked((TileEntityMobSpawner) te)) {
                    if (marked == null) marked = new ArrayList<>();
                    marked.add((TileEntityMobSpawner) te);
                }
            }
            if (marked == null) continue;
            for (TileEntityMobSpawner old : marked) {
                if (world.getTileEntity(old.xCoord, old.yCoord, old.zCoord) != old) continue;
                if (TweaksConfig.lootSpawners) swap(world, old);
                else revert(world, old);
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
        for (int i = 0; i < Math.max(1, t.spawnCount) && t.freeSlots() >= 9; i++) {
            List<ItemStack> loot = MobLoot.roll((WorldServer) world, t.xCoord + 0.5, t.yCoord + 1.0, t.zCoord + 0.5,
                MobLoot.pick(t.candidates, world.rand));
            any |= !loot.isEmpty();
            for (ItemStack stack : MobLoot.merge(loot)) {
                if (t.noDamaged && damaged(stack)) continue; // thrown away
                ItemStack left = insertAround(world, t.xCoord, t.yCoord, t.zCoord, stack);
                if (left != null) left = t.addToBuffer(left);
                if (left != null) drop(world, t.xCoord, t.yCoord + 1, t.zCoord, left); // more than 27 stacks: never lose it
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
        return Math.max(20, (int) Math.round(ticks / Math.max(0.01, TweaksConfig.lootSpawnerSpeed)));
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
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (stack == null || stack.stackSize <= 0) return null;
            stack = insertInto(world, x + d.offsetX, y + d.offsetY, z + d.offsetZ, d.getOpposite(), stack);
        }
        return stack == null || stack.stackSize <= 0 ? null : stack;
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
        double speed = Math.max(0.01, TweaksConfig.lootSpawnerSpeed);
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
        if (TweaksConfig.lootSpawnerWrench) msg.appendSibling(new ChatComponentText(" A wrench picks it up."));
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
