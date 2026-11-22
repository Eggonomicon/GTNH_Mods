package com.valier.tweaks;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;

import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FilterList;
import com.valier.tweaks.pipe.PipeActionMessage;

import gregtech.api.enums.ItemList;

/**
 * Part of {@code valiertweaks selftest lootspawner} (2.2.0): the loot filter and the loot spawner's menu, in a chunk of
 * its own. Zombies holding an item that always drops (drop chance 2, as gear a mob picked up drops) give exact counts.
 * <ul>
 * <li>The Void list by item (end to end: none in the chest, counted as destroyed), by NBT (two enchanted books, only
 * the listed one destroyed), by ore dictionary (oak and acacia logs), by mod (two GregTech parts), and a wear setting
 * on its own (worn swords).</li>
 * <li>The Allow list (only flesh comes out), and Void beating Allow; XP bottles filtered too.</li>
 * <li>The menu: list picked, slots set by click, shift-click and NEI, kinds (no "existing"), NBT, damage, snapshot of
 * the store, clear; stored items the filter now destroys removed and counted; nothing changes in the store view or
 * where a claim protects it; the screen's copy matches.</li>
 * <li>Saved and loaded, and through a wrench (the item's lore says it filters).</li>
 * </ul>
 */
final class LootFilterSelfTest {

    final WorldServer world;
    final EntityPlayer player;
    final int bx = (SelfTest.CX + 1) << 4, bz = (SelfTest.CZ + 8) << 4;
    int y;

    LootFilterSelfTest(WorldServer world, EntityPlayer player) {
        this.world = world;
        this.player = player;
    }

    void run() {
        world.theChunkProviderServer.loadChunk(bx >> 4, bz >> 4);
        y = world.getHeightValue(bx + 8, bz + 8) + 2;
        clear();
        try {
            voidItems();
            matching();
            allow();
            xpBottles();
            menu();
            upgrades();
            saving();
        } finally {
            clear();
        }
    }

    // ---- end to end ---------------------------------------------------------------------------------------------

    void voidItems() {
        int[] p = { bx + 2, y, bz + 2 };
        LootSpawnerTile t = holding(p, new ItemStack(Items.iron_sword));
        IInventory chest = chestUnder(p);
        t.voids.filter[0] = new ItemStack(Items.iron_sword);
        cycle(t);
        int swords = LootSelfTest.count(chest, Items.iron_sword) + LootSelfTest.countHeld(t, Items.iron_sword);
        SelfTest.check(t.rolls == 1 && swords == 0 && t.voidedLastHour() >= 4,
            "Void list: the zombies' iron swords are destroyed as they are made (" + swords + " came out, "
                + t.voidedLastHour() + " destroyed)");

        // NBT: two spawners whose zombies hold different enchanted books; only the listed book is destroyed.
        ItemStack sharp = book(Enchantment.sharpness, 1), smite = book(Enchantment.smite, 2);
        int[] pa = { bx + 6, y, bz + 2 }, pb = { bx + 10, y, bz + 2 };
        LootSpawnerTile a = holding(pa, sharp), b = holding(pb, smite);
        for (LootSpawnerTile s : new LootSpawnerTile[] { a, b }) {
            s.voids.filter[0] = sharp.copy();
            s.voids.matchNbt = true;
        }
        cycle(a);
        cycle(b);
        int booksA = LootSelfTest.count(chestUnder(pa), Items.enchanted_book), booksB = LootSelfTest.count(chestUnder(pb), Items.enchanted_book);
        SelfTest.check(booksA == 0 && booksB == 4,
            "Void list matching NBT: Sharpness I books are destroyed, Smite II books still come out (" + booksA + " / " + booksB + ")");
        b.voids.matchNbt = false;
        SelfTest.check(!b.lets(smite) && !b.lets(sharp), "without NBT matching, the entry names every enchanted book");
    }

