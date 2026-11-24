package com.valier.tweaks.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.ContainerPipeFace;
import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.FilterList;
import com.valier.tweaks.pipe.PipeActionMessage;
import com.valier.tweaks.pipe.PipeTier;
import com.valier.tweaks.pipe.TilePipe;

/**
 * A pipe face's menu, laid out after EnderIO's conduit screens (vanilla-style panel, icon buttons with tooltips, the
 * pipe's other faces as tabs down the left) but drawn by this mod ({@link ValierScreen}). Everything clicked goes to
 * the server, which sends the settings back.
 * <ul>
 * <li>Top: mode (left-click next, right-click back), redstone, overflow (Provide), priority; per transfer and every N
 * ticks (steppers; the mouse wheel works on them too); the tag.</li>
 * <li>Filter: three lists at once, picked by the tabs (Allow, Deny, Void). Each: item list, mod list or existing items;
 * damage, NBT, ore dictionary; snapshot (fill the list from the block), clear. 18 ghost slots: left-click with an item
 * sets (a stack sets the amount too), right-click clears, the mouse wheel changes the amount (Allow: keep that many in
 * the block; Void: destroy only what is over it); items dragged from NEI set a slot too, so nobody needs to own the
 * item.</li>
 * <li>Pulling faces: the spread (in order, round robin, even split) where the overflow button sits on Provide faces.</li>
 * </ul>
 */
public class GuiPipeFace extends ValierScreen {

    static final String[] SIDES = { "Below", "Above", "North", "South", "West", "East", "Source" };

    final ContainerPipeFace c;
    GuiTextField tag;
    private String typed;

    public GuiPipeFace(ContainerPipeFace c) {
        super(c);
        this.c = c;
        xSize = 176;
        ySize = 240;
    }

    FaceConfig cfg() {
        return c.cfg;
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
        return cfg().list(list);
    }

    @Override
    void pickList(int list) {
        c.list = list;
        send(PipeActionMessage.LIST, list, 0);
    }

    @Override
    boolean kindShown(FaceConfig.Kind k) {
        return k == FaceConfig.Kind.ITEMS || !c.source() && (k == FaceConfig.Kind.MODS || c.list != FaceConfig.VOID);
    }

    @Override
    boolean snapshotShown() {
        return !c.source();
    }

    /** Whether a list does anything in the face's mode (Void: only on faces that pull or are pushed into). */
    @Override
    boolean listUsed(int list) {
        FaceMode m = cfg().mode;
        if (m == FaceMode.OFF) return false;
        if (c.source()) return list == FaceConfig.ALLOW;
        if (list == FaceConfig.VOID) return m.pulls() || m.takesPushes();
        return true;
    }

    /** What a list does in the face's mode, for the tooltips. */
    @Override
    List<String> listExplain(int list) {
        FaceMode m = cfg().mode;
        String what = c.fluid() ? "fluids" : "items";
        List<String> out = new ArrayList<>();
        out.add(EnumChatFormatting.WHITE + FaceConfig.LIST_NAMES[list] + " list (" + cfg().list(list).entries() + ")");
        if (m == FaceMode.OFF) {
            out.add("This face is off.");
            return out;
        }
        if (c.source()) {
            out.add(list == FaceConfig.ALLOW ? "What this creative pipe makes, endlessly." : "Not used by a creative source.");
            return out;
        }
        switch (list) {
            case FaceConfig.ALLOW:
                if (m == FaceMode.REQUEST) {
                    out.add("What to keep stocked in this block:");
                    out.add("each entry's amount (none: a stack, 1,000 mB).");
                } else {
                    out.add("Only the " + what + " it names pass (empty: all).");
                    out.add(m == FaceMode.INSERT ? "An amount: fill the block up to it."
                        : "An amount: leave that many in the block.");
                }
                break;
            case FaceConfig.DENY:
                out.add("The " + what + " it names are never moved or");
                out.add("destroyed by this face. Wins over both other lists.");
                break;
            default:
                if (!listUsed(list)) {
                    out.add("Not used in " + m.words + " mode.");
                    break;
                }
                if (m.pulls()) {
                    out.add(EnumChatFormatting.LIGHT_PURPLE + "Destroys the " + what + " it names in this block,");
                    out.add(EnumChatFormatting.LIGHT_PURPLE + "within each transfer. An amount: leave that many.");
                }
                if (m.takesPushes()) {
                    out.add(EnumChatFormatting.LIGHT_PURPLE + "Destroys the " + what + " it names that are sent");
                    out.add(EnumChatFormatting.LIGHT_PURPLE + "here and don't fit (overflow trash).");
                }
        }
        return out;
    }

