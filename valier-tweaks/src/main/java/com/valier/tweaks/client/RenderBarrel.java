package com.valier.tweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.valier.tweaks.storage.TileBarrel;

/**
 * A barrel's label: the item it holds and how many, on its front. Cheap by design: only barrels within 16 blocks whose
 * front the player is in front of are drawn, and the count's text is made once per change, not per frame. Linked
 * barrels' counts are violet, void-overflow barrels' pink.
 */
public class RenderBarrel extends TileEntitySpecialRenderer {

    private static final double RANGE_SQ = 16 * 16;
    private final RenderItem items = new RenderItem();

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partial) {
        if (!(te instanceof TileBarrel)) return;
        TileBarrel t = (TileBarrel) te;
        if (t.shownItem == null || t.shownItem.getItem() == null) return;
        double cx = x + 0.5, cy = y + 0.5, cz = z + 0.5;
        if (cx * cx + cy * cy + cz * cz > RANGE_SQ) return;
        ForgeDirection d = ForgeDirection.getOrientation(t.facing);
        if (-cx * d.offsetX - cz * d.offsetZ <= 0.5) return; // the player isn't in front of its label
        if (t.label == null || t.labelFor != t.shownCount) {
            t.label = text(t.shownCount);
            t.labelFor = t.shownCount;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer font = mc.fontRenderer;
        int light = te.getWorldObj()
            .getLightBrightnessForSkyBlocks(te.xCoord + d.offsetX, te.yCoord, te.zCoord + d.offsetZ, 0);
        GL11.glPushMatrix();
        GL11.glTranslated(cx, cy, cz);
        GL11.glRotatef(angle(t.facing), 0, 1, 0);
        GL11.glTranslated(0, 0, 0.5 + 0.003);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light % 65536, light / 65536f);
        // The item, flat, half a block wide, a little above the middle.
        GL11.glPushMatrix();
        GL11.glTranslated(0, 0.1, 0);
        float s = 0.5F / 16F;
        GL11.glScalef(s, -s, 0.0005F);
        GL11.glTranslatef(-8, -8, 0);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        items.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), t.shownItem, 0, 0);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
        // The count under it.
        GL11.glTranslated(0, -0.27, 0.001);
        float ts = 0.0125F;
        GL11.glScalef(ts, -ts, ts);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        int colour = !t.shownChannel.isEmpty() ? 0xE6A0FF : t.shownVoid ? 0xFFB0B0 : 0xFFFFFF;
        font.drawString(t.label, -font.getStringWidth(t.label) / 2, 0, colour);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glPopMatrix();
    }

    /** The turn (degrees about Y) that points +Z out of the label's face. */
    static float angle(int facing) {
        switch (facing) {
            case 2:
                return 180F;
            case 4:
                return 270F;
            case 5:
                return 90F;
            default:
                return 0F;
        }
    }

    static String text(long n) {
        if (n >= 10_000_000_000L) return (n / 1_000_000_000L) + "G";
        if (n >= 10_000_000L) return (n / 1_000_000L) + "M";
        if (n >= 100_000L) return (n / 1000L) + "k";
        return String.format("%,d", n);
    }
}