    void matching() {
        LootSpawnerTile t = new LootSpawnerTile();
        ItemStack oak = new ItemStack(Blocks.log, 1, 0), acacia = new ItemStack(Blocks.log2, 1, 0);
        t.voids.filter[0] = oak.copy();
        SelfTest.check(!t.lets(oak) && t.lets(acacia), "an item entry names that item only");
        t.voids.oreDict = true;
        SelfTest.check(!t.lets(acacia), "with the ore dictionary on, an oak log entry names acacia logs too (logWood)");
        t.voids.clear();
        t.voids.oreDict = false;
        t.voids.kind = FaceConfig.Kind.MODS;
        t.voids.filter[0] = ItemList.Conveyor_Module_LV.get(1);
        SelfTest.check(!t.lets(ItemList.Electric_Pump_LV.get(1)) && t.lets(new ItemStack(Items.rotten_flesh)),
            "a mod entry (a GregTech conveyor) names every GregTech item, nothing else");
        t.voids.clear();
        t.voids.kind = FaceConfig.Kind.ITEMS;
        t.voids.damage = FaceConfig.Damage.DAMAGED;
        SelfTest.check(!t.lets(LootSelfTest.worn(Items.iron_sword)) && t.lets(new ItemStack(Items.iron_sword))
            && t.lets(new ItemStack(Items.rotten_flesh)), "an empty Void list set to \"only damaged\" destroys worn gear only");
    }

    void allow() {
        int[] p = { bx + 2, y, bz + 6 };
        LootSpawnerTile t = holding(p, new ItemStack(Items.iron_sword));
        t.allow.filter[0] = new ItemStack(Items.rotten_flesh);
        cycle(t);
        IInventory chest = chestUnder(p);
        int swords = LootSelfTest.count(chest, Items.iron_sword);
        SelfTest.check(swords == 0 && t.voidedLastHour() >= 4, "Allow list (rotten flesh): the swords are destroyed (" + swords
            + " came out, " + t.voidedLastHour() + " destroyed)");
        SelfTest.check(t.lets(new ItemStack(Items.rotten_flesh)) && !t.lets(new ItemStack(Items.bone)),
            "only what the Allow list names comes out");
        t.voids.filter[0] = new ItemStack(Items.rotten_flesh);
        SelfTest.check(!t.lets(new ItemStack(Items.rotten_flesh)), "the Void list wins over the Allow list");
    }

    void xpBottles() {
        int[] p = { bx + 6, y, bz + 6 };
        LootSpawnerTile t = holding(p, new ItemStack(Items.stick));
        t.xpBottles = true;
        t.voids.filter[0] = new ItemStack(Items.experience_bottle);
        cycle(t);
        int bottles = LootSelfTest.count(chestUnder(p), Items.experience_bottle);
        SelfTest.check(bottles == 0 && t.voidedLastHour() >= 1, "XP bottles are filtered too (" + bottles + " came out, "
            + t.voidedLastHour() + " destroyed)");
    }

    // ---- the menu -----------------------------------------------------------------------------------------------

