package com.valier.tweaks.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.PipeActionMessage;
import com.valier.tweaks.storage.SatchelInventory;
import com.valier.tweaks.storage.SatchelMenu;
import com.valier.tweaks.storage.SatchelTier;

/**
 * A Satchel's or Flask's menu ({@link SatchelMenu}): its slots (drawn from the item itself, any count), the selected
 * slot's settings and the Satchel's pickup switches, then the inventory. Corner marks on a slot: violet top left void
 * overflow, cyan top right ore dictionary, white bottom left locked, a yellow line under it a keep amount; the selected
 * slot is blue.
 */
public class GuiSatchel extends ValierScreen {

    final SatchelMenu c;
    final int rows;

    public GuiSatchel(SatchelMenu c) {
        super(c);
        this.c = c;
        SatchelInventory inv = c.inv();
        rows = inv == null ? 1 : (inv.size + 8) / 9;
        xSize = 176;
        ySize = SatchelMenu.invY(rows) + 82;
    }

    SatchelInventory inv() {
        return c.inv();
    }

    @Override
    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
    }

    int settingsY() {
        return 18 + rows * 18 + 8;
    }

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        int y = settingsY();
        boolean fl = inv() != null && inv().fluid;
        stepper(8, y, 18, 40, SatchelMenu.KEEP, fl ? 100 : 1, fl ? 1000 : 64, () -> {
            SatchelInventory inv = inv();
            return inv == null ? "" : number(inv.keep[inv.selected]);
        }, () -> lines(EnumChatFormatting.WHITE + "Keep (selected slot)", "Shift-clicks, placing and automation", "leave this many.",
            EnumChatFormatting.GRAY + (inv() != null && inv().fluid ? "Steps of 100 mB; shift: 1,000. Wheel works too." : "Shift: by 64. Wheel works too.")));
        Widget ore = add(80, y, 18, 18, b -> send(SatchelMenu.ORE, 0, 0));
        ore.item = () -> new ItemStack(Blocks.iron_ore);
        ore.badge = () -> inv() != null && inv().ore[inv().selected] ? ICON_CHECK : ICON_CROSS;
        ore.visible = () -> inv() != null && !inv().fluid;
        ore.tip = () -> lines(EnumChatFormatting.WHITE + "Ore dictionary (selected slot)", "On: picked-up items sharing an ore name",
            "are stored as this slot's item.");
        Widget voids = add(100, y, 18, 18, b -> send(SatchelMenu.VOID, 0, 0));
        voids.item = () -> new ItemStack(Blocks.obsidian);
        voids.badge = () -> inv() != null && inv().voids[inv().selected] ? ICON_CHECK : ICON_CROSS;
        voids.tip = () -> lines(EnumChatFormatting.WHITE + "Void overflow (selected slot)", "On: when it is full, more of it is",
            "still taken, and destroyed.");
        Widget lock = add(120, y, 18, 18, b -> send(SatchelMenu.LOCK, 0, 0));
        lock.item = () -> new ItemStack(Items.iron_door);
        lock.badge = () -> inv() != null && inv().lock[inv().selected] ? ICON_CHECK : ICON_CROSS;
        lock.tip = () -> lines(EnumChatFormatting.WHITE + "Lock (selected slot)", "On: it keeps its item at 0, so nothing",
            "else takes the slot.");
        Widget pickup = add(140, y, 18, 18, b -> send(SatchelMenu.PICKUP, 0, 0));
        pickup.item = () -> new ItemStack(Blocks.hopper);
        pickup.badge = () -> inv() != null && inv().pickup ? ICON_CHECK : ICON_CROSS;
        pickup.visible = () -> inv() != null && !inv().fluid;
        pickup.tip = () -> lines(EnumChatFormatting.WHITE + "Pickup " + (inv() != null && inv().pickup ? "on" : "off"),
            "Items you pick up that it holds go in", "first (anywhere in your inventory).");
        Widget pickNew = add(158 - 2, y + 20, 14, 10, b -> send(SatchelMenu.PICKNEW, 0, 0));
        pickNew.text = () -> inv() != null && inv().pickNew ? "+" : "-";
        pickNew.visible = () -> inv() != null && !inv().fluid;
        pickNew.tip = () -> lines(EnumChatFormatting.WHITE + "New items " + (inv() != null && inv().pickNew ? "too" : "no"),
            "On: picked-up items it doesn't hold", "go into empty slots too.");
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    int cellX(int i) {
        return guiLeft + 8 + (i % 9) * 18;
    }

    int cellY(int i) {
        return guiTop + 18 + (i / 9) * 18;
    }

    int cellAt(int mx, int my) {
        SatchelInventory inv = inv();
        if (inv == null) return -1;
        for (int i = 0; i < inv.size; i++) {
            int x = cellX(i), y = cellY(i);
            if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) return i;
        }
        return -1;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        panel(guiLeft, guiTop, xSize, ySize);
        SatchelInventory inv = inv();
        if (inv != null) {
            for (int i = 0; i < inv.size; i++) {
                int x = cellX(i), y = cellY(i);
                slot(x, y, i == inv.selected, false);
                if (inv.fluid) fluid(inv.fluids[i], x, y);
                else item(inv.items[i], x, y);
                if (inv.voids[i]) drawRect(x, y, x + 3, y + 3, 0xFFB070E0);
                if (inv.ore[i]) drawRect(x + 13, y, x + 16, y + 3, 0xFF50D0E0);
                if (inv.lock[i]) drawRect(x, y + 13, x + 3, y + 16, 0xFFFFFFFF);
                if (inv.keep[i] > 0) drawRect(x, y + 16, x + 16, y + 17, 0xFFE0D050);
            }
        }
        for (Object o : inventorySlots.inventorySlots) {
            net.minecraft.inventory.Slot s = (net.minecraft.inventory.Slot) o;
            slot(guiLeft + s.xDisplayPosition, guiTop + s.yDisplayPosition, false, false);
        }
        drawWidgets(mx, my);
    }

    void fluid(FluidStack f, int x, int y) {
        if (f == null || f.getFluid() == null) return;
        IIcon icon = f.getFluid()
            .getStillIcon();
        if (icon == null) icon = f.getFluid()
            .getIcon();
        if (icon == null) return;
        mc.getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        int colour = f.getFluid()
            .getColor(f);
        GL11.glColor4f((colour >> 16 & 255) / 255F, (colour >> 8 & 255) / 255F, (colour & 255) / 255F, 1F);
        drawTexturedModelRectFromIcon(x, y, icon, 16, 16);
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        SatchelInventory inv = inv();
        if (inv == null) return;
        String name = (inv.fluid ? "Valier Flask (" : "Valier Satchel (") + SatchelTier.NAMES[inv.tier] + ")";
        fontRendererObj.drawString(name, 8, 5, TEXT);
        String each = inv.creative ? "endless" : number(inv.capacity) + (inv.fluid ? " mB" : "") + " each";
        fontRendererObj.drawString(each, xSize - 8 - fontRendererObj.getStringWidth(each), 5, 0x707070);
        fontRendererObj.drawString("Keep", 8, settingsY() + 21, 0x606060);
        if (!inv.fluid) fontRendererObj.drawString("New items", 100, settingsY() + 21, 0x606060);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < inv.size; i++) {
            if (inv.isEmpty(i)) continue;
            String n = inv.creative ? "∞" : number(inv.fluid ? inv.fluids[i].amount / 1000 : inv.counts[i]) + (inv.fluid ? "B" : "");
            int x = 8 + (i % 9) * 18, y = 18 + (i / 9) * 18;
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 300);
            GL11.glScalef(0.75F, 0.75F, 1F);
            fontRendererObj.drawStringWithShadow(n, (int) ((x + 17) / 0.75F) - fontRendererObj.getStringWidth(n), (int) ((y + 10) / 0.75F),
                0xFFFFFF);
            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        if (widgetTip(mx, my)) return;
        int i = cellAt(mx, my);
        SatchelInventory inv = inv();
        if (i < 0 || inv == null) return;
        List<String> tip = new ArrayList<>();
        if (inv.fluid) {
            if (inv.fluids[i] != null) {
                tip.add(EnumChatFormatting.WHITE + inv.fluids[i].getLocalizedName());
                tip.add(EnumChatFormatting.YELLOW + (inv.creative ? "Endless" : String.format("%,d / %,d mB", inv.fluids[i].amount, inv.capacity)));
            } else tip.add(EnumChatFormatting.WHITE + "Tank " + (i + 1) + (inv.lock[i] ? " (locked)" : " (empty)"));
            tip.add(EnumChatFormatting.GRAY + "Click with a bucket or cell: fill or empty it.");
        } else {
            if (inv.items[i] != null) {
                @SuppressWarnings("unchecked")
                List<String> t = inv.items[i].getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
                tip.addAll(t);
                tip.add(EnumChatFormatting.YELLOW + (inv.creative ? "Endless" : String.format("%,d / %,d", inv.counts[i], inv.capacity)));
            } else tip.add(EnumChatFormatting.WHITE + "Slot " + (i + 1) + (inv.lock[i] ? " (locked)" : " (empty)"));
            tip.add(EnumChatFormatting.GRAY + "Click: a stack (or put in). Right-click: one.");
            tip.add(EnumChatFormatting.GRAY + "Shift-click: a stack to your inventory.");
        }
        if (inv.keep[i] > 0) tip.add(EnumChatFormatting.GOLD + "Keeps " + String.format("%,d", inv.keep[i]));
        if (inv.voids[i]) tip.add(EnumChatFormatting.LIGHT_PURPLE + "Void overflow");
        if (inv.ore[i]) tip.add(EnumChatFormatting.AQUA + "Ore dictionary");
        tip.add(EnumChatFormatting.DARK_GRAY + (i == inv.selected ? "Selected" : "Ctrl-click: select (placing, settings)"));
        drawHoveringText(tip, mx, my, fontRendererObj);
    }

    // ---- input -------------------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        int i = cellAt(mx, my);
        SatchelInventory inv = inv();
        if (i >= 0 && inv != null) {
            if (GuiScreen.isCtrlKeyDown() || button == 2) send(SatchelMenu.SELECT, i, 0);
            else if (inv.fluid) {
                if (mc.thePlayer.inventory.getItemStack() != null) send(SatchelMenu.CONTAINER, i, 0);
                else send(SatchelMenu.SELECT, i, 0);
            } else if (shift()) send(SatchelMenu.SHIFT, i, 0);
            else send(SatchelMenu.TAKE, i, button == 1 ? 1 : 0);
            return;
        }
        super.mouseClicked(mx, my, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dir = wheel();
        if (dir != 0) wheelWidgets(mouseX(), mouseY(), dir);
    }
}
