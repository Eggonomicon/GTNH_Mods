package com.valier.tweaks.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.valier.tweaks.grid.BlockGrid;
import com.valier.tweaks.grid.TileGrid;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Draws Grid blocks into the chunk's mesh (no per-frame cost): the block, and over it a ring of light in its network's
 * colour (none when it is on no network). It reads only the colour the server sends ({@link TileGrid#shownColour}); a
 * change redraws the chunk.
 */
public class RenderGrid implements ISimpleBlockRenderingHandler {

    final int id;

    public RenderGrid(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks rb) {
        rb.renderStandardBlock(block, x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        int c = te instanceof TileGrid ? ((TileGrid) te).shownColour : -1;
        if (c < 0 || BlockGrid.light == null) return true;
        rb.setOverrideBlockTexture(BlockGrid.light);
        rb.renderStandardBlockWithColorMultiplier(block, x, y, z, (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
        rb.clearOverrideBlockTexture();
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks rb) {
        IIcon icon = block.getIcon(0, meta);
        Tessellator t = Tessellator.instance;
        rb.setRenderBounds(0, 0, 0, 1, 1, 1);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        t.startDrawingQuads();
        t.setNormal(0, -1, 0);
        rb.renderFaceYNeg(block, 0, 0, 0, icon);
        t.setNormal(0, 1, 0);
        rb.renderFaceYPos(block, 0, 0, 0, icon);
        t.setNormal(0, 0, -1);
        rb.renderFaceZNeg(block, 0, 0, 0, icon);
        t.setNormal(0, 0, 1);
        rb.renderFaceZPos(block, 0, 0, 0, icon);
        t.setNormal(-1, 0, 0);
        rb.renderFaceXNeg(block, 0, 0, 0, icon);
        t.setNormal(1, 0, 0);
        rb.renderFaceXPos(block, 0, 0, 0, icon);
        t.draw();
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return id;
    }
}