    void menu() {
        int[] p = { bx + 10, y, bz + 6 };
        LootSpawnerTile t = holding(p, new ItemStack(Items.stick));
        Object opened = LootMenu.server(player, world, p[0], p[1], p[2]);
        SelfTest.check(opened instanceof LootMenu, "the menu opens for a player who may change the spawner (" + opened + ")");
        if (!(opened instanceof LootMenu)) return;
        LootMenu m = (LootMenu) opened;
        t.addToBuffer(new ItemStack(Items.iron_sword));
        t.addToBuffer(new ItemStack(Items.iron_sword));
        t.addToBuffer(new ItemStack(Items.rotten_flesh, 5));
        m.apply(player, PipeActionMessage.SET_SLOT, 0, 0, new ItemStack(Items.iron_sword), null);
        SelfTest.check(m.store && t.voids.entries() == 0, "in the store view, list buttons change nothing");
        m.apply(player, PipeActionMessage.LIST, FaceConfig.VOID, 0, null, null);
        m.apply(player, PipeActionMessage.SET_SLOT, 0, 0, new ItemStack(Items.iron_sword), null);
        SelfTest.check(!m.store && t.voids.filter[0] != null && t.voids.filter[0].getItem() == Items.iron_sword
            && LootSelfTest.countHeld(t, Items.iron_sword) == 0 && LootSelfTest.countHeld(t, Items.rotten_flesh) == 5
            && m.note.contains("destroyed 2"), "a sword dragged into the Void list: the 2 swords it kept are destroyed, the "
                + "flesh kept, and the menu says so (" + m.note + ")");
        player.inventory.setItemStack(new ItemStack(Items.bone, 3));
        m.slotClick(1, 0, 0, player);
        SelfTest.check(t.voids.filter[1] != null && t.voids.filter[1].getItem() == Items.bone && t.voids.filter[1].stackSize == 1
            && player.inventory.getItemStack() != null && player.inventory.getItemStack().stackSize == 3,
            "clicking a ghost slot with bones sets it, and the bones stay on the cursor");
        player.inventory.setItemStack(null);
        m.slotClick(1, 1, 0, player);
        SelfTest.check(t.voids.filter[1] == null, "right-clicking it clears it");
        player.inventory.setInventorySlotContents(9, new ItemStack(Items.arrow, 7));
        m.transferStackInSlot(player, LootMenu.INV_FROM);
        SelfTest.check(t.voids.filter[1] != null && t.voids.filter[1].getItem() == Items.arrow
            && player.inventory.getStackInSlot(9) != null && player.inventory.getStackInSlot(9).stackSize == 7,
            "shift-clicking arrows in the inventory adds them to the list and keeps them");
        player.inventory.setInventorySlotContents(9, null);
        m.apply(player, PipeActionMessage.LIST, FaceConfig.ALLOW, 0, null, null);
        m.apply(player, PipeActionMessage.SNAPSHOT, 0, 0, null, null);
        SelfTest.check(t.allow.entries() == 1 && t.allow.filter[0].getItem() == Items.rotten_flesh,
            "snapshot: the Allow list becomes what the store holds (rotten flesh)");
        m.apply(player, PipeActionMessage.LIST, FaceConfig.VOID, 0, null, null);
        m.apply(player, PipeActionMessage.KIND, FaceConfig.Kind.EXISTING.ordinal(), 0, null, null);
        boolean noExisting = t.voids.kind == FaceConfig.Kind.ITEMS;
        m.apply(player, PipeActionMessage.KIND, FaceConfig.Kind.MODS.ordinal(), 0, null, null);
        boolean mods = t.voids.kind == FaceConfig.Kind.MODS;
        m.apply(player, PipeActionMessage.KIND, FaceConfig.Kind.ITEMS.ordinal(), 0, null, null);
        m.apply(player, PipeActionMessage.NBT, 0, 0, null, null);
        m.apply(player, PipeActionMessage.DAMAGE, 1, 0, null, null);
        SelfTest.check(noExisting && mods && t.voids.kind == FaceConfig.Kind.ITEMS && t.voids.matchNbt
            && t.voids.damage == FaceConfig.Damage.ANY, "kinds (items, mods; never \"existing\"), NBT and damage buttons work");
        // The screen's copy.
        LootMenu screen = new LootMenu(player.inventory, null, new InventoryBasic("x", false, LootSpawnerTile.SLOTS), world, p[0],
            p[1], p[2]);
        screen.receive(m.state());
        SelfTest.check(screen.listAt(FaceConfig.VOID)
            .entries() == 2 && screen.listAt(FaceConfig.ALLOW)
                .entries() == 1
            && !screen.store && screen.list == FaceConfig.VOID && screen.line3.equals("No upgrades")
            && screen.voided >= 2, "the screen gets both lists, the view and the spawner's state (" + screen.line1 + "; "
                + screen.line2 + ")");
        m.apply(player, PipeActionMessage.CLEAR, 0, 0, null, null);
        SelfTest.check(t.voids.entries() == 0 && t.allow.entries() == 1, "clear empties the list shown, not the other");
        // A claim.
        SelfTest.DenyBreaks guard = new SelfTest.DenyBreaks();
        MinecraftForge.EVENT_BUS.register(guard);
        Object refused;
        try {
            refused = LootMenu.server(player, world, p[0], p[1], p[2]);
            m.apply(player, PipeActionMessage.SET_SLOT, 5, 0, new ItemStack(Items.bone), null);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(guard);
        }
        SelfTest.check(refused == null && t.voids.filter[5] == null, "where a claim protects it, the menu doesn't open and "
            + "changes are refused");
    }

