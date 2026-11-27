package com.valier.tweaks.storage;

import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

/**
 * Storage holding many kinds of item in big counts (a Valier Barrel Controller's network, 2.4.0). Valier pipes use
 * this instead of its slots (as {@link BigStorage} for one barrel): a Request or a filtered Pull asks for exactly the
 * item it wants, answered from the controller's item lookup, and every amount is a whole count, not a stack.
 * <p>
 * Amounts in the big stacks are what automation may take (keep amounts left out), capped at an int. Takes and inserts
 * are always checked against the barrels, so an amount that changed since it was looked at only means less moves.
 */
public interface ManyStorage {

    /** Everything automation may take, one big stack per kind: a snapshot made at most once a tick. */
    List<ItemStack> manyPeek();

    /**
     * Up to max kinds from {@link #manyPeek()} that pass the test, starting where the last call stopped, so a pipe
     * pulling everything looks at a few kinds a transfer and gets round to all of them.
     */
    List<ItemStack> manyPeekSome(int max, Predicate<ItemStack> test);

    /**
     * Just that item, from the lookup (fresh): exactly it (item, damage, NBT), or with {@code anyNbt} every NBT variant
     * of the item and damage, one big stack per variant. A kind held but all kept (or a locked empty barrel) comes back
     * with size 0.
     */
    List<ItemStack> manyPeek(ItemStack like, boolean anyNbt);

    /** Takes up to n of exactly s out for automation (keep amounts are left); null when none can be. */
    ItemStack manyExtract(ItemStack s, int n);

    /** How many of s (up to max) it takes now. */
    int manyRoom(ItemStack s, int max);

    /** Puts up to max of s in (s itself is not changed); returns how many it took. */
    int manyInsert(ItemStack s, int max);
}
