package com.valier.tweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.storage.ItemSatchel;
import com.valier.tweaks.storage.SatchelEvents;
import com.valier.tweaks.storage.SatchelInventory;

/**
 * Players' side of a held Satchel or Flask: sneak + mouse wheel picks its slot (the server is told), and for 3 seconds
 * after it is taken in hand or a slot is picked, the selected item or fluid and how much of it shows beside the hotbar.
 */
public final class SatchelHud {

    private static final long SHOW_MS = 3000;
    private final RenderItem items = new RenderItem();
    private ItemStack lastStack;
    private int lastSelected = -1;
    private long shownAt;

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onMouse(MouseEvent event) {
        if (event.dwheel == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.currentScreen != null || !mc.thePlayer.isSneaking()) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemSatchel)) return;
        event.setCanceled(true);
        int dir = event.dwheel > 0 ? -1 : 1;
        ValierTweaks.NET.sendToServer(new SatchelEvents.Scroll(dir));
        shownAt = System.currentTimeMillis();
    }

    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.HOTBAR) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        ItemStack held = mc.thePlayer.getHeldItem();
        SatchelInventory inv = SatchelInventory.of(held);
        long now = System.currentTimeMillis();
        if (inv == null) {
            lastStack = null;
            return;
        }
        if (held != lastStack && (lastStack == null || lastStack.getItem() != held.getItem()) || inv.selected != lastSelected) shownAt = now;
        lastStack = held;
        lastSelected = inv.selected;
        if (now - shownAt > SHOW_MS) return;
        ScaledResolution res = event.resolution;
        int x = res.getScaledWidth() / 2 + 96, y = res.getScaledHeight() - 20;
        int i = inv.selected;
        String text;
        if (inv.isEmpty(i)) text = "Slot " + (i + 1) + ": empty";
        else if (inv.fluid) {
            FluidStack f = inv.fluids[i];
            fluid(mc, f, x, y);
            text = f.getLocalizedName() + " " + (inv.creative ? "∞" : String.format("%,d mB", f.amount));
        } else {
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.enableGUIStandardItemLighting();
            items.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), inv.items[i], x, y);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            text = inv.items[i].getDisplayName() + " × " + (inv.creative ? "∞" : String.format("%,d", inv.counts[i]));
        }
        GL11.glDisable(GL11.GL_LIGHTING);
        mc.fontRenderer.drawStringWithShadow(text, x + 19, y + 4, 0xFFFFFF);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private static void fluid(Minecraft mc, FluidStack f, int x, int y) {
        if (f.getFluid() == null) return;
        IIcon icon = f.getFluid()
            .getStillIcon();
        if (icon == null) return;
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        int c = f.getFluid()
            .getColor(f);
        GL11.glColor4f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, 1F);
        mc.ingameGUI.drawTexturedModelRectFromIcon(x, y, icon, 16, 16);
        GL11.glColor4f(1, 1, 1, 1);
    }
}
