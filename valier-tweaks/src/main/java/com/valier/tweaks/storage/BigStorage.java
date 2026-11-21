package com.valier.tweaks.storage;

import net.minecraft.item.ItemStack;

/**
 * An inventory holding more of one item than a stack (a barrel). Valier pipes use this instead of its two slots, so
 * one transfer can move the pipe's whole amount (an Ultimate pipe's 320) rather than one stack at a time.
 */
public interface BigStorage {

    /** The slot automation takes from (slot 0 takes what is put in). */
    int OUT_SLOT = 1;

    /** How many of s (up to max) it takes now. */
    int bigRoom(ItemStack s, int max);

    /** Puts up to max of s in (s itself is not changed); returns how many it took. */
    int bigInsert(ItemStack s, int max);

    /** The item it holds, sized to what automation may take (capped at an int), or null. */
    ItemStack bigPeek();

    /** Takes up to max out for automation (keeping the keep amount); null if none. */
    ItemStack bigExtract(int max);
}
