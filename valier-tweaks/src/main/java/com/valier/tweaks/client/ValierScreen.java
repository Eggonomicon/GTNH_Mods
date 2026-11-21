package com.valier.tweaks.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.ContainerPipeFace;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FilterList;
import com.valier.tweaks.pipe.PipeActionMessage;

/**
 * What Valier's menus share (the pipe face menu, the loot spawner menu): vanilla-style panels and slots drawn as
 * rectangles, icon buttons with tooltips ({@link Widget}, icons from {@code textures/gui/pipe_icons.png}), and the
 * filter lists: tabs (Allow, Deny, Void), the toolbar for the list shown (items / mods / existing, damage, NBT, ore
 * dictionary, snapshot, clear) and its ghost slots ({@link ContainerPipeFace.Ghost}). Clicks go to the server as
 * {@link PipeActionMessage}s, whose codes every Valier menu reads the same way.
 */
public abstract class ValierScreen extends GuiContainer {

    static final ResourceLocation ICONS = new ResourceLocation(ValierTweaks.MODID, "textures/gui/pipe_icons.png");
    // Icon strip positions (tools/gui_icons.py).
    static final int ICON_OFF = 0, ICON_ALLOW = 6, ICON_DENY = 7, ICON_CLEAR = 8, ICON_CHECK = 9, ICON_CROSS = 10,
        ICON_ITEMS = 11, ICON_MODS = 12, ICON_EXISTING = 13, ICON_SNAPSHOT = 14, ICON_TAG = 15, ICON_VOID = 16,
        ICON_SPREAD = 17; // + Spread ordinal
    static final int[] LIST_ICONS = { ICON_ALLOW, ICON_DENY, ICON_VOID };
    static final int[] LIST_COLOURS = { 0x7CFF7C, 0xFF7C7C, 0xE6A0FF };
    static final int[] AMOUNT_COLOURS = { 0xFFFF60, 0xFFFF60, 0xE6A0FF };
    static final int TEXT = 0x404040;

    final List<Widget> widgets = new ArrayList<>();
    /** The ghost slot picked last (for its amount), or -1. */
    int selected = -1;

    ValierScreen(Container c) {
        super(c);
    }

    /** One action to the server for this menu. */
    abstract void send(int action, int a, int b);

    /** The list the ghost slots show, and its number ({@link FaceConfig#ALLOW}, DENY or VOID); screens with lists say. */
    FilterList fl() {
        return null;
    }

    int listShown() {
        return FaceConfig.ALLOW;
    }

    FilterList listAt(int list) {
        return null;
    }

    /** Shows another list (the screen's own copy at once, then the server's say). */
    void pickList(int list) {}

    /** Whether a list does anything now (greyed when not). */
    boolean listUsed(int list) {
        return true;
    }

    /** What a list does, for its tab's tooltip. */
    List<String> listExplain(int list) {
        return lines(EnumChatFormatting.WHITE + FaceConfig.LIST_NAMES[list] + " list (" + listAt(list).entries() + ")");
    }

    boolean kindShown(FaceConfig.Kind k) {
        return true;
    }

    /** Whether the list toolbar and the ghost slots are on screen. */
    boolean toolbarShown() {
        return true;
    }

    boolean snapshotShown() {
        return true;
    }

    List<String> snapshotTip() {
        return lines(EnumChatFormatting.WHITE + "Snapshot", "The " + FaceConfig.LIST_NAMES[listShown()] + " list becomes what the block",
            "on this face holds now (each item once).");
    }

    static boolean shift() {
        return GuiScreen.isShiftKeyDown();
    }

    // ---- building ---------------------------------------------------------------------------------------------------

    Widget add(int x, int y, int w, int h, IntConsumer click) {
        Widget wd = new Widget(x, y, w, h, click);
        widgets.add(wd);
        return wd;
    }

    /** A number with a minus button on its left and a plus on its right (shift: the big step). */
    void stepper(int x, int y, int h, int boxW, int action, int small, int big, Supplier<String> value,
        Supplier<List<String>> tip) {
        Widget minus = add(x, y, 12, h, b -> send(action, -(shift() ? big : small), 0));
        minus.text = () -> "-";
        Widget box = add(x + 12, y, boxW, h, null);
        box.text = value;
        box.flat = true;
        box.wheel = d -> send(action, d * (shift() ? big : small), 0);
        Widget plus = add(x + 12 + boxW, y, 12, h, b -> send(action, shift() ? big : small, 0));
        plus.text = () -> "+";
        minus.tip = tip;
        box.tip = tip;
        plus.tip = tip;
    }

