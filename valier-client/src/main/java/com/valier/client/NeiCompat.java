package com.valier.client;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.API;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * NEI, on players' computers only (it uses client classes; never loaded on a server):
 * <ul>
 * <li>a Valier XP Crafter is added to NEI's item list (next to the ULV fluid tank), so searching "Valier" or "crafter"
 * finds it and R on it shows its recipe ({@link XpCrafterRecipe});</li>
 * <li>its tooltip's mod name line, which WAILA adds through NEI's tooltip handlers, says Valier Tweaks
 * ({@link ModNameLine}). This handler is added after WAILA's (this mod's postInit runs after WAILA's), so it sees that
 * line.</li>
 * </ul>
 */
final class NeiCompat {

    private NeiCompat() {}

    static void register() {
        GuiContainerManager.addTooltipHandler(new ModName());
        Item tank = GameRegistry.findItem("gregtech", "gt.blockmachines");
        if (tank != null) API.addItemVariant(tank, XpCrafterRecipe.crafter(new ItemStack(tank, 1, XpCrafterRecipe.TANK_META)));
    }

    static final class ModName implements IContainerTooltipHandler {

        @Override
        public List<String> handleItemTooltip(GuiContainer gui, ItemStack stack, int x, int y, List<String> tip) {
            return ModNameLine.fix(stack, tip);
        }
    }
}
