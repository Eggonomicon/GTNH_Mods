package com.valier.client;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import mcp.mobius.waila.utils.ModIdentification;

/**
 * A Valier XP Crafter item's tooltip ends with the mod it comes from, which WAILA adds ("GregTech": the crafter is a
 * GregTech tank item). This makes that line say Valier Tweaks. WAILA adds it through NEI's tooltip handlers when NEI is
 * there ({@link NeiCompat}), else in a tooltip event (this class, run after WAILA's).
 */
public final class ModNameLine {

    /** WAILA's mod name colour and style. */
    static final String FORMAT = "§9§o";

    /** The tooltip of a crafter item with WAILA's mod name line made Valier Tweaks; other items' unchanged. */
    public static List<String> fix(ItemStack stack, List<String> tip) {
        if (tip == null || !isCrafter(stack)) return tip;
        String waila;
        try {
            waila = FORMAT + ModIdentification.nameFromStack(stack);
        } catch (Throwable e) {
            return tip; // no WAILA: no such line
        }
        for (int i = tip.size() - 1; i >= 0; i--) {
            if (waila.equals(tip.get(i))) {
                tip.set(i, FORMAT + StatCollector.translateToLocal("valierclient.xpcrafter.mod"));
                break;
            }
        }
        return tip;
    }

    /** A Valier XP Crafter item: it carries the compound Valier Tweaks gives it. */
    static boolean isCrafter(ItemStack s) {
        return s != null && s.hasTagCompound()
            && s.getTagCompound()
                .hasKey(XpCrafterHud.TAG, 10);
    }

    /** Without NEI, WAILA adds the line in this event; this runs after it. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipEvent event) {
        fix(event.itemStack, event.toolTip);
    }
}
