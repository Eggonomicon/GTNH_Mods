package com.valier.client;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.oredict.ShapedOreRecipe;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

/**
 * The Valier XP Crafter's crafting recipe, so NEI shows it. Valier Tweaks makes the crafter on the server (its own copy
 * of this recipe decides; clients don't get recipes from servers): this copy is only registered on players' computers,
 * where it shows the recipe and what the crafting grid will hold.
 *
 * <pre>
 * ULV tank  LV pump         LV conveyor
 * ULV tank  crafting table  LV circuit
 * ULV tank  LV pump         LV robot arm
 * </pre>
 */
public final class XpCrafterRecipe {

    /** GT++'s Ultra Low Voltage Fluid Tank (Valier Tweaks' default tank). */
    static final int TANK_META = 817;

    private XpCrafterRecipe() {}

    /** The recipe, or null without GregTech's machine block. */
    public static IRecipe make() {
        Item machines = GameRegistry.findItem("gregtech", "gt.blockmachines");
        if (machines == null) return null;
        ItemStack tank = new ItemStack(machines, 1, TANK_META);
        return new ShapedOreRecipe(crafter(tank), "TPC", "TWc", "TPA", 'T', tank, 'P', ItemList.Electric_Pump_LV.get(1), 'C',
            ItemList.Conveyor_Module_LV.get(1), 'A', ItemList.Robot_Arm_LV.get(1), 'W', new ItemStack(Blocks.crafting_table), 'c',
            OrePrefixes.circuit.get(Materials.LV)
                .toString());
    }

    /** The tank as a new Valier XP Crafter item looks (the server's crafted one has the same name and tooltip). */
    static ItemStack crafter(ItemStack tank) {
        ItemStack s = tank.copy();
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound info = new NBTTagCompound();
        info.setBoolean("Full", false);
        info.setInteger("Bank", 0);
        info.setLong("Made", 0);
        tag.setTag(XpCrafterHud.TAG, info);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", EnumChatFormatting.RESET + "Valier XP Crafter");
        NBTTagList lore = new NBTTagList();
        for (String line : new String[] { "Makes: nothing yet", "Place it, then right-click it", "to choose what it makes",
            "Fill it with liquid XP; what it makes", "goes into chests and pipes touching it,", "or waits in its store" })
            lore.appendTag(new NBTTagString(EnumChatFormatting.GRAY + line));
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        s.setTagCompound(tag);
        return s;
    }
}