    /**
     * The menu's upgrade buttons (2.3.1): XP bottles and Damaged Yes/No, paid from anywhere in the inventory (not the
     * hand) and given back; Looting + and -; refused without the item; the screen gets the levels.
     */
    void upgrades() {
        int[] p = { bx + 10, y, bz + 10 };
        LootSpawnerTile t = holding(p, new ItemStack(Items.stick));
        Object opened = LootMenu.server(player, world, p[0], p[1], p[2]);
        net.minecraft.item.Item gold = LootSpawners.itemOf(TweaksConfig.lootSpawnerXpItem),
            emerald = LootSpawners.itemOf(TweaksConfig.lootSpawnerFilterItem),
            lapis = LootSpawners.itemOf(TweaksConfig.lootSpawnerLootingItem);
        if (!(opened instanceof LootMenu) || gold == null || emerald == null || lapis == null) {
            SelfTest.check(opened instanceof LootMenu, "the menu opens for its upgrade buttons");
            SelfTest.log("upgrade buttons not checked: an upgrade item set in the config doesn't exist");
            return;
        }
        LootMenu m = (LootMenu) opened;
        boolean creative = player.capabilities.isCreativeMode;
        player.capabilities.isCreativeMode = false;
        try {
            int xpCost = TweaksConfig.lootSpawnerXpItemCount, dmgCost = TweaksConfig.lootSpawnerFilterItemCount;
            player.inventory.clearInventory(null, -1);
            player.inventory.currentItem = 0;
            player.inventory.setInventorySlotContents(20, new ItemStack(gold, xpCost + 1, Math.max(0, LootSpawners.metaOf(
                TweaksConfig.lootSpawnerXpItem))));
            m.apply(player, LootMenu.XP, 0, 0, null, null);
            boolean on = t.xpBottles && have(gold) == 1 && t.xpPaid != null;
            m.apply(player, LootMenu.XP, 0, 0, null, null);
            SelfTest.check(on && !t.xpBottles && have(gold) == xpCost + 1 && t.xpPaid == null, "the menu's XP bottles button: "
                + "Yes takes its item from anywhere in the inventory, No gives it back (" + have(gold) + " there)");
            player.inventory.setInventorySlotContents(31, new ItemStack(emerald, dmgCost, Math.max(0, LootSpawners.metaOf(
                TweaksConfig.lootSpawnerFilterItem))));
            t.addToBuffer(new ItemStack(Items.iron_sword, 1, 40)); // a worn sword it was keeping
            m.apply(player, LootMenu.DAMAGED, 0, 0, null, null);
            on = t.noDamaged && have(emerald) == 0 && LootSelfTest.countHeld(t, Items.iron_sword) == 0;
            m.apply(player, LootMenu.DAMAGED, 0, 0, null, null);
            SelfTest.check(on && !t.noDamaged && have(emerald) == dmgCost, "the menu's Damaged button: No takes its item and "
                + "throws away the worn sword it kept, Yes gives the item back");
            player.inventory.clearInventory(null, -1);
            m.apply(player, LootMenu.XP, 0, 0, null, null);
            m.apply(player, LootMenu.DAMAGED, 0, 0, null, null);
            SelfTest.check(!t.xpBottles && !t.noDamaged, "without the item in the inventory, neither switches on");
            player.inventory.setInventorySlotContents(9, new ItemStack(lapis, 4, Math.max(0, LootSpawners.metaOf(
                TweaksConfig.lootSpawnerLootingItem))));
            for (int i = 0; i < 3; i++) m.apply(player, LootMenu.LOOTING, 1, 0, null, null);
            int in = t.looting.blocks, level = t.looting.level(), left = have(lapis);
            m.apply(player, LootMenu.LOOTING, -1, 0, null, null);
            SelfTest.check(in == 3 && level == LootSpawners.lootingLevel(3) && left == 1 && t.looting.blocks == 2 && t.looting.paid == 2
                && have(lapis) == 2, "Looting + puts three in from the inventory (Looting " + level + "), - takes one back out ("
                    + t.looting.blocks + " in it, " + have(lapis) + " back in the inventory)");
            LootMenu screen = new LootMenu(player.inventory, null, new InventoryBasic("x", false, LootSpawnerTile.SLOTS), world, p[0],
                p[1], p[2]);
            screen.receive(m.state());
            SelfTest.check(screen.lootingIn == 2 && screen.looting == t.looting.level() && !screen.xp && !screen.noDamaged
                && screen.lootingItem != null && screen.lootingItem.getItem() == lapis && screen.xpItem != null
                && screen.lootingTiers.length == TweaksConfig.lootSpawnerLootingBlocks.length,
                "the screen gets the upgrades (Looting " + screen.looting + ", " + screen.lootingIn + " in it, XP " + screen.xp
                    + ", damaged " + (screen.noDamaged ? "thrown away" : "out") + ") and what they cost");
            m.apply(player, LootMenu.LOOTING, -1, 0, null, null);
            m.apply(player, LootMenu.LOOTING, -1, 0, null, null);
            SelfTest.check(t.looting.blocks == 0 && t.looting.item == null && have(lapis) == 4, "taking them all back out leaves "
                + "it with none, and all four came back");
        } finally {
            player.capabilities.isCreativeMode = creative;
            player.inventory.clearInventory(null, -1);
        }
    }

