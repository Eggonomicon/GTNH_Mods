package com.valier.tweaks;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * The Valier XP Crafter item: what the recipe makes and a broken crafter drops ({@link XpCrafters#toItem}), knowing
 * what it made, the emerald block, the XP it was holding and its tank's liquid XP. Placing it makes that crafter.
 */
public class ItemBlockXpCrafter extends ItemBlock {

    public ItemBlockXpCrafter(Block block) {
        super(block);
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hitX,
        float hitY, float hitZ, int meta) {
        if (!super.placeBlockAt(stack, player, world, x, y, z, side, hitX, hitY, hitZ, meta)) return false;
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileXpCrafter) XpCrafters.placed(world, (TileXpCrafter) te, stack, player);
        }
        return true;
    }
}
