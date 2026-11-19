package com.valier.tweaks.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.ContainerPipeFace;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.PipeActionMessage;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;

/**
 * A pipe face's menu, laid out after EnderIO's conduit screens (vanilla-style panel, icon buttons with tooltips, the
 * pipe's other faces as tabs down the left) but drawn by this mod: rectangles and its own icon strip
 * ({@code textures/gui/pipe_icons.png}), plus vanilla item icons. Everything clicked goes to the server, which sends
 * the settings back.
 * <ul>
 * <li>Top: mode (left-click next, right-click back), redstone, overflow (Provide), priority; per transfer and every N
 * ticks (steppers; the mouse wheel works on them too); the tag.</li>
 * <li>Filter: item list, mod list or existing items; whitelist/blacklist, damage, NBT, ore dictionary; snapshot (fill
 * the list from the block), clear. 18 ghost slots: left-click with an item sets (a stack sets the amount too),
 * right-click clears, the mouse wheel changes the amount (keep that many in the block); items dragged from NEI set a
 * slot too, so nobody needs to own the item.</li>
 * </ul>
 */
public class GuiPipeFace extends GuiContainer {

    static final ResourceLocation ICONS = new ResourceLocation(ValierTweaks.MODID, "textures/gui/pipe_icons.png");
    // Icon strip positions (tools/gui_icons.py).
    static final int ICON_OFF = 0, ICON_WHITE = 6, ICON_BLACK = 7, ICON_CLEAR = 8, ICON_CHECK = 9, ICON_CROSS = 10,
        ICON_ITEMS = 11, ICON_MODS = 12, ICON_EXISTING = 13, ICON_SNAPSHOT = 14, ICON_TAG = 15;
    static final int TEXT = 0x404040;
    static final String[] SIDES = { "Below", "Above", "North", "South", "West", "East" };

    final ContainerPipeFace c;
    final List<Widget> widgets = new ArrayList<>();
    GuiTextField tag;
    private String typed;
    int selected = -1;

    public GuiPipeFace(ContainerPipeFace c) {
        super(c);
        this.c = c;
        xSize = 176;
        ySize = 240;
    }