    /** Tabs of 52 × 11, one per list, from x. */
    void addListTabs(int x, int y, int... lists) {
        for (int k = 0; k < lists.length; k++) {
            int list = lists[k];
            Widget t = add(x + 53 * k, y, 52, 11, b -> {
                selected = -1;
                pickList(list);
            });
            t.text = () -> FaceConfig.LIST_NAMES[list] + " " + listAt(list).entries();
            t.on = () -> listShown() == list;
            t.colour = () -> listUsed(list) ? LIST_COLOURS[list] : 0x909090;
            t.tip = () -> listExplain(list);
        }
    }

    /** The list toolbar: 9 buttons of 18 from x 8, all for the list shown. */
    void addToolbar(int y) {
        for (FaceConfig.Kind k : FaceConfig.Kind.values()) {
            Widget w = add(8 + 18 * k.ordinal(), y, 18, 18, b -> send(PipeActionMessage.KIND, k.ordinal(), 0));
            w.icon = () -> k == FaceConfig.Kind.ITEMS ? ICON_ITEMS : k == FaceConfig.Kind.MODS ? ICON_MODS : ICON_EXISTING;
            w.on = () -> fl().kind == k;
            w.visible = () -> toolbarShown() && kindShown(k);
            w.tip = () -> lines(EnumChatFormatting.WHITE + k.words + (fl().kind == k ? " (on)" : ""), k.explain);
        }
        Widget info = add(62, y, 18, 18, null);
        info.icon = () -> LIST_ICONS[listShown()];
        info.flat = true;
        info.visible = this::toolbarShown;
        info.tip = () -> listExplain(listShown());
        Widget damage = add(80, y, 18, 18, b -> send(PipeActionMessage.DAMAGE, b == 1 ? -1 : 1, 0));
        damage.item = () -> new ItemStack(Items.iron_sword, 1, damageShown());
        damage.overlay = this::damageMark;
        damage.visible = this::toolbarShown;
        damage.tip = () -> lines(EnumChatFormatting.WHITE + fl().damage.words, fl().damage.explain,
            EnumChatFormatting.GRAY + "Click: next. Right-click: back.");
        Widget nbt = add(98, y, 18, 18, b -> send(PipeActionMessage.NBT, 0, 0));
        nbt.item = () -> new ItemStack(Items.name_tag);
        nbt.badge = () -> fl().matchNbt ? ICON_CHECK : ICON_CROSS;
        nbt.visible = this::toolbarShown;
        nbt.tip = () -> lines(EnumChatFormatting.WHITE + (fl().matchNbt ? "Match NBT" : "Ignore NBT"),
            fl().matchNbt ? "Enchantments, names and the like must be the same." : "Enchantments, names and the like don't matter.");
        Widget ore = add(116, y, 18, 18, b -> send(PipeActionMessage.ORE, 0, 0));
        ore.item = () -> new ItemStack(Blocks.iron_ore);
        ore.badge = () -> fl().oreDict ? ICON_CHECK : ICON_CROSS;
        ore.visible = this::toolbarShown;
        ore.tip = () -> lines(EnumChatFormatting.WHITE + "Ore dictionary " + (fl().oreDict ? "on" : "off"),
            "On: items sharing an ore dictionary name", "match (any copper ingot for a copper ingot).");
        Widget snap = add(134, y, 18, 18, b -> send(PipeActionMessage.SNAPSHOT, 0, 0));
        snap.icon = () -> ICON_SNAPSHOT;
        snap.visible = () -> toolbarShown() && snapshotShown();
        snap.tip = this::snapshotTip;
        Widget clear = add(152, y, 18, 18, b -> send(PipeActionMessage.CLEAR, 0, 0));
        clear.icon = () -> ICON_CLEAR;
        clear.visible = this::toolbarShown;
        clear.tip = () -> lines(EnumChatFormatting.WHITE + "Clear the " + FaceConfig.LIST_NAMES[listShown()] + " list");
    }

    int damageShown() {
        switch (fl().damage) {
            case D25:
                return 80;
            case D50:
                return 140;
            case D75:
            case DAMAGED:
                return 200;
            default:
                return 0;
        }
    }

    String damageMark() {
        switch (fl().damage) {
            case EXACT:
                return "=";
            case ANY:
                return "*";
            case UNDAMAGED:
                return "0";
            case DAMAGED:
                return "!";
            case D25:
                return "25";
            case D50:
                return "50";
            default:
                return "75";
        }
    }

    static List<String> lines(String... l) {
        return new ArrayList<>(Arrays.asList(l));
    }

