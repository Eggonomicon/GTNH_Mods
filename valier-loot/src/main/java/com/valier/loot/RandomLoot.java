package com.valier.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraft.world.World;
import net.minecraftforge.common.ChestGenHooks;

/**
 * Each player's own random roll for a loot chest, from a weighted Forge chest-loot table (ChestGenHooks category):
 * LootGames' reward tiers chest1..chest4, vanilla's dungeonChest, any TooMuchLoot table, or a custom pool from the
 * config. Which table a chest uses depends on its dimension and distance from spawn (LootConfig's pool rules).
 */
public final class RandomLoot {

    private RandomLoot() {}

    /** The pool rule for a chest at x, z in this world, or null for none. */
    public static LootConfig.PoolRule ruleFor(World world, int x, int z) {
        ChunkCoordinates spawn = world.getSpawnPoint();
        double dx = x - spawn.posX, dz = z - spawn.posZ;
        return LootConfig.poolFor(world.provider.dimensionId, Math.sqrt(dx * dx + dz * dz));
    }

    /** Adds a roll from the chest's pool into random empty slots of the player's copy. */
    static void addRoll(World world, int x, int z, ItemStack[] copy, Random rand) {
        LootConfig.PoolRule rule = ruleFor(world, x, z);
        if (rule == null) return;
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < copy.length; i++) if (copy[i] == null) free.add(i);
        Collections.shuffle(free, rand);
        int next = 0;
        for (ItemStack stack : roll(rule, rand)) {
            if (next >= free.size()) break; // copy full: the rest of the roll is lost, as in a full vanilla chest
            copy[free.get(next++)] = stack;
        }
    }

    /** One roll of a pool: its number of items, each a weighted pick of the table. */
    public static List<ItemStack> roll(LootConfig.PoolRule rule, Random rand) {
        List<ItemStack> out = new ArrayList<>();
        WeightedRandomChestContent[] items = ChestGenHooks.getItems(rule.category, rand);
        if (items == null || items.length == 0) return out;
        int count = rule.minItems >= 0 ? rule.minItems + rand.nextInt(rule.maxItems - rule.minItems + 1)
            : ChestGenHooks.getCount(rule.category, rand);
        // A custom pool has no count of its own (Forge makes it 0); then roll a few items anyway.
        if (count <= 0 && rule.minItems < 0) count = 3;
        // One pick at a time into a fresh inventory: vanilla fills random slots and can overwrite earlier picks, and
        // going through generateChestContents keeps loot entries that customise what they generate (enchanted books).
        InventoryBasic scratch = new InventoryBasic("", false, LootData.SIZE);
        for (int i = 0; i < count; i++) {
            WeightedRandomChestContent.generateChestContents(rand, items, scratch, 1);
            for (int slot = 0; slot < scratch.getSizeInventory(); slot++) {
                ItemStack stack = scratch.getStackInSlotOnClosing(slot);
                if (stack != null && stack.stackSize > 0) out.add(stack);
            }
        }
        return out;
    }
}
