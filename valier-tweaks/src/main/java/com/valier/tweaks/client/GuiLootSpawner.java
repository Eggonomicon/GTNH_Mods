package com.valier.tweaks.client;

import java.util.List;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.valier.tweaks.LootMenu;
import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FilterList;
import com.valier.tweaks.pipe.PipeActionMessage;

/**
 * A loot spawner's menu ({@link LootMenu}): what it is doing on top, then three tabs. Store: the loot it is keeping
 * (take it out). Allow and Void: its loot filter, with the pipe lists' toolbar and ghost slots (click with an item or
 * drag one from NEI to set, right-click to clear).
 */
public class GuiLootSpawner extends ValierScreen {

    final LootMenu c;

    public GuiLootSpawner(LootMenu c) {
        super(c);
        this.c = c;
        xSize = 176;
        ySize = 215;
    }

    @Override
    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
    }

    @Override
    FilterList fl() {
        return c.fl();
    }

    @Override
    int listShown() {
        return c.list;
    }

    @Override
    FilterList listAt(int list) {
        return c.listAt(list);
    }

    @Override
    void pickList(int list) {
        c.store = false;
        c.list = list;
        place();
        send(PipeActionMessage.LIST, list, 0);
    }

    @Override
    boolean toolbarShown() {
        return !c.store;
    }

    @Override
    boolean kindShown(FaceConfig.Kind k) {
        return k != FaceConfig.Kind.EXISTING;
    }

    @Override
    List<String> snapshotTip() {
        return lines(EnumChatFormatting.WHITE + "Snapshot", "The " + FaceConfig.LIST_NAMES[c.list] + " list becomes what",
            "the store holds now (each item once).");
    }

    @Override
    List<String> listExplain(int list) {
        List<String> out = lines(EnumChatFormatting.WHITE + FaceConfig.LIST_NAMES[list] + " list (" + listAt(list).entries() + ")");
        if (list == FaceConfig.ALLOW) {
            out.add("Only the items it names come out (empty: all).");
            out.add("Everything else is destroyed as it is made.");
        } else {
            out.add(EnumChatFormatting.LIGHT_PURPLE + "The items it names are destroyed as they");
            out.add(EnumChatFormatting.LIGHT_PURPLE + "are made. Wins over the Allow list.");
        }
        out.add(EnumChatFormatting.GRAY + "Match NBT to pick e.g. one enchanted book.");
        return out;
    }

    // ---- layout -------------------------------------------------------------------------------------------------------

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        Widget storeTab = add(8, 47, 52, 11, b -> {
            c.store = true;
            selected = -1;
            place();
            send(PipeActionMessage.LIST, LootMenu.STORE, 0);
        });
        storeTab.text = () -> "Store";
        storeTab.on = () -> c.store;
        storeTab.colour = () -> 0xFFE080;
        storeTab.tip = () -> lines(EnumChatFormatting.WHITE + "Store", "Loot nothing touching it took yet.", "Take it out; nothing goes in.");
        addListTabs(61, 47, FaceConfig.ALLOW, FaceConfig.VOID);
        addToolbar(60);
        place();
    }

    /** Puts the store's or the ghost slots on screen (the other ones off it). */
    void place() {
        for (Object o : c.inventorySlots) {
            Slot s = (Slot) o;
            if (s.slotNumber < LootMenu.STORE_FROM) {
                int i = s.slotNumber;
                s.xDisplayPosition = c.store ? -1000 : 8 + (i % 9) * 18;
                s.yDisplayPosition = LootMenu.GHOST_Y + (i / 9) * 18;
            } else if (s.slotNumber < LootMenu.INV_FROM) {
                int i = s.slotNumber - LootMenu.STORE_FROM;
                s.xDisplayPosition = c.store ? 8 + (i % 9) * 18 : -1000;
                s.yDisplayPosition = LootMenu.STORE_Y + (i / 9) * 18;
            }
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        place(); // the server may have changed the view
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        place();
        panel(guiLeft, guiTop, xSize, ySize);
        if (!c.store) filterArea(guiLeft + 5, guiTop + 59, guiLeft + xSize - 5, guiTop + 119);
        drawSlots();
        drawWidgets(mx, my);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString("Valier Loot Spawner", 8, 5, TEXT);
        fontRendererObj.drawString(trim(c.line1), 8, 16, 0x303030);
        fontRendererObj.drawString(trim(c.line2), 8, 26, c.line2.startsWith("Working") ? 0x2E7D32 : 0x9A4A1A);
        fontRendererObj.drawString(trim(c.line3), 8, 36, 0x606060);
        fontRendererObj.drawString(trim(info()), 8, 122, !c.store && !c.note.isEmpty() ? 0x9A1A1A : 0x606060);
    }

    String trim(String s) {
        return fontRendererObj.trimStringToWidth(s, xSize - 16);
    }

    /** The line under the store or the list. */
    String info() {
        boolean filters = c.listAt(FaceConfig.ALLOW)
            .active()
            || c.listAt(FaceConfig.VOID)
                .active();
        if (c.store) return !filters ? "No loot filter: everything comes out"
            : "Filter destroyed " + number(c.voided) + " in the last hour";
        if (!c.note.isEmpty()) return c.note;
        if (c.list == FaceConfig.ALLOW) return fl().active() ? "Only what this list names comes out" : "Empty: everything comes out";
        return "What this list names is destroyed";
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        if (widgetTip(mx, my)) return;
        if (mx >= guiLeft + 6 && mx < guiLeft + xSize - 6 && my >= guiTop + 14 && my < guiTop + 46 && !c.full.isEmpty()) {
            @SuppressWarnings("unchecked")
            List<String> l = fontRendererObj.listFormattedStringToWidth(c.full, 240);
            drawHoveringText(l, mx, my, fontRendererObj);
            return;
        }
        int slot = ghostAt(mx, my);
        if (slot >= 0 && fl().filter[slot] == null && mc.thePlayer.inventory.getItemStack() == null) {
            drawHoveringText(lines(EnumChatFormatting.WHITE + FaceConfig.LIST_NAMES[c.list] + " slot " + (slot + 1),
                "Click with an item (or drag one from NEI)", "to set it.", EnumChatFormatting.GRAY + "Right-click clears."), mx, my,
                fontRendererObj);
        }
    }

    /** Vanilla's item tooltip, plus what a filter slot does. */
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void renderToolTip(ItemStack stack, int x, int y) {
        int slot = ghostAt(x, y);
        if (slot < 0) {
            super.renderToolTip(stack, x, y);
            return;
        }
        List list = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        list.add(c.list == FaceConfig.VOID ? EnumChatFormatting.LIGHT_PURPLE + "Destroyed as it is made"
            : EnumChatFormatting.GREEN + "Comes out");
        list.add(EnumChatFormatting.GRAY + "Right-click: clear.");
        drawHoveringText(list, x, y, fontRendererObj);
    }

    // ---- input -----------------------------------------------------------------------------------------------------

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dir = wheel();
        if (dir != 0) wheelWidgets(mouseX(), mouseY(), dir);
    }
}
