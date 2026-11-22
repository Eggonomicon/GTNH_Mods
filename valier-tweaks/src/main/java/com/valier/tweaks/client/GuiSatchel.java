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

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.PipeActionMessage;
import com.valier.tweaks.storage.ItemSatchel;
import com.valier.tweaks.storage.SatchelInventory;
import com.valier.tweaks.storage.SatchelMenu;
import com.valier.tweaks.storage.SatchelTier;

/**
 * A Satchel's or Flask's menu ({@link SatchelMenu}): its slots (drawn from the item itself, any count) and the
 * inventory; on the right (2.3.3) the selected slot's settings and the Satchel's switches. Marks on a slot: violet top
 * left void overflow, cyan top right ore dictionary, white bottom left locked, a yellow line under it a keep amount;
 * 2.3.3: blue bottom right refill, green top middle send home. The selected slot has an orange frame (2.3.2).
 */
public class GuiSatchel extends ValierScreen {

    final SatchelMenu c;
    final int rows;

    public GuiSatchel(SatchelMenu c) {
        super(c);
        this.c = c;
        SatchelInventory inv = c.inv();
        rows = inv == null ? 1 : (inv.size + 8) / 9;
        xSize = SIDE + SIDE_W;
        ySize = SatchelMenu.invY(rows) + 82;
    }

    SatchelInventory inv() {
        return c.inv();
    }

