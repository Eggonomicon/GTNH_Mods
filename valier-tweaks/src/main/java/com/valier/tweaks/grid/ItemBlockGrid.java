package com.valier.tweaks.grid;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

/** A Grid block as an item (metadata: its tier); picked up or broken, it keeps its settings, network and energy. */
public class ItemBlockGrid extends ItemBlock {

    public ItemBlockGrid(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    BlockGrid grid() {
        return (BlockGrid) field_150939_a;
    }

    @Override
    public int getMetadata(int damage) {
        return grid().kind == TileGrid.CONTROLLER ? 0 : GridTier.clamp(damage);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return grid().kind == TileGrid.CONTROLLER ? super.getUnlocalizedName() : super.getUnlocalizedName() + "." + GridTier.clamp(stack.getItemDamage());
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(BlockGrid.ITEM_TAG) ? 1 : 64;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int t = GridTier.clamp(stack.getItemDamage());
        int kind = grid().kind;
        String v = String.format("%,d", GridTier.volts(t));
        switch (kind) {
            case TileGrid.PLUG:
                list.add(EnumChatFormatting.GRAY + "Takes EU up to " + v + " V (" + GridTier.amps() + " A), IC2 EU and RF");
                list.add(EnumChatFormatting.GRAY + "into its Grid network.");
                break;
            case TileGrid.POINT:
                list.add(EnumChatFormatting.GRAY + "Sends EU (up to " + v + " V, " + GridTier.amps() + " A) and RF out of its");
                list.add(EnumChatFormatting.GRAY + "network; GregTech machines at their own voltage.");
                break;
            case TileGrid.CELL:
                list.add(EnumChatFormatting.GRAY + "Stores " + String.format("%,d", GridTier.cellEu(t)) + " EU for its network.");
                break;
            default:
                list.add(EnumChatFormatting.GRAY + "Charges its team's players' items, anywhere.");
        }
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(BlockGrid.ITEM_TAG) : null;
        if (tag != null && !tag.getString("Net")
            .isEmpty()) list.add(EnumChatFormatting.LIGHT_PURPLE + "Network \"" + tag.getString("Net") + "\"");
        if (tag != null && tag.getLong("Stored") > 0) list.add(EnumChatFormatting.YELLOW + String.format("%,d EU", tag.getLong("Stored")));
        list.add(EnumChatFormatting.DARK_GRAY + "Right-click it for its menu.");
    }
}
