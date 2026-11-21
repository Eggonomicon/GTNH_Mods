package com.valier.tweaks.client;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.valier.tweaks.LootSpawnerTile;
import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierGui;
import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.grid.GridMenu;
import com.valier.tweaks.grid.GridTier;
import com.valier.tweaks.grid.TileGrid;
import com.valier.tweaks.grid.TileGridCell;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.PipeGui;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;
import com.valier.tweaks.storage.BarrelMenu;
import com.valier.tweaks.storage.BarrelTier;
import com.valier.tweaks.storage.SatchelInventory;
import com.valier.tweaks.storage.SatchelTier;
import com.valier.tweaks.storage.TileBarrel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * The dev client's scene (single player's server side, see {@link DevClient}): every pipe tier of both kinds in a row,
 * pipes between chests with one face of each mode, a pipe network turning a corner, a loot spawner (cow) and an XP
 * crafter. Later it opens a pipe face's menu for the screenshot.
 * <p>
 * 2.2 and 2.3: the loot spawner gets some loot and a Void list, a barrel of every tier stands filled beside two linked
 * LV barrels sharing 639 iron ingots, the Grid's blocks (all tiers) are on one network with two docks beside them, and
 * the loot, barrel, Satchel, Flask and Grid menus open one after another.
 */
public final class DevScene {

