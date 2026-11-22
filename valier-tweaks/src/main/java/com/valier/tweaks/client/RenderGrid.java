package com.valier.tweaks.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.valier.tweaks.grid.BlockGrid;
import com.valier.tweaks.grid.TileGrid;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Draws Grid blocks into the chunk's mesh (no per-frame cost): the block, and over it a ring of light in its network's
 * colour (none when it is on no network). It reads only the colour the server sends ({@link TileGrid#shownColour}); a
 * change redraws the chunk. Plugs and points are small nodes, as Flux Networks' are: their whole face drawn on a core
 * half a block wide, and an arm with a plate out to each energy block beside them (a neighbour changing redraws it).
 */
public class RenderGrid implements ISimpleBlockRenderingHandler {

    final int id;

    public RenderGrid(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks rb) {
        TileEntity te = world.getTileEntity(x, y, z);
        int c = te instanceof TileGrid ? ((TileGrid) te).shownColour : -1;
        if (block instanceof BlockGrid && ((BlockGrid) block).node()) return node(world, x, y, z, (BlockGrid) block, c, rb);
        rb.renderStandardBlock(block, x, y, z);
        if (c < 0 || BlockGrid.light == null) return true;
        rb.setOverrideBlockTexture(BlockGrid.light);
        rb.renderStandardBlockWithColorMultiplier(block, x, y, z, (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
        rb.clearOverrideBlockTexture();
        return true;
    }

    boolean node(IBlockAccess world, int x, int y, int z, BlockGrid block, int c, RenderBlocks rb) {
        int meta = world.getBlockMetadata(x, y, z), arms = block.arms(world, x, y, z);
        boolean all = rb.renderAllFaces;
        rb.renderAllFaces = true; // its small boxes never hide behind a neighbour's face
        float lo = BlockGrid.CORE, hi = 1 - lo;
        rb.setRenderBounds(lo, lo, lo, hi, hi, hi);
        rb.setOverrideBlockTexture(new Whole(block.getIcon(0, meta), lo, hi));
        rb.renderStandardBlock(block, x, y, z);
        if (c >= 0 && BlockGrid.light != null) {
            rb.setOverrideBlockTexture(new Whole(BlockGrid.light, lo, hi));
            rb.renderStandardBlockWithColorMultiplier(block, x, y, z, (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
        }
        rb.setOverrideBlockTexture(block.arm(meta));
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if ((arms & 1 << d.ordinal()) == 0) continue;
            for (boolean plate : new boolean[] { false, true }) {
                float[] b = BlockGrid.armBox(d, plate);
                rb.setRenderBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
                rb.renderStandardBlock(block, x, y, z);
            }
        }
        rb.clearOverrideBlockTexture();
        rb.renderAllFaces = all;
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks rb) {
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        if (block instanceof BlockGrid && ((BlockGrid) block).node()) { // the core, with arms out both sides
            BlockGrid g = (BlockGrid) block;
            float lo = BlockGrid.CORE, hi = 1 - lo;
            rb.setRenderBounds(lo, lo, lo, hi, hi, hi);
            cube(block, rb, new Whole(g.getIcon(0, meta), lo, hi));
            for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.WEST, ForgeDirection.EAST }) {
                for (boolean plate : new boolean[] { false, true }) {
                    float[] b = BlockGrid.armBox(d, plate);
                    rb.setRenderBounds(b[0], b[1], b[2], b[3], b[4], b[5]);
                    cube(block, rb, g.arm(meta));
                }
            }
        } else {
            rb.setRenderBounds(0, 0, 0, 1, 1, 1);
            cube(block, rb, block.getIcon(0, meta));
        }
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    /** The six faces of the renderer's box, for the inventory. */
    static void cube(Block block, RenderBlocks rb, IIcon icon) {
        Tessellator t = Tessellator.instance;
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
    }

    /**
     * An icon drawn whole across a smaller box: the box's lo to hi (0 to 1) maps to the icon's whole width and height
     * (RenderBlocks takes a face's texture from where the box sits in the block).
     */
    static final class Whole implements IIcon {

        final IIcon in;
        final double lo, span;

        Whole(IIcon in, float lo, float hi) {
            this.in = in;
            this.lo = lo * 16.0;
            this.span = (hi - lo) * 16.0;
        }

        @Override
        public int getIconWidth() {
            return in.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return in.getIconHeight();
        }

        @Override
        public float getMinU() {
            return in.getMinU();
        }

        @Override
        public float getMaxU() {
            return in.getMaxU();
        }

        @Override
        public float getInterpolatedU(double u) {
            return in.getInterpolatedU((u - lo) * 16.0 / span);
        }

        @Override
        public float getMinV() {
            return in.getMinV();
        }

        @Override
        public float getMaxV() {
            return in.getMaxV();
        }

        @Override
        public float getInterpolatedV(double v) {
            return in.getInterpolatedV((v - lo) * 16.0 / span);
        }

        @Override
        public String getIconName() {
            return in.getIconName();
        }
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
