package com.valier.tweaks.client;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.PipeActionMessage;
import com.valier.tweaks.storage.BarrelControllerMenu;

/**
 * A Barrel Controller's menu ({@link BarrelControllerMenu}): the barrels it reaches, the items they hold most of, and
 * the "fill empty barrels" switch.
 */
public class GuiBarrelController extends ValierScreen {

    final BarrelControllerMenu c;

    public GuiBarrelController(BarrelControllerMenu c) {
        super(c);
        this.c = c;
        xSize = 176;
        ySize = 128;
    }

    @Override
    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
    }

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        Widget fill = add(8, 44, 18, 18, b -> send(BarrelControllerMenu.FILL, 0, 0));
        fill.item = () -> new ItemStack(Blocks.chest);
        fill.badge = () -> c.fillEmpty ? ICON_CHECK : ICON_CROSS;
        fill.tip = () -> c.fillEmpty
            ? lines(EnumChatFormatting.WHITE + "Fill empty barrels: on", "An item no barrel holds yet goes into",
                "an empty, unlocked barrel.", EnumChatFormatting.GRAY + "Click to turn it off.")
            : lines(EnumChatFormatting.WHITE + "Fill empty barrels: off", "Only items a barrel already holds (or is",
                "locked to) go in; anything else stays", "where it came from.", EnumChatFormatting.GRAY + "Click to turn it on.");
        for (int i = 0; i < BarrelControllerMenu.SHOWN; i++) {
            int k = i;
            Widget w = add(8 + 20 * i, 90, 18, 18, null);
            w.flat = true;
            w.visible = () -> k < c.top.size();
            w.item = () -> k < c.top.size() ? c.top.get(k) : null;
            w.overlay = () -> k < c.topCount.size() ? number(c.topCount.get(k)) : "";
            w.tip = () -> k < c.top.size()
                ? lines(EnumChatFormatting.WHITE + c.top.get(k)
                    .getDisplayName(), String.format("%,d", c.topCount.get(k)))
                : null;
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        panel(guiLeft, guiTop, xSize, ySize);
        for (int i = 0; i < BarrelControllerMenu.SHOWN; i++) slot(guiLeft + 8 + 20 * i, guiTop + 90, false, false);
        drawWidgets(mx, my);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString("Valier Barrel Controller", 8, 5, TEXT);
        String barrels = String.format("%,d barrel%s", c.barrels, c.barrels == 1 ? "" : "s");
        if (c.stores != c.barrels) barrels += String.format(" (%,d stores: linked ones share)", c.stores);
        fontRendererObj.drawString(barrels, 8, 18, 0x303030);
        fontRendererObj.drawString(String.format("%,d kinds of item, %,d empty", c.kinds, c.empties), 8, 29, 0x505050);
        fontRendererObj.drawString("Fill empty barrels: " + (c.fillEmpty ? "on" : "off"), 30, 49, TEXT);
        if (c.capped) fontRendererObj.drawString(fontRendererObj.trimStringToWidth(String.format("At its most: %,d barrels.", c.max),
            160), 8, 68, 0x9A1A1A);
        else if (c.barrels == 0) fontRendererObj.drawString("Place Valier Barrels against it.", 8, 68, 0x707070);
        else fontRendererObj.drawString(String.format("Reaches barrels in a chain, %d blocks out.", c.range), 8, 68, 0x707070);
        fontRendererObj.drawString(c.top.isEmpty() ? "Holds nothing yet." : "Holds most of:", 8, 80, TEXT);
        fontRendererObj.drawString("Right-click with an item: in it goes.", 8, 113, 0x707070);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        widgetTip(mx, my);
    }
}
