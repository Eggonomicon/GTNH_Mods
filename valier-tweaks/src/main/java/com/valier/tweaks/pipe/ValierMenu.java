package com.valier.tweaks.pipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A Valier menu (a {@link net.minecraft.inventory.Container}) that takes {@link PipeActionMessage}s from its player and
 * gets its state from the server as one compound ({@link PipeFaceMessage}): the pipe face menu, the loot spawner menu.
 */
public interface ValierMenu {

    /** One action from the menu (server side; every value checked). */
    void apply(EntityPlayer player, int action, int a, int b, ItemStack stack, String text);

    /** The client's copy of the state, from the server. */
    void receive(NBTTagCompound t);
}
