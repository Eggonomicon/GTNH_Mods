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
        if (PipeTier.wireless(tier)) {
            lines.add(grey + "Tagged faces link to your team's Wireless pipes with the same tag");
            lines.add(grey + "So do tagged faces on any pipe joined to it");
        }
        if (tier == PipeTier.CREATIVE) {
            String pink = EnumChatFormatting.LIGHT_PURPLE.toString();
            lines.add(pink + "Makes endless " + (kind == PipeKind.FLUID ? "fluid" : "items") + ": right-click its centre");
            lines.add(pink + "Only ops and creative players can place, change or break it");
        }
        lines.add(grey + "Right-click where it touches a block: its menu");
    }

    /** Creative pipes: only ops and players in creative mode may place them. */
    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, net.minecraft.world.World world, int x, int y, int z,
        int side, float hx, float hy, float hz, int meta) {
        if (!world.isRemote && PipeTier.clamp(meta) == PipeTier.CREATIVE && !PipeClicks.creativeAllowed(player)) {
            PipeClicks.tell(player, EnumChatFormatting.RED, "Only ops and players in creative mode can place a creative pipe.");
            return false;
        }
        return super.placeBlockAt(stack, player, world, x, y, z, side, hx, hy, hz, meta);
    }
}
