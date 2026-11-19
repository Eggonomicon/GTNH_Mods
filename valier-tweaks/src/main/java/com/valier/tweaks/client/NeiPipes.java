package com.valier.tweaks.client;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;

/**
 * NEI and the pipe face menu (only loaded with NEI): an item dragged from NEI's panel onto a filter slot sets it, so a
 * player doesn't need to own the item; NEI's panel keeps clear of the menu's face tabs.
 */
public final class NeiPipes implements INEIGuiHandler {

    public static void register() {
        API.registerNEIGuiHandler(new NeiPipes());
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mx, int my, ItemStack stack, int button) {
        return gui instanceof GuiPipeFace && ((GuiPipeFace) gui).dropped(mx, my, stack);
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int w, int h) {
        return gui instanceof GuiPipeFace && ((GuiPipeFace) gui).overTabs(x, y, w, h);
    }
}