    FaceConfig cfg() {
        return c.cfg;
    }

    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
    }

    static boolean shift() {
        return GuiScreen.isShiftKeyDown();
    }

    // ---- layout -------------------------------------------------------------------------------------------------------

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        boolean fluid = c.fluid();
        // Row 1: mode, redstone, overflow, priority.
        Widget mode = add(8, 16, 70, 18, b -> send(PipeActionMessage.MODE, b == 1 ? -1 : 1, 0));
        mode.icon = () -> cfg().mode.ordinal();
        mode.text = () -> cfg().mode.words;
        mode.tip = () -> lines(EnumChatFormatting.WHITE + "Mode: " + cfg().mode.words, cfg().mode.explain,
            EnumChatFormatting.GRAY + "Click: next. Right-click: back.");
        Widget redstone = add(80, 16, 18, 18, b -> send(PipeActionMessage.REDSTONE, b == 1 ? -1 : 1, 0));
        redstone.item = () -> new ItemStack(cfg().redstone == FaceConfig.ALWAYS ? Items.redstone
            : Item.getItemFromBlock(cfg().redstone == FaceConfig.WITH_SIGNAL ? Blocks.redstone_torch : Blocks.unlit_redstone_torch));
        redstone.tip = () -> lines(EnumChatFormatting.WHITE + "Redstone: " + (cfg().redstone == FaceConfig.ALWAYS ? "ignored"
            : cfg().redstone == FaceConfig.WITH_SIGNAL ? "works only with a signal" : "works only without a signal"),
            "A signal on the pipe itself.");
        Widget overflow = add(100, 16, 18, 18, b -> send(PipeActionMessage.OVERFLOW, 0, 0));
        overflow.item = () -> new ItemStack(Blocks.hopper);
        overflow.badge = () -> cfg().acceptOverflow ? ICON_CHECK : ICON_CROSS;
        overflow.visible = () -> cfg().mode == FaceMode.PROVIDE;
        overflow.tip = () -> lines(EnumChatFormatting.WHITE + "Overflow: " + (cfg().acceptOverflow ? "on" : "off"),
            "On: this block also takes what pulling faces", "can't put anywhere else (a buffer).");
        stepper(120, 16, 18, 24, PipeActionMessage.PRIORITY, 1, 10, () -> String.valueOf(cfg().priority),
            () -> lines(EnumChatFormatting.WHITE + "Priority: " + cfg().priority, "Higher goes first; equal ones take turns.",
                EnumChatFormatting.GRAY + "Shift: by 10. Mouse wheel works too."));
        // Row 2: per transfer, every N ticks.
        stepper(33, 38, 16, 26, PipeActionMessage.AMOUNT, fluid ? 100 : 1, fluid ? 1000 : 16, () -> number(perMove()),
            () -> lines(EnumChatFormatting.WHITE + "Per transfer: " + perMove() + (fluid ? " mB" : " items"),
                "At most " + c.max + (fluid ? " mB" : "") + " on this tier.", EnumChatFormatting.GRAY + "Shift: by "
                    + (fluid ? "1,000" : "16") + ". Mouse wheel works too."));
        stepper(118, 38, 16, 26, PipeActionMessage.TICKS, 1, 20, () -> cfg().interval(c.pipe.tier()) + "t",
            () -> lines(EnumChatFormatting.WHITE + "Every " + cfg().interval(c.pipe.tier()) + " ticks", "Fastest on this tier: every "
                + c.minTicks + ". Slower costs the server less.", EnumChatFormatting.GRAY + "Shift: by 20 (a second)."));
        // Row 3: the tag.
        tag = new GuiTextField(fontRendererObj, guiLeft + 27, guiTop + 59, 141, 12);
        tag.setMaxStringLength(FaceConfig.MAX_TAG);
        tag.setText(cfg().tag);
        typed = cfg().tag;
        Widget tagIcon = add(8, 57, 16, 16, null);
        tagIcon.icon = () -> ICON_TAG;
        tagIcon.flat = true;
        tagIcon.tip = () -> lines(EnumChatFormatting.WHITE + "Tag" + (cfg().tag.isEmpty() ? " (none)" : ": " + cfg().tag),
            "Faces only trade with faces of the same tag.", c.pipe.tier() == PipeTier.WIRELESS
                ? "Wireless: links to your team's Wireless pipes" : "Within the pipes joined together (channels).",
            c.pipe.tier() == PipeTier.WIRELESS ? "with the same tag, anywhere." : "Enter (or closing) saves it.");
        // Filter toolbar: 9 buttons of 18.
        for (FaceConfig.Kind k : FaceConfig.Kind.values()) {
            Widget w = add(8 + 18 * k.ordinal(), 85, 18, 18, b -> send(PipeActionMessage.KIND, k.ordinal(), 0));
            w.icon = () -> k == FaceConfig.Kind.ITEMS ? ICON_ITEMS : k == FaceConfig.Kind.MODS ? ICON_MODS : ICON_EXISTING;
            w.on = () -> cfg().kind == k;
            w.tip = () -> lines(EnumChatFormatting.WHITE + k.words + (cfg().kind == k ? " (on)" : ""), k.explain);
        }
        Widget black = add(62, 85, 18, 18, b -> send(PipeActionMessage.BLACKLIST, 0, 0));
        black.icon = () -> cfg().blacklist ? ICON_BLACK : ICON_WHITE;
        black.tip = () -> cfg().blacklist ? lines(EnumChatFormatting.WHITE + "Blacklist", "Everything except what matches.")
            : lines(EnumChatFormatting.WHITE + "Whitelist", "Only what matches (an empty list lets all through).");
        Widget damage = add(80, 85, 18, 18, b -> send(PipeActionMessage.DAMAGE, b == 1 ? -1 : 1, 0));
        damage.item = () -> new ItemStack(Items.iron_sword, 1, damageShown());
        damage.overlay = this::damageMark;
        damage.tip = () -> lines(EnumChatFormatting.WHITE + cfg().damage.words, cfg().damage.explain,
            EnumChatFormatting.GRAY + "Click: next. Right-click: back.");
        Widget nbt = add(98, 85, 18, 18, b -> send(PipeActionMessage.NBT, 0, 0));
        nbt.item = () -> new ItemStack(Items.name_tag);
        nbt.badge = () -> cfg().matchNbt ? ICON_CHECK : ICON_CROSS;
        nbt.tip = () -> lines(EnumChatFormatting.WHITE + (cfg().matchNbt ? "Match NBT" : "Ignore NBT"),
            cfg().matchNbt ? "Enchantments, names and the like must be the same." : "Enchantments, names and the like don't matter.");
        Widget ore = add(116, 85, 18, 18, b -> send(PipeActionMessage.ORE, 0, 0));
        ore.item = () -> new ItemStack(Blocks.iron_ore);
        ore.badge = () -> cfg().oreDict ? ICON_CHECK : ICON_CROSS;
        ore.tip = () -> lines(EnumChatFormatting.WHITE + "Ore dictionary " + (cfg().oreDict ? "on" : "off"),
            "On: items sharing an ore dictionary name", "match (any copper ingot for a copper ingot).");
        Widget snap = add(134, 85, 18, 18, b -> send(PipeActionMessage.SNAPSHOT, 0, 0));
        snap.icon = () -> ICON_SNAPSHOT;
        snap.tip = () -> lines(EnumChatFormatting.WHITE + "Snapshot", "The list becomes what the block on this face", "holds now (each item once).");
        Widget clear = add(152, 85, 18, 18, b -> send(PipeActionMessage.CLEAR, 0, 0));
        clear.icon = () -> ICON_CLEAR;
        clear.tip = () -> lines(EnumChatFormatting.WHITE + "Clear the list");
        // The selected slot's amount.
        int[] steps = fluid ? new int[] { -1000, -100, 100, 1000 } : new int[] { -16, -1, 1, 16 };
        for (int i = 0; i < 4; i++) {
            int step = steps[i];
            Widget w = add(96 + 18 * i, 144, 18, 13, b -> {
                if (selected >= 0) send(PipeActionMessage.COUNT, selected, step);
            });
            w.text = () -> (step > 0 ? "+" : "") + (Math.abs(step) >= 1000 ? (step / 1000) + "k" : String.valueOf(step));
            w.visible = this::amountRow;
        }
        Keyboard.enableRepeatEvents(true);
    }

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

    int perMove() {
        return cfg().amount <= 0 ? c.max : Math.min(cfg().amount, c.max);
    }

    boolean amountRow() {
        return cfg().kind == FaceConfig.Kind.ITEMS && selected >= 0 && cfg().filter[selected] != null;
    }

    int damageShown() {
        switch (cfg().damage) {
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
        switch (cfg().damage) {
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

    static String number(int n) {
        if (n >= 100_000) return (n / 1000) + "k";
        if (n >= 10_000) return String.format("%.1fk", n / 1000.0)
            .replace(".0k", "k");
        return String.valueOf(n);
    }

    // ---- the pipe's other faces, as tabs ----------------------------------------------------------------------------

    List<Integer> faces() {
        List<Integer> out = new ArrayList<>();
        TilePipe p = c.pipe;
        int conn = p.renderState & 0xFFF;
        for (int s = 0; s < 6; s++) if ((conn >> (6 + s) & 1) != 0) out.add(s);
        if (!out.contains(c.side)) out.add(0, c.side);
        return out;
    }

    int tabY(int k) {
        return guiTop + 6 + k * 24;
    }

    ItemStack blockIcon(int s) {
        ForgeDirection d = ForgeDirection.getOrientation(s);
        int x = c.pipe.xCoord + d.offsetX, y = c.pipe.yCoord + d.offsetY, z = c.pipe.zCoord + d.offsetZ;
        Block b = mc.theWorld.getBlock(x, y, z);
        Item item = b == null ? null : Item.getItemFromBlock(b);
        if (item == null) return new ItemStack(Blocks.chest);
        return new ItemStack(item, 1, b.getDamageValue(mc.theWorld, x, y, z));
    }

    FaceMode modeOf(int s) {
        return FaceMode.of(c.pipe.renderState >> (12 + 3 * s) & 7);
    }

    /** NEI's item panel stays clear of the tabs. */
    public boolean overTabs(int x, int y, int w, int h) {
        int tx = guiLeft - 24, ty = guiTop + 6, th = faces().size() * 24;
        return x < tx + 24 && x + w > tx && y < ty + th && y + h > ty;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------------

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        List<Integer> faces = faces();
        for (int k = 0; k < faces.size(); k++) if (faces.get(k) != c.side) tab(k, faces.get(k), false);
        panel(guiLeft, guiTop, xSize, ySize);
        for (int k = 0; k < faces.size(); k++) if (faces.get(k) == c.side) tab(k, faces.get(k), true);
        filterArea(guiLeft + 5, guiTop + 84, guiLeft + xSize - 5, guiTop + 143);
        for (Object o : c.inventorySlots) {
            net.minecraft.inventory.Slot s = (net.minecraft.inventory.Slot) o;
            slot(guiLeft + s.xDisplayPosition, guiTop + s.yDisplayPosition,
                s.slotNumber == selected && s.slotNumber < FaceConfig.FILTER, s.slotNumber < FaceConfig.FILTER);
        }
        for (Widget w : widgets) if (w.shown()) w.draw(mx, my);
        tag.drawTextBox();
    }

    void tab(int k, int s, boolean on) {
        int x = guiLeft - 22, y = tabY(k);
        drawRect(x, y, x + 24, y + 22, 0xFF000000);
        drawRect(x + 1, y + 1, x + 24, y + 21, on ? 0xFFC6C6C6 : 0xFFA4A4A4);
        drawRect(x + 1, y + 1, x + 24, y + 2, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 2, y + 21, 0xFFFFFFFF);
        if (on) drawRect(x + 22, y + 1, x + 25, y + 21, 0xFFC6C6C6); // joins the panel
        FaceMode m = modeOf(s);
        drawRect(x + 2, y + 18, x + 21, y + 20, 0xFF000000 | (m == FaceMode.OFF ? 0x707070 : m.colour));
        item(blockIcon(s), x + 4, y + 2);
    }

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
        func_146110_a(x, y, n * 16, 0, 16, 16, 256, 16);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        String title = PipeTier.NAMES[c.pipe.tier()] + (c.fluid() ? " Fluid" : " Item") + " Pipe";
        fontRendererObj.drawString(title, 8, 5, TEXT);
        String face = SIDES[c.side] + " face";
        fontRendererObj.drawString(face, xSize - 8 - fontRendererObj.getStringWidth(face), 5, 0x707070);
        fontRendererObj.drawString(c.fluid() ? "mB" : "Move", 8, 42, TEXT);
        fontRendererObj.drawString("Filter", 8, 75, TEXT);
        String sum = cfg().kind.words + (cfg().kind == FaceConfig.Kind.EXISTING ? "" : cfg().blacklist ? ", blacklist" : ", whitelist");
        fontRendererObj.drawString(sum, xSize - 8 - fontRendererObj.getStringWidth(sum), 75, 0x606060);
        fontRendererObj.drawString("Every", 87, 42, TEXT);
        if (amountRow()) {
            int k = cfg().count[selected];
            String s = k > 0 ? "Keep " + number(k) + (c.fluid() ? " mB" : "") + " here" : "No amount (slot " + (selected + 1) + ")";
            fontRendererObj.drawString(s, 8, 147, TEXT);
        } else if (cfg().kind == FaceConfig.Kind.EXISTING) {
            fontRendererObj.drawString("Matches what the block holds", 8, 147, 0x606060);
        } else {
            fontRendererObj.drawString(c.netState.isEmpty() ? "Inventory" : c.netState, 8, 147, 0x606060);
        }
        // Amounts on the filter slots, as stack sizes are drawn.
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            if (cfg().filter[i] == null || cfg().count[i] <= 0 || cfg().kind != FaceConfig.Kind.ITEMS) continue;
            String n = number(cfg().count[i]);
            int x = ContainerPipeFace.FILTER_X + (i % 9) * 18, y = ContainerPipeFace.FILTER_Y + (i / 9) * 18;
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 300);
            fontRendererObj.drawStringWithShadow(n, x + 17 - fontRendererObj.getStringWidth(n), y + 9, 0xFFFF60);
            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        for (Widget w : widgets) {
            if (w.shown() && w.over(mx, my) && w.tip != null) {
                drawHoveringText(w.tip.get(), mx, my, fontRendererObj);
                return;
            }
        }
        List<Integer> faces = faces();
        for (int k = 0; k < faces.size(); k++) {
            int x = guiLeft - 22, y = tabY(k);
            if (mx >= x && mx < x + 22 && my >= y && my < y + 22) {
                int s = faces.get(k);
                ItemStack b = blockIcon(s);
                drawHoveringText(lines(EnumChatFormatting.WHITE + SIDES[s] + ": " + b.getDisplayName(), "Mode: " + modeOf(s).words
                    + (s == c.side ? " (open)" : ""), EnumChatFormatting.GRAY + (s == c.side ? "" : "Click to set up this face.")), mx, my,
                    fontRendererObj);
                return;
            }
        }
        int slot = ghostAt(mx, my);
        if (slot >= 0 && cfg().filter[slot] == null && mc.thePlayer.inventory.getItemStack() == null) {
            drawHoveringText(lines(EnumChatFormatting.WHITE + "Filter slot " + (slot + 1), "Click with an item (or drag one from NEI)",
                "to set it; a stack sets the amount too.", EnumChatFormatting.GRAY + "Right-click clears. Wheel: amount."), mx, my,
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
        if (cfg().kind == FaceConfig.Kind.ITEMS) list.add(EnumChatFormatting.YELLOW + (cfg().count[slot] > 0
            ? "Keep " + cfg().count[slot] + (c.fluid() ? " mB" : "") + " in the block" : "No amount"));
        list.add(EnumChatFormatting.GRAY + "Right-click: clear. Wheel: amount (shift: more).");
        drawHoveringText(list, x, y, fontRendererObj);
    }

    // ---- input -----------------------------------------------------------------------------------------------------

    int ghostAt(int mx, int my) {
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            int x = guiLeft + ContainerPipeFace.FILTER_X + (i % 9) * 18, y = guiTop + ContainerPipeFace.FILTER_Y + (i / 9) * 18;
            if (mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) return i;
        }
        return -1;
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        List<Integer> faces = faces();
        for (int k = 0; k < faces.size(); k++) {
            int x = guiLeft - 22, y = tabY(k);
            if (mx >= x && mx < x + 22 && my >= y && my < y + 22) {
                if (faces.get(k) != c.side) {
                    sendTag();
                    send(PipeActionMessage.FACE, faces.get(k), 0);
                }
                return;
            }
        }
        for (Widget w : widgets) {
            if (w.shown() && w.over(mx, my) && w.click != null) {
                mc.getSoundHandler()
                    .playSound(net.minecraft.client.audio.PositionedSoundRecord.func_147674_a(new ResourceLocation("gui.button.press"), 1.0F));
                w.click.accept(button);
                return;
            }
        }
        int g = ghostAt(mx, my);
        if (g >= 0) selected = g;
        tag.mouseClicked(mx, my, button);
        super.mouseClicked(mx, my, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d == 0) return;
        int dir = d > 0 ? 1 : -1;
        int mx = Mouse.getEventX() * width / mc.displayWidth, my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        int g = ghostAt(mx, my);
        if (g >= 0 && cfg().filter[g] != null && cfg().kind == FaceConfig.Kind.ITEMS) {
            selected = g;
            int step = c.fluid() ? (shift() ? 1000 : 100) : (shift() ? 16 : 1);
            send(PipeActionMessage.COUNT, g, dir * step);
            return;
        }
        for (Widget w : widgets) if (w.shown() && w.wheel != null && w.over(mx, my)) w.wheel.accept(dir);
    }

    @Override
    protected void keyTyped(char ch, int key) {
        if (tag.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                sendTag();
                tag.setFocused(false);
                return;
            }
            if (key == Keyboard.KEY_ESCAPE) {
                tag.setFocused(false);
                return;
            }
            tag.textboxKeyTyped(ch, key);
            return;
        }
        super.keyTyped(ch, key);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        tag.updateCursorCounter();
        if (!tag.isFocused() && !tag.getText()
            .equals(cfg().tag) && typed.equals(tag.getText())) {
            tag.setText(cfg().tag);
            typed = cfg().tag;
        }
    }

    void sendTag() {
        String t = FaceConfig.cleanTag(tag.getText());
        if (!t.equals(cfg().tag)) ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, PipeActionMessage.TAG, 0, 0, null, t));
        typed = tag.getText();
    }

    @Override
    public void onGuiClosed() {
        sendTag();
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** An item dropped from NEI's panel: sets the filter slot under the mouse (no need to own the item). */
    public boolean dropped(int mx, int my, ItemStack stack) {
        int g = ghostAt(mx, my);
        if (g < 0 || stack == null) return false;
        ItemStack one = stack.copy();
        one.stackSize = 1;
        selected = g;
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, PipeActionMessage.SET_SLOT, g, 0, one, null));
        return true;
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
                int colour = hover ? 0xFFFFA0 : 0xFFFFFF;
                fontRendererObj.drawStringWithShadow(t, tx, ay + (h - 7) / 2, colour);
            }
        }
    }
}