    static String number(long n) {
        if (n >= 1_000_000_000L) return String.format("%.1fG", n / 1e9)
            .replace(".0G", "G");
        if (n >= 1_000_000L) return String.format("%.1fM", n / 1e6)
            .replace(".0M", "M");
        if (n >= 100_000) return (n / 1000) + "k";
        if (n >= 10_000) return String.format("%.1fk", n / 1000.0)
            .replace(".0k", "k");
        return String.valueOf(n);
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    static void panel(int x, int y, int w, int h) {
        drawRect(x + 1, y, x + w - 1, y + h, 0xFF000000);
        drawRect(x, y + 1, x + w, y + h - 1, 0xFF000000);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        drawRect(x + 1, y + 1, x + w - 3, y + 3, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 3, y + h - 3, 0xFFFFFFFF);
        drawRect(x + 3, y + h - 3, x + w - 1, y + h - 1, 0xFF555555);
        drawRect(x + w - 3, y + 3, x + w - 1, y + h - 1, 0xFF555555);
    }

    /** A vanilla slot; filter (ghost) slots are a cooler grey, so they don't read as inventory. */
    static void slot(int x, int y, boolean on, boolean ghost) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        drawRect(x, y, x + 17, y + 17, 0xFFFFFFFF);
        drawRect(x, y, x + 16, y + 16, on ? 0xFF9DA8CC : ghost ? 0xFF7C8698 : 0xFF8B8B8B);
    }