    @Override
    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
    }

    /** The settings panel on the right (2.3.3): where it starts, how wide. */
    static final int SIDE = 180, SIDE_W = 100;

    int sideH() {
        return inv() != null && inv().fluid ? 60 : 112;
    }

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        boolean fl = inv() != null && inv().fluid;
        int x = SIDE + 4;
        stepper(x + 28, 16, 18, 36, SatchelMenu.KEEP, fl ? 100 : 1, fl ? 1000 : 64, () -> {
            SatchelInventory inv = inv();
            return inv == null ? "" : number(inv.keep[inv.selected]);
        }, () -> lines(EnumChatFormatting.WHITE + "Keep (selected slot)", "Shift-clicks, placing, the dock, chests", "and refills leave this many.",
            EnumChatFormatting.GRAY + (fl ? "Steps of 100 mB; shift: 1,000. Wheel works too." : "Shift: by 64. Wheel works too.")));
        if (!fl) stepper(x + 28, 36, 18, 36, SatchelMenu.REFILL, 1, 64, () -> {
            SatchelInventory inv = inv();
            return inv == null ? "" : inv.refill[inv.selected] == 0 ? "off" : number(inv.refill[inv.selected]);
        }, this::refillTip);
        int row = fl ? 38 : 56;
        Widget ore = add(x, row, 18, 18, b -> send(SatchelMenu.ORE, 0, 0));
        ore.item = () -> new ItemStack(Blocks.iron_ore);
        ore.badge = () -> inv() != null && inv().ore[inv().selected] ? ICON_CHECK : ICON_CROSS;
        ore.visible = () -> !fl;
        ore.tip = () -> lines(EnumChatFormatting.WHITE + "Ore dictionary (selected slot)", "On: picked-up items sharing an ore name",
            "are stored as this slot's item.");
        Widget voids = add(fl ? x : x + 20, row, 18, 18, b -> send(SatchelMenu.VOID, 0, 0));
        voids.item = () -> new ItemStack(Blocks.obsidian);
        voids.badge = () -> inv() != null && inv().voids[inv().selected] ? ICON_CHECK : ICON_CROSS;
        voids.tip = () -> lines(EnumChatFormatting.WHITE + "Void overflow (selected slot)", "On: when it is full, more of it is",
            "still taken, and destroyed.");
        Widget lock = add(fl ? x + 20 : x + 40, row, 18, 18, b -> send(SatchelMenu.LOCK, 0, 0));
        lock.item = () -> new ItemStack(Items.iron_door);
        lock.badge = () -> inv() != null && inv().lock[inv().selected] ? ICON_CHECK : ICON_CROSS;
        lock.tip = () -> lines(EnumChatFormatting.WHITE + "Lock (selected slot)", "On: it keeps its item at 0, so nothing",
            "else takes the slot.");
        if (fl) return;
        Widget home = add(x + 60, row, 18, 18, b -> send(SatchelMenu.HOME, 0, b == 1 ? 1 : 0));
        home.item = () -> new ItemStack(ModBlocks.barrel);
        home.badge = () -> inv() != null && inv().home[inv().selected] != null ? ICON_CHECK : ICON_CROSS;
        home.tip = this::homeTip;
        Widget pickup = add(x, 90, 18, 18, b -> send(SatchelMenu.PICKUP, 0, 0));
        pickup.item = () -> new ItemStack(Blocks.hopper);
        pickup.badge = () -> inv() != null && inv().pickup ? ICON_CHECK : ICON_CROSS;
        pickup.tip = () -> lines(EnumChatFormatting.WHITE + "Pickup " + (inv() != null && inv().pickup ? "on" : "off"),
            "Items you pick up that it holds go in", "first (anywhere in your inventory).");
        Widget pickNew = add(x + 20, 90, 18, 18, b -> send(SatchelMenu.PICKNEW, 0, 0));
        pickNew.item = () -> new ItemStack(Items.paper);
        pickNew.badge = () -> inv() != null && inv().pickNew ? ICON_CHECK : ICON_CROSS;
        pickNew.tip = () -> lines(EnumChatFormatting.WHITE + "New items " + (inv() != null && inv().pickNew ? "too" : "no"),
            "On: picked-up items it doesn't hold go", "into empty slots too (and Take out", "takes anything).");
        Widget restock = add(x + 40, 90, 18, 18, b -> send(SatchelMenu.RESTOCK, 0, 0));
        restock.item = () -> new ItemStack(Blocks.cobblestone);
        restock.badge = () -> inv() != null && inv().restock ? ICON_CHECK : ICON_CROSS;
        restock.tip = () -> lines(EnumChatFormatting.WHITE + "Hotbar restock " + (inv() != null && inv().restock ? "on" : "off"),
            "When the stack in your hand runs out", "(the last block placed, the last food", "eaten), a new stack comes from here.");
        Widget box = add(x + 60, 90, 18, 18, b -> send(SatchelMenu.BOX, b == 1 ? -1 : 1, 0));
        box.item = () -> new ItemStack(Blocks.chest);
        box.overlay = () -> inv() == null ? "" : BOX_SHORT[inv().box];
        box.tip = () -> {
            SatchelInventory inv = inv();
            int m = inv == null ? 0 : inv.box;
            return lines(EnumChatFormatting.WHITE + "Sneak + right-click a chest:", EnumChatFormatting.YELLOW + ItemSatchel.boxText(m),
                "Any container: barrels, machines too.", "Each slot keeps its keep amount.",
                EnumChatFormatting.GRAY + "Click: next. Right-click: back.");
        };
    }

    static final String[] BOX_SHORT = { "sel", "all", "out", "off" };

    List<String> refillTip() {
        SatchelInventory inv = inv();
        int n = inv == null ? 0 : inv.refill[inv.selected];
        ItemStack it = inv == null ? null : inv.items[inv.selected];
        String head = n == 0 ? "Refill (selected slot): off" : "Refill (selected slot): " + String.format("%,d", n)
            + (it != null && n == it.getMaxStackSize() ? " (one stack)" : "");
        return lines(EnumChatFormatting.WHITE + head, "Keeps this many of its item in your", "inventory, topped up from the Satchel.",
            EnumChatFormatting.GRAY + "Shift: by 64 (a stack of blocks).", EnumChatFormatting.GRAY + "Waits while a chest or menu is open.");
    }

    List<String> homeTip() {
        SatchelInventory inv = inv();
        String to = inv == null || inv.home[inv.selected] == null ? "off" : "\"" + inv.home[inv.selected] + "\"";
        return lines(EnumChatFormatting.WHITE + "Send home (selected slot): " + to, "Picked-up items over its keep amount",
            "go straight to your team's barrel", "channel, wherever its barrels are.",
            EnumChatFormatting.GRAY + "Click: the next channel that takes it.", EnumChatFormatting.GRAY + "Right-click: off.");
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
        panel(guiLeft, guiTop, 176, ySize);
        panel(guiLeft + SIDE, guiTop, SIDE_W, sideH());
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
                if (!inv.fluid && inv.refill[i] > 0) drawRect(x + 13, y + 13, x + 16, y + 16, 0xFF60A0FF);
                if (!inv.fluid && inv.home[i] != null) drawRect(x + 6, y, x + 10, y + 3, 0xFF60D060);
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
        fontRendererObj.drawString(each, 176 - 8 - fontRendererObj.getStringWidth(each), 5, 0x707070);
        // The settings panel: the selected slot's, then (a Satchel) the whole Satchel's.
        String sel = (inv.fluid ? "Tank " : "Slot ") + (inv.selected + 1) + (inv.isEmpty(inv.selected) ? ""
            : ": " + (inv.fluid ? inv.fluids[inv.selected].getLocalizedName() : inv.items[inv.selected].getDisplayName()));
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(sel, SIDE_W - 8), SIDE + 4, 5, TEXT);
        fontRendererObj.drawString("Keep", SIDE + 4, 21, 0x606060);
        if (!inv.fluid) {
            fontRendererObj.drawString("Refill", SIDE + 4, 41, 0x606060);
            fontRendererObj.drawString("Satchel", SIDE + 4, 80, TEXT);
        }
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
        // The selected slot or tank (2.3.2): a bright frame round it, over its item or fluid (a fluid covers the whole
        // slot, so its blue background never showed).
        if (inv.selected >= 0 && inv.selected < inv.size) {
            int x = 8 + (inv.selected % 9) * 18, y = 18 + (inv.selected / 9) * 18;
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 250);
            frame(x - 1, y - 1, x + 17, y + 17, SELECTED);
            frame(x, y, x + 16, y + 16, SELECTED);
            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    static final int SELECTED = 0xFFFF9A1E;

    static void frame(int x0, int y0, int x1, int y1, int colour) {
        drawRect(x0, y0, x1, y0 + 1, colour);
        drawRect(x0, y1 - 1, x1, y1, colour);
        drawRect(x0, y0 + 1, x0 + 1, y1 - 1, colour);
        drawRect(x1 - 1, y0 + 1, x1, y1 - 1, colour);
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
        if (!inv.fluid && inv.refill[i] > 0) tip.add(EnumChatFormatting.BLUE + "Refills your inventory to " + String.format("%,d", inv.refill[i]));
        if (!inv.fluid && inv.home[i] != null) tip.add(EnumChatFormatting.GREEN + "Sends home: " + inv.home[i]);
        if (i == inv.selected) tip.add(EnumChatFormatting.GOLD + (inv.fluid ? "Selected: placed, and put in tanks" : "Selected: placed by right-click"));
        else tip.add(EnumChatFormatting.DARK_GRAY + (inv.fluid ? "Click: select it" : "Ctrl-click or middle-click: select it"));
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
        // Ctrl-click or middle-click on something in your inventory (2.3.2): select the slot or tank holding it.
        if (inv != null && (GuiScreen.isCtrlKeyDown() || button == 2)) {
            net.minecraft.inventory.Slot s = inventorySlotAt(mx, my);
            if (s != null && s.getHasStack()) {
                int at = holding(inv, s.getStack());
                if (at >= 0) send(SatchelMenu.SELECT, at, 0);
                return;
            }
        }
        super.mouseClicked(mx, my, button);
    }

    net.minecraft.inventory.Slot inventorySlotAt(int mx, int my) {
        for (Object o : inventorySlots.inventorySlots) {
            net.minecraft.inventory.Slot s = (net.minecraft.inventory.Slot) o;
            int x = guiLeft + s.xDisplayPosition, y = guiTop + s.yDisplayPosition;
            if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) return s;
        }
        return null;
    }

    /** The Satchel's slot holding stack (a Flask: the tank holding the fluid in it, a bucket or cell), else -1. */
    static int holding(SatchelInventory inv, ItemStack stack) {
        if (!inv.fluid) return inv.find(stack);
        ItemStack one = stack.copy();
        one.stackSize = 1;
        return inv.findFluid(SatchelMenu.containerFluid(one));
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dir = wheel();
        if (dir != 0) wheelWidgets(mouseX(), mouseY(), dir);
    }
}
