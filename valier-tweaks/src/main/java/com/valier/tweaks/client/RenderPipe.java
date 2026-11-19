package com.valier.tweaks.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.valier.tweaks.pipe.BlockPipe;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.TilePipe;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/**
 * Draws pipes into the chunk's mesh (no per-frame cost): a core, an arm to each joined side, and where an arm touches a
 * block a plate in the colour of that face's mode. It reads only the one number the server sends per pipe
 * ({@link TilePipe#renderState}).
 */
public class RenderPipe implements ISimpleBlockRenderingHandler {

    static final float LO = BlockPipe.CORE_MIN, HI = BlockPipe.CORE_MAX, PLO = BlockPipe.PLATE_MIN, PHI = BlockPipe.PLATE_MAX,
        P = BlockPipe.PLATE;

    final int id;

    public RenderPipe(int id) {
        this.id = id;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId, RenderBlocks rb) {
        TileEntity te = world.getTileEntity(x, y, z);
        int s = te instanceof TilePipe ? ((TilePipe) te).renderState : 0;
        BlockPipe pipe = (BlockPipe) block;
        IIcon icon = block.getIcon(0, world.getBlockMetadata(x, y, z));
        rb.setOverrideBlockTexture(icon);
        rb.setRenderBounds(LO, LO, LO, HI, HI, HI);
        rb.renderStandardBlock(block, x, y, z);
        for (int side = 0; side < 6; side++) {
            boolean toPipe = (s >> side & 1) != 0, toBlock = (s >> (6 + side) & 1) != 0;
            if (!toPipe && !toBlock) continue;
            float end = toBlock ? 1 - P : 1;
            arm(rb, side, LO, HI, HI, end);
            rb.renderStandardBlock(block, x, y, z);
            if (toBlock) {
                FaceMode m = FaceMode.of(s >> (12 + 3 * side) & 7);
                int c = m == FaceMode.OFF ? 0x606060 : m.colour;
                rb.setOverrideBlockTexture(pipe.plate);
                arm(rb, side, PLO, PHI, 1 - P, 1);
                rb.renderStandardBlockWithColorMultiplier(block, x, y, z, (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F,
                    (c & 255) / 255F);
                rb.setOverrideBlockTexture(icon);
            }
        }
        rb.clearOverrideBlockTexture();
        rb.setRenderBounds(0, 0, 0, 1, 1, 1);
        return true;
    }

    /** Render bounds of a box sticking out of the core towards a side: from {@code from} to {@code to} along it. */
    static void arm(RenderBlocks rb, int side, float lo, float hi, float from, float to) {
        float a = 1 - to, b = 1 - from; // the same span on the low side
        switch (side) {
            case 0:
                rb.setRenderBounds(lo, a, lo, hi, b, hi);
                break;
            case 1:
                rb.setRenderBounds(lo, from, lo, hi, to, hi);
                break;
            case 2:
                rb.setRenderBounds(lo, lo, a, hi, hi, b);
                break;
            case 3:
                rb.setRenderBounds(lo, lo, from, hi, hi, to);
                break;
            case 4:
                rb.setRenderBounds(a, lo, lo, b, hi, hi);
                break;
            default:
                rb.setRenderBounds(from, lo, lo, to, hi, hi);
        }
    }

    @Override
    public void renderInventoryBlock(Block block, int meta, int modelId, RenderBlocks rb) {
        IIcon icon = block.getIcon(0, meta);
        Tessellator t = Tessellator.instance;
        rb.setRenderBounds(0, LO, LO, 1, HI, HI);
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
        rb.setRenderBounds(0, 0, 0, 1, 1, 1);
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