    static volatile boolean built;
    static volatile int tick;
    static int baseX, baseY, baseZ;
    /** Where 2.3's blocks stand: barrels from x+20 (z 0), the Grid and the docks at z+8. */
    static final int BARRELS_X = 20, GRID_Z = 8;
    /** The server's side of the timeline (DevClient moves the camera and takes the shots to match). */
    static final int LOOT_MENU = 376, BARREL_MENU = 416, SATCHEL_MENU = 440, FLASK_MENU = 464, GRID_MENU = 488;
    private int wait;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        WorldServer w = DimensionManager.getWorld(0);
        if (w == null || w.playerEntities.isEmpty()) return;
        if (!built) {
            if (++wait < 40) return;
            build(w);
            built = true;
            return;
        }
        tick++;
        if (tick == 215) { // the camera (the player) is beside the pipe by now: menus close beyond 8 blocks
            EntityPlayerMP p = (EntityPlayerMP) w.playerEntities.get(0);
            TileEntity te = w.getTileEntity(baseX + 2, baseY, baseZ + 6);
            if (te instanceof TilePipe) PipeGui.open(p, (TilePipe) te, ForgeDirection.WEST.ordinal());
        }
        EntityPlayerMP p = (EntityPlayerMP) w.playerEntities.get(0);
        int y = baseY;
        if (tick == LOOT_MENU) p.openGui(ValierTweaks.instance, ValierGui.LOOT, w, baseX + 14, y, baseZ);
        if (tick == BARREL_MENU) p.openGui(ValierTweaks.instance, ValierGui.BARREL, w, baseX + BARRELS_X + 10, y, baseZ);
        if (tick == SATCHEL_MENU - 8 || tick == FLASK_MENU - 8) p.closeScreen();
        if (tick == SATCHEL_MENU - 6) hand(p, satchel());
        if (tick == FLASK_MENU - 6) hand(p, flask());
        if (tick == SATCHEL_MENU || tick == FLASK_MENU) p.openGui(ValierTweaks.instance, ValierGui.SATCHEL, w, p.inventory.currentItem, 0, 0);
        if (tick == GRID_MENU) p.openGui(ValierTweaks.instance, ValierGui.GRID, w, baseX + BARRELS_X + 4, y, baseZ + GRID_Z);
    }

    void build(WorldServer w) {
        baseX = 0;
        baseZ = 0;
        baseY = w.getHeightValue(0, 0);
        int y = baseY;
        // Every tier, item then fluid, standing alone (just the core).
        for (int t = 0; t < PipeTier.COUNT; t++) {
            pipe(w, baseX + t * 2, y, baseZ, ModBlocks.itemPipe, t);
            pipe(w, baseX + t * 2, y, baseZ + 2, ModBlocks.fluidPipe, t);
        }
        // A chest, two pipes, a chest: four faces, one per mode (Provide, Insert, Extract, Request) on two rigs.
        rig(w, baseX, y, baseZ + 6, FaceMode.EXTRACT, FaceMode.INSERT, PipeTier.ELITE);
        rig(w, baseX, y, baseZ + 9, FaceMode.PROVIDE, FaceMode.REQUEST, PipeTier.ADVANCED);
        // A line turning a corner and climbing.
        for (int i = 0; i < 4; i++) pipe(w, baseX + 8 + i, y, baseZ + 6, ModBlocks.itemPipe, PipeTier.BASIC);
        for (int i = 1; i < 4; i++) pipe(w, baseX + 11, y, baseZ + 6 + i, ModBlocks.itemPipe, PipeTier.BASIC);
        for (int i = 1; i < 3; i++) pipe(w, baseX + 11, y + i, baseZ + 9, ModBlocks.itemPipe, PipeTier.BASIC);
        // A loot spawner and an XP crafter.
        w.setBlock(baseX + 14, y, baseZ, Blocks.mob_spawner);
        TileEntity te = w.getTileEntity(baseX + 14, y, baseZ);
        if (te instanceof TileEntityMobSpawner) {
            ((TileEntityMobSpawner) te).func_145881_a()
                .setEntityName("Cow");
            com.valier.tweaks.LootSpawnerTile.mark((TileEntityMobSpawner) te);
            com.valier.tweaks.LootSpawners.swap(w, (TileEntityMobSpawner) te);
        }
        w.setBlock(baseX + 14, y, baseZ + 3, ModBlocks.xpCrafter);
        loot(w, baseX + 14, y, baseZ);
        if (!w.playerEntities.isEmpty()) {
            EntityPlayerMP p = (EntityPlayerMP) w.playerEntities.get(0);
            barrels(w, y, p);
            grid(w, y, p);
        }
        ValierTweaks.LOG.info("[DevClient] scene built at {} {} {}", baseX, y, baseZ);
    }

    /** Some loot in the spawner's store, and a Void list naming rotten flesh and one enchanted book (NBT matched). */
    static void loot(WorldServer w, int x, int y, int z) {
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof LootSpawnerTile)) return;
        LootSpawnerTile t = (LootSpawnerTile) te;
        t.setInventorySlotContents(0, new ItemStack(Items.leather, 37));
        t.setInventorySlotContents(1, new ItemStack(Items.beef, 64));
        t.setInventorySlotContents(2, new ItemStack(Items.beef, 21));
        t.setInventorySlotContents(3, new ItemStack(Items.experience_bottle, 12));
        t.voids.filter[0] = new ItemStack(Items.rotten_flesh);
        t.voids.filter[1] = Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(Enchantment.baneOfArthropods, 5));
        t.voids.matchNbt = true;
        t.markDirty();
    }

    /** A barrel of every tier, filled to a ninth (Basic) up to seven ninths (LuV); then two linked LV ones. */
    static void barrels(WorldServer w, int y, EntityPlayerMP p) {
        ItemStack[] fill = { new ItemStack(Blocks.cobblestone), new ItemStack(Items.iron_ingot), new ItemStack(Items.redstone),
            new ItemStack(Blocks.log, 1, 2), new ItemStack(Items.diamond), new ItemStack(Blocks.glass), new ItemStack(Items.gold_ingot) };
        for (int t = 0; t < BarrelTier.COUNT; t++) {
            TileBarrel b = barrel(w, baseX + BARRELS_X + t, y, baseZ, t);
            if (b != null) b.store()
                .insert(fill[t], b.store()
                    .capacity() * (t + 1) / 9, false);
        }
        // Linked: 640 iron ingots in one, the other joins, one ingot comes out of the second: 639 in both.
        TileBarrel a = barrel(w, baseX + BARRELS_X + 8, y, baseZ, BarrelTier.LV);
        TileBarrel c = barrel(w, baseX + BARRELS_X + 10, y, baseZ, BarrelTier.LV);
        if (a == null || c == null) return;
        a.store()
            .insert(new ItemStack(Items.iron_ingot), 640, false);
        for (TileBarrel b : new TileBarrel[] { a, c }) {
            Object m = BarrelMenu.server(p, w, b.xCoord, b.yCoord, b.zCoord);
            if (m instanceof BarrelMenu) ((BarrelMenu) m).apply(p, BarrelMenu.LINK, 0, 0, null, "Iron");
        }
        c.store()
            .extract(1, false, false);
    }

    static TileBarrel barrel(WorldServer w, int x, int y, int z, int tier) {
        w.setBlock(x, y, z, ModBlocks.barrel, tier, 3);
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileBarrel)) return null;
        ((TileBarrel) te).facing = ForgeDirection.NORTH.ordinal(); // its front toward the camera
        w.markBlockForUpdate(x, y, z);
        return (TileBarrel) te;
    }

    /** Every tier: cells (part full), plugs above them, points on top; the controller and two docks beside. */
    static void grid(WorldServer w, int y, EntityPlayerMP p) {
        int x0 = baseX + BARRELS_X, z = baseZ + GRID_Z;
        for (int t = 0; t < GridTier.COUNT; t++) {
            TileGrid cell = gridBlock(w, p, x0 + t, y, z, ModBlocks.gridCell, t);
            if (cell instanceof TileGridCell) ((TileGridCell) cell).stored = ((TileGridCell) cell).capacity() * (t + 2) / 10;
            gridBlock(w, p, x0 + t, y + 1, z, ModBlocks.gridPlug, t);
            gridBlock(w, p, x0 + t, y + 2, z, ModBlocks.gridPoint, t);
        }
        gridBlock(w, p, x0 + 8, y, z, ModBlocks.gridController, 0);
        dock(w, x0 + 10, y, z, p, satchel());
        dock(w, x0 + 11, y, z, p, flask());
    }

    static TileGrid gridBlock(WorldServer w, EntityPlayerMP p, int x, int y, int z, net.minecraft.block.Block b, int tier) {
        w.setBlock(x, y, z, b, tier, 3);
        TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileGrid)) return null;
        Object m = GridMenu.server(p, w, x, y, z);
        if (m instanceof GridMenu) ((GridMenu) m).apply(p, GridMenu.NET, 0, 0, null, "Base");
        return (TileGrid) te;
    }

    /** Docks a Satchel or Flask the way a player does (in hand, right-click), keeping what the hand held. */
    static void dock(WorldServer w, int x, int y, int z, EntityPlayerMP p, ItemStack bag) {
        w.setBlock(x, y, z, ModBlocks.satchelDock);
        int slot = p.inventory.currentItem;
        ItemStack was = p.inventory.getStackInSlot(slot);
        p.inventory.setInventorySlotContents(slot, bag);
        ModBlocks.satchelDock.onBlockActivated(w, x, y, z, p, 1, 0.5F, 0.5F, 0.5F);
        p.inventory.setInventorySlotContents(slot, was);
    }

    static void hand(EntityPlayerMP p, ItemStack s) {
        p.inventory.setInventorySlotContents(p.inventory.currentItem, s);
        p.inventoryContainer.detectAndSendChanges();
    }

    /** A LuV Satchel holding a mix (big counts, one locked slot, a keep amount, ore dictionary on ingots). */
    static ItemStack satchel() {
        ItemStack s = new ItemStack(ModBlocks.satchel, 1, SatchelTier.LUV);
        SatchelInventory inv = SatchelInventory.of(s);
        Object[][] fill = { { new ItemStack(Blocks.cobblestone), 1234567 }, { new ItemStack(Items.iron_ingot), 34567 },
            { new ItemStack(Items.redstone), 250000 }, { new ItemStack(Blocks.log, 1, 2), 3000 }, { new ItemStack(Items.diamond), 64 },
            { new ItemStack(Blocks.dirt), 999 }, { new ItemStack(Blocks.glass), 12000 }, { new ItemStack(Blocks.torch), 500 },
            { new ItemStack(Items.coal), 8800 }, { new ItemStack(Items.gold_ingot), 42 }, { new ItemStack(Blocks.sand), 77777 },
            { new ItemStack(Items.ender_pearl), 16 } };
        for (Object[] f : fill) inv.insert((ItemStack) f[0], (Integer) f[1], false, false);
        inv.keep[0] = 64;
        inv.ore[1] = true;
        inv.lock[4] = true;
        inv.selected = 0;
        inv.save();
        return s;
    }

    /** An IV Flask with water and lava. */
    static ItemStack flask() {
        ItemStack s = new ItemStack(ModBlocks.flask, 1, SatchelTier.IV);
        SatchelInventory inv = SatchelInventory.of(s);
        inv.fill(new FluidStack(FluidRegistry.WATER, 12_000_000), true);
        inv.fill(new FluidStack(FluidRegistry.LAVA, 640_000), true);
        inv.lock[1] = true;
        inv.save();
        return s;
    }

    static TilePipe pipe(WorldServer w, int x, int y, int z, net.minecraft.block.Block b, int tier) {
        w.setBlock(x, y, z, b, tier, 3);
        TileEntity te = w.getTileEntity(x, y, z);
        if (te instanceof TilePipe) ((TilePipe) te).updateConnections();
        return te instanceof TilePipe ? (TilePipe) te : null;
    }

    static void rig(WorldServer w, int x, int y, int z, FaceMode left, FaceMode right, int tier) {
        w.setBlock(x + 1, y, z, Blocks.chest);
        w.setBlock(x + 4, y, z, Blocks.chest);
        ((IInventory) w.getTileEntity(x + 1, y, z)).setInventorySlotContents(0, new ItemStack(Blocks.cobblestone, 64));
        TilePipe a = pipe(w, x + 2, y, z, com.valier.tweaks.ModBlocks.itemPipe, tier);
        TilePipe b = pipe(w, x + 3, y, z, com.valier.tweaks.ModBlocks.itemPipe, tier);
        if (a == null || b == null) return;
        a.updateConnections();
        b.updateConnections();
        FaceConfig fa = a.face(ForgeDirection.WEST.ordinal()), fb = b.face(ForgeDirection.EAST.ordinal());
        if (fa != null) fa.mode = left;
        if (fb != null) {
            fb.mode = right;
            fb.allow.filter[0] = new ItemStack(Blocks.cobblestone);
            fb.allow.count[0] = 32;
        }
        if (fa != null && left == FaceMode.EXTRACT) { // something to see in the menu's filter
            fa.allow.filter[0] = new ItemStack(Blocks.cobblestone);
            fa.allow.filter[1] = new ItemStack(net.minecraft.init.Items.iron_ingot);
            fa.allow.count[1] = 16;
            fa.allow.filter[2] = new ItemStack(net.minecraft.init.Items.diamond_pickaxe);
            fa.allow.filter[3] = new ItemStack(Blocks.log, 1, 2);
            fa.allow.count[3] = 128;
            fa.allow.oreDict = true;
            fa.deny.filter[0] = new ItemStack(Blocks.dirt);
            fa.voids.filter[0] = new ItemStack(net.minecraft.init.Items.rotten_flesh);
            fa.voids.filter[1] = new ItemStack(Blocks.cobblestone);
            fa.voids.count[1] = 1000;
            fa.spread = FaceConfig.Spread.EVEN;
            fa.tag = "001";
        }
        a.faceChanged();
        b.faceChanged();
    }
}