    /** The filter's own area: a sunk, blue-grey tinted frame round its buttons and slots. */
    static void filterArea(int x0, int y0, int x1, int y1) {
        drawRect(x0, y0, x1, y1, 0xFF505A6E);
        drawRect(x0 + 1, y0 + 1, x1, y1, 0xFFFFFFFF);
        drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFAEB7C8);
    }

    /** Every slot on screen (ghosts and hidden slots, moved off screen, told apart). */
    void drawSlots() {
        for (Object o : inventorySlots.inventorySlots) {
            Slot s = (Slot) o;
            if (s.xDisplayPosition < -500) continue;
            boolean ghost = s instanceof ContainerPipeFace.Ghost;
            slot(guiLeft + s.xDisplayPosition, guiTop + s.yDisplayPosition, ghost && s.getSlotIndex() == selected, ghost);
        }
    }

    void item(ItemStack s, int x, int y) {
        if (s == null || s.getItem() == null) return;
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), s, x, y);
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glPopMatrix();
        GL11.glColor4f(1, 1, 1, 1);
    }

    void icon(int n, int x, int y) {
        mc.getTextureManager()
            .bindTexture(ICONS);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_BLEND);
        func_146110_a(x, y, n * 16, 0, 16, 16, 512, 16);
    }

    void drawWidgets(int mx, int my) {
        for (Widget w : widgets) if (w.shown()) w.draw(mx, my);
    }

    /** The tooltip of the widget under the mouse, if any (true: drawn). */
    boolean widgetTip(int mx, int my) {
        for (Widget w : widgets) {
            if (w.shown() && w.over(mx, my) && w.tip != null) {
                drawHoveringText(w.tip.get(), mx, my, fontRendererObj);
                return true;
            }
        }
        return false;
    }

    // ---- input -----------------------------------------------------------------------------------------------------

    /** The ghost slot under the mouse (its index in the list), or -1. */
    int ghostAt(int mx, int my) {
        if (!toolbarShown()) return -1;
        for (Object o : inventorySlots.inventorySlots) {
            if (!(o instanceof ContainerPipeFace.Ghost)) continue;
            Slot s = (Slot) o;
            int x = guiLeft + s.xDisplayPosition, y = guiTop + s.yDisplayPosition;
            if (s.xDisplayPosition > -500 && mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) return s.getSlotIndex();
        }
        return -1;
    }

    /** A click on a widget (true: taken). */
    boolean widgetClick(int mx, int my, int button) {
        for (Widget w : widgets) {
            if (w.shown() && w.over(mx, my) && w.click != null) {
                mc.getSoundHandler()
                    .playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                w.click.accept(button);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        if (widgetClick(mx, my, button)) return;
        int g = ghostAt(mx, my);
        if (g >= 0) selected = g;
        super.mouseClicked(mx, my, button);
    }

    /** The mouse wheel: on a widget that takes it. Returns the direction (0: none) for the screen's own use. */
    int wheel() {
        int d = Mouse.getEventDWheel();
        if (d == 0) return 0;
        return d > 0 ? 1 : -1;
    }

    int mouseX() {
        return Mouse.getEventX() * width / mc.displayWidth;
    }

    int mouseY() {
        return height - Mouse.getEventY() * height / mc.displayHeight - 1;
    }

    void wheelWidgets(int mx, int my, int dir) {
        for (Widget w : widgets) if (w.shown() && w.wheel != null && w.over(mx, my)) w.wheel.accept(dir);
    }

    /** An item dropped from NEI's panel: sets the ghost slot under the mouse (no need to own the item). */
    public boolean dropped(int mx, int my, ItemStack stack) {
        int g = ghostAt(mx, my);
        if (g < 0 || stack == null) return false;
        ItemStack one = stack.copy();
        one.stackSize = 1;
        selected = g;
        ValierTweaks.NET.sendToServer(new PipeActionMessage(inventorySlots.windowId, PipeActionMessage.SET_SLOT, g, 0, one, null));
        return true;
    }

    /** Whether NEI's item panel should keep clear of this area (tabs outside the panel). */
    public boolean overTabs(int x, int y, int w, int h) {
        return false;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---- widgets ---------------------------------------------------------------------------------------------------

    /** A button (or a flat label) in vanilla style: an icon, an item or text, a tooltip. */
    final class Widget {

        final int x, y, w, h;
        final IntConsumer click;
        Supplier<Integer> icon;
        Supplier<ItemStack> item;
        Supplier<String> text, overlay;
        Supplier<Integer> badge;
        Supplier<List<String>> tip;
        Supplier<Integer> colour;
        BooleanSupplier visible, on;
        IntConsumer wheel;
        boolean flat;

        Widget(int x, int y, int w, int h, IntConsumer click) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.click = click;
        }

        boolean shown() {
            return visible == null || visible.getAsBoolean();
        }

        boolean over(int mx, int my) {
            return mx >= guiLeft + x && mx < guiLeft + x + w && my >= guiTop + y && my < guiTop + y + h;
        }

        void draw(int mx, int my) {
            int ax = guiLeft + x, ay = guiTop + y;
            boolean hover = over(mx, my) && click != null, pressed = on != null && on.getAsBoolean();
            if (flat) {
                if (text != null) { // a number box, sunk like a slot
                    drawRect(ax, ay, ax + w, ay + h, 0xFF8B8B8B);
                    drawRect(ax, ay, ax + w, ay + 1, 0xFF373737);
                    drawRect(ax, ay, ax + 1, ay + h, 0xFF373737);
                    drawRect(ax, ay + h - 1, ax + w, ay + h, 0xFFFFFFFF);
                    drawRect(ax + w - 1, ay, ax + w, ay + h, 0xFFFFFFFF);
                }
            } else {
                drawRect(ax, ay, ax + w, ay + h, 0xFF000000);
                int fill = pressed ? 0xFF7E8FC2 : hover ? 0xFFBCC5E6 : 0xFFAAAAAA;
                drawRect(ax + 1, ay + 1, ax + w - 1, ay + h - 1, fill);
                drawRect(ax + 1, ay + 1, ax + w - 1, ay + 2, pressed ? 0xFF55618A : 0xFFE8E8E8);
                drawRect(ax + 1, ay + 1, ax + 2, ay + h - 1, pressed ? 0xFF55618A : 0xFFE8E8E8);
                drawRect(ax + 1, ay + h - 2, ax + w - 1, ay + h - 1, pressed ? 0xFFAFC0F0 : 0xFF6A6A6A);
                drawRect(ax + w - 2, ay + 1, ax + w - 1, ay + h - 1, pressed ? 0xFFAFC0F0 : 0xFF6A6A6A);
            }
            int ix = ax + (w >= 18 ? 1 : 0), iy = ay + (h - 16) / 2;
            boolean hasPicture = icon != null || item != null;
            if (icon != null) icon(icon.get(), ix, iy);
            if (item != null) item(item.get(), ix, iy);
            if (badge != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(0, 0, 300);
                icon(badge.get(), ix, iy);
                GL11.glPopMatrix();
            }
            if (overlay != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(0, 0, 300);
                String o = overlay.get();
                fontRendererObj.drawStringWithShadow(o, ax + w - 1 - fontRendererObj.getStringWidth(o), ay + h - 9, 0xFFFFFF);
                GL11.glPopMatrix();
            }
            if (text != null) {
                String t = text.get();
                int tx = hasPicture ? ax + 20 : ax + (w - fontRendererObj.getStringWidth(t) + 1) / 2;
                int colour = hover ? 0xFFFFA0 : this.colour != null ? this.colour.get() : 0xFFFFFF;
                fontRendererObj.drawStringWithShadow(t, tx, ay + (h - 7) / 2, colour);
            }
        }
    }
}