    @Override
    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
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
        overflow.visible = () -> cfg().mode == FaceMode.PROVIDE && !c.source();
        overflow.tip = () -> lines(EnumChatFormatting.WHITE + "Overflow: " + (cfg().acceptOverflow ? "on" : "off"),
            "On: this block also takes what pulling faces", "can't put anywhere else (a buffer).");
        Widget spread = add(100, 16, 18, 18, b -> send(PipeActionMessage.SPREAD, b == 1 ? -1 : 1, 0));
        spread.icon = () -> ICON_SPREAD + cfg().spread.ordinal();
        spread.visible = () -> cfg().mode.pulls();
        spread.tip = () -> lines(EnumChatFormatting.WHITE + "Spread: " + cfg().spread.words, cfg().spread.explain + ".",
            EnumChatFormatting.GRAY + "Click: next. Right-click: back.");
        stepper(120, 16, 18, 24, PipeActionMessage.PRIORITY, 1, 10, () -> String.valueOf(cfg().priority),
            () -> lines(EnumChatFormatting.WHITE + "Priority: " + cfg().priority, "Higher fills first; equal ones share",
                "by the pulling face's spread setting.",
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
        tag = new GuiTextField(fontRendererObj, guiLeft + 27, guiTop + 58, 141, 12);
        tag.setMaxStringLength(FaceConfig.MAX_TAG);
        tag.setText(cfg().tag);
        typed = cfg().tag;
        Widget tagIcon = add(8, 56, 16, 16, null);
        tagIcon.icon = () -> ICON_TAG;
        tagIcon.flat = true;
        tagIcon.tip = () -> lines(EnumChatFormatting.WHITE + "Tag" + (cfg().tag.isEmpty() ? " (none)" : ": " + cfg().tag),
            "Faces only trade with faces of the same tag.", PipeTier.wireless(c.pipe.tier())
                ? "Wireless: links to your team's Wireless pipes" : "Within the pipes joined together; with a",
            PipeTier.wireless(c.pipe.tier()) ? "with the same tag, anywhere." : "Wireless pipe joined in, it links like one.");
        // The three lists, as tabs over the filter, and the toolbar for the one shown.
        addListTabs(8, 73, FaceConfig.ALLOW, FaceConfig.DENY, FaceConfig.VOID);
        addToolbar(85);
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

    int perMove() {
        return cfg().amount <= 0 ? c.max : Math.min(cfg().amount, c.max);
    }

    /** Whether the list shown takes amounts (Allow and Void item lists). */
    boolean amounts() {
        return c.list != FaceConfig.DENY && fl().kind == FaceConfig.Kind.ITEMS && !c.source();
    }

    boolean amountRow() {
        return amounts() && selected >= 0 && fl().filter[selected] != null;
    }

    // ---- the pipe's other faces, as tabs ----------------------------------------------------------------------------

    List<Integer> faces() {
        List<Integer> out = new ArrayList<>();
        TilePipe p = c.pipe;
        int conn = p.renderState & 0xFFF;
        for (int s = 0; s < 6; s++) if ((conn >> (6 + s) & 1) != 0) out.add(s);
        if (!out.contains(c.side) && !c.source()) out.add(0, c.side);
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
    @Override
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
        drawSlots();
        drawWidgets(mx, my);
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

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        String title = PipeTier.NAMES[c.pipe.tier()] + (c.fluid() ? " Fluid" : " Item") + " Pipe";
        fontRendererObj.drawString(title, 8, 5, TEXT);
        String face = c.source() ? "Source (endless)" : SIDES[c.side] + " face";
        fontRendererObj.drawString(face, xSize - 8 - fontRendererObj.getStringWidth(face), 5, 0x707070);
        fontRendererObj.drawString(c.fluid() ? "mB" : "Move", 8, 42, TEXT);
        fontRendererObj.drawString("Every", 87, 42, TEXT);
        if (amountRow()) {
            int k = fl().count[selected];
            String unit = c.fluid() ? " mB" : "";
            String s = c.list == FaceConfig.VOID ? (k > 0 ? "Void over " + number(k) + unit : "Void all of it")
                : k > 0 ? "Keep " + number(k) + unit + " here" : "No amount (slot " + (selected + 1) + ")";
            fontRendererObj.drawString(s, 8, 147, TEXT);
        } else if (!listUsed(c.list)) {
            fontRendererObj.drawString(FaceConfig.LIST_NAMES[c.list] + ": not used in " + cfg().mode.words + " mode", 8, 147, 0x806060);
        } else if (fl().kind == FaceConfig.Kind.EXISTING) {
            fontRendererObj.drawString("Matches what the block holds", 8, 147, 0x606060);
        } else {
            fontRendererObj.drawString(c.netState.isEmpty() ? "Inventory" : c.netState, 8, 147, 0x606060);
        }
        // Amounts on the filter slots, as stack sizes are drawn.
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        for (int i = 0; i < FaceConfig.FILTER; i++) {
            if (!amounts() || fl().filter[i] == null || fl().count[i] <= 0) continue;
            String n = number(fl().count[i]);
            int x = ContainerPipeFace.FILTER_X + (i % 9) * 18, y = ContainerPipeFace.FILTER_Y + (i / 9) * 18;
            GL11.glPushMatrix();
            GL11.glTranslatef(0, 0, 300);
            fontRendererObj.drawStringWithShadow(n, x + 17 - fontRendererObj.getStringWidth(n), y + 9, AMOUNT_COLOURS[c.list]);
            GL11.glPopMatrix();
        }
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        if (widgetTip(mx, my)) return;
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
        if (slot >= 0 && fl().filter[slot] == null && mc.thePlayer.inventory.getItemStack() == null) {
            drawHoveringText(lines(EnumChatFormatting.WHITE + FaceConfig.LIST_NAMES[c.list] + " slot " + (slot + 1), "Click with an item (or drag one from NEI)",
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
        int k = fl().count[slot];
        String unit = c.fluid() ? " mB" : "";
        if (c.list == FaceConfig.DENY) list.add(EnumChatFormatting.RED + "Denied: never moved or destroyed here");
        else if (amounts()) list.add(c.list == FaceConfig.VOID
            ? EnumChatFormatting.LIGHT_PURPLE + (k > 0 ? "Destroys what is over " + k + unit : "Destroys all of it")
            : EnumChatFormatting.YELLOW + (k > 0 ? "Keep " + k + unit + " in the block" : "No amount"));
        list.add(EnumChatFormatting.GRAY + (amounts() ? "Right-click: clear. Wheel: amount (shift: more)." : "Right-click: clear."));
        drawHoveringText(list, x, y, fontRendererObj);
    }

    // ---- input -----------------------------------------------------------------------------------------------------

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
        if (widgetClick(mx, my, button)) return;
        int g = ghostAt(mx, my);
        if (g >= 0) selected = g;
        tag.mouseClicked(mx, my, button);
        super.mouseClicked(mx, my, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dir = wheel();
        if (dir == 0) return;
        int mx = mouseX(), my = mouseY();
        int g = ghostAt(mx, my);
        if (g >= 0 && fl().filter[g] != null && amounts()) {
            selected = g;
            int step = c.fluid() ? (shift() ? 1000 : 100) : (shift() ? 16 : 1);
            send(PipeActionMessage.COUNT, g, dir * step);
            return;
        }
        wheelWidgets(mx, my, dir);
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
}
