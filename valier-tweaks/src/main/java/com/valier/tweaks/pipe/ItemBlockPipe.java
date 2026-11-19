package com.valier.tweaks.pipe;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pipe items: damage = tier. The tooltip says how much one transfer moves and how often. */
public class ItemBlockPipe extends ItemBlock {

    public ItemBlockPipe(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return PipeTier.clamp(damage);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + PipeTier.clamp(stack.getItemDamage());
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        PipeKind kind = ((BlockPipe) field_150939_a).kind;
        int tier = PipeTier.clamp(stack.getItemDamage());
        String grey = EnumChatFormatting.GRAY.toString();
        lines.add(grey + "Up to " + PipeTier.max(kind, tier) + (kind == PipeKind.FLUID ? " mB" : " items") + " every "
            + PipeTier.interval(tier) + " ticks per Extract/Request face");
        lines.add(grey + "Instant: nothing travels along the pipe");
        if (tier == PipeTier.WIRELESS) lines.add(grey + "Tagged faces link to your team's Wireless pipes with the same tag");
        lines.add(grey + "Right-click where it touches a block: its menu");
    }
}