    int have(net.minecraft.item.Item item) {
        int n = 0;
        for (ItemStack s : player.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
        return n;
    }

    void saving() {
        int[] p = { bx + 2, y, bz + 10 };
        LootSpawnerTile t = holding(p, new ItemStack(Items.stick));
        t.voids.filter[3] = book(Enchantment.sharpness, 1);
        t.voids.matchNbt = true;
        t.allow.filter[0] = new ItemStack(Items.rotten_flesh);
        t.allow.kind = FaceConfig.Kind.MODS;
        NBTTagCompound tag = new NBTTagCompound();
        t.writeToNBT(tag);
        LootSpawnerTile copy = new LootSpawnerTile();
        copy.readFromNBT(tag);
        SelfTest.check(same(copy.voids, t.voids) && same(copy.allow, t.allow), "saved and loaded, it keeps both lists");
        ItemStack item = LootSpawners.toItem(t);
        NBTTagCompound saved = LootSpawners.savedSpawner(item);
        LootSpawnerTile placed = new LootSpawnerTile();
        NBTTagCompound s = (NBTTagCompound) saved.copy();
        s.setInteger("x", p[0]);
        s.setInteger("y", p[1]);
        s.setInteger("z", p[2]);
        placed.readFromNBT(s);
        String lore = item.getTagCompound()
            .getCompoundTag("display")
            .toString();
        SelfTest.check(same(placed.voids, t.voids) && same(placed.allow, t.allow) && lore.contains("Filters its loot"),
            "picked up with a wrench and placed again, it keeps both lists, and the item says it filters");
    }

    static boolean same(FilterList a, FilterList b) {
        NBTTagCompound x = new NBTTagCompound(), y = new NBTTagCompound();
        a.write(x);
        b.write(y);
        return x.equals(y) && a.entries() > 0;
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    static ItemStack book(Enchantment e, int level) {
        return Items.enchanted_book.getEnchantedItemStack(new EnchantmentData(e, level));
    }

    /** A zombie loot spawner over a chest whose zombies hold {@code held}, which always drops. */
    LootSpawnerTile holding(int[] p, ItemStack held) {
        world.setBlock(p[0], p[1], p[2], Blocks.mob_spawner);
        TileEntityMobSpawner v = (TileEntityMobSpawner) world.getTileEntity(p[0], p[1], p[2]);
        NBTTagCompound props = new NBTTagCompound();
        NBTTagList equipment = new NBTTagList();
        NBTTagList chances = new NBTTagList();
        equipment.appendTag(held.writeToNBT(new NBTTagCompound()));
        chances.appendTag(new NBTTagFloat(2.0F));
        for (int i = 0; i < 4; i++) {
            equipment.appendTag(new NBTTagCompound());
            chances.appendTag(new NBTTagFloat(0.0F));
        }
        props.setTag("Equipment", equipment);
        props.setTag("DropChances", chances);
        MobSpawnerBaseLogic logic = v.func_145881_a();
        logic.setRandomEntity(logic.new WeightedRandomMinecart(props, "Zombie"));
        world.setBlock(p[0], p[1] - 1, p[2], Blocks.chest);
        LootSpawnerTile.mark(v);
        return LootSpawners.swap(world, v);
    }

    IInventory chestUnder(int[] p) {
        return (IInventory) world.getTileEntity(p[0], p[1] - 1, p[2]);
    }

    static void cycle(LootSpawnerTile t) {
        t.timer = 0;
        LootSpawners.cycle(t);
    }

    void clear() {
        for (int x = bx; x < bx + 16; x++) for (int z = bz; z < bz + 16; z++) for (int yy = y - 1; yy <= y + 1; yy++) {
            Object te = world.getTileEntity(x, yy, z);
            if (te instanceof LootSpawnerTile) for (int i = 0; i < LootSpawnerTile.SLOTS; i++) ((LootSpawnerTile) te).items[i] = null;
            else if (te instanceof IInventory) LootSelfTest.clearInv((IInventory) te);
            if (!world.isAirBlock(x, yy, z)) world.setBlockToAir(x, yy, z);
        }
    }
}
