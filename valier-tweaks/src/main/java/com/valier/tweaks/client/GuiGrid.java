package com.valier.tweaks.client;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.grid.GridMenu;
import com.valier.tweaks.grid.GridTier;
import com.valier.tweaks.grid.TileGrid;
import com.valier.tweaks.pipe.PipeActionMessage;

/** A Grid block's menu ({@link GridMenu}). */
public class GuiGrid extends ValierScreen {

    static final String[] KINDS = { "Grid Plug", "Grid Point", "Grid Cell", "Grid Controller" };
    final GridMenu c;
    GuiTextField name;
    private String shownNet = null;
    /** The first block the network page shows (it scrolls). */
    int listTop;
    static final int ROWS = 5;
    static final String[] SHORT = { "Plug", "Point", "Cell", "Ctrl" };

    public GuiGrid(GridMenu c) {
        super(c);
        this.c = c;
        xSize = 176;
        ySize = 196;
    }

    NBTTagCompound s() {
        return c.state;
    }

    int kind() {
        return s().getInteger("Kind");
    }

    @Override
    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
    }

    void sendText(int action, String text) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, 0, 0, null, text));
    }

    boolean linkedToTyped() {
        String n = s().getString("Net");
        return !n.isEmpty() && n.equalsIgnoreCase(name.getText()
            .trim());
    }

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        name = new GuiTextField(fontRendererObj, guiLeft + 9, guiTop + 29, 108, 12);
        name.setMaxStringLength(32);
        name.setText(s().getString("Net"));
        Widget link = add(122, 27, 46, 16, b -> {
            if (linkedToTyped()) send(GridMenu.UNLINK, 0, 0);
            else sendText(GridMenu.NET, name.getText());
        });
        link.text = () -> linkedToTyped() ? "Unlink" : "Link";
        link.tip = () -> lines(EnumChatFormatting.WHITE + (linkedToTyped() ? "Unlink" : "Link to \"" + name.getText() + "\""),
            "Blocks on one network (your team's) share", "energy, anywhere, while their chunks are loaded.");
        // The network's colour: a swatch to click through the 16 dye colours.
        Widget colour = add(122, 12, 46, 13, b -> send(GridMenu.COLOUR, b == 1 ? -1 : 1, 0));
        colour.visible = () -> !s().getString("Net")
            .isEmpty();
        colour.tip = () -> lines(EnumChatFormatting.WHITE + "Network colour: " + s().getString("ColourName"),
            "Every block of the network shows it.", EnumChatFormatting.GRAY + "Click: next. Right-click: back.");
        for (int i = 0; i < 3; i++) {
            int k = i;
            Widget row = add(8, 46 + 10 * i, 160, 10, b -> {
                if (k < c.networks.size()) name.setText(c.networks.get(k)[0]);
            });
            row.flat = true;
            row.visible = () -> k < c.networks.size();
            row.text = () -> k < c.networks.size() ? c.networks.get(k)[0] + " (" + c.networks.get(k)[1] + " blocks)" : "";
            row.colour = () -> k < c.networks.size() && c.networks.get(k)[0].equalsIgnoreCase(s().getString("Net")) ? 0xE6A0FF : 0xFFFFFF;
            row.tip = () -> lines(EnumChatFormatting.GRAY + "Click to pick it.");
        }
        stepper(8, 80, 18, 30, GridMenu.PRIORITY, 1, 10, () -> String.valueOf(s().getInteger("Priority")),
            () -> lines(EnumChatFormatting.WHITE + "Priority " + s().getInteger("Priority"), "Higher points (and cells) go first;",
                "equal ones share by what they want.", EnumChatFormatting.GRAY + "Shift: by 10. Wheel works too."));
        Widget surge = add(76, 80, 18, 18, b -> send(GridMenu.SURGE, 0, 0));
        surge.item = () -> new ItemStack(Items.glowstone_dust);
        surge.badge = () -> s().getBoolean("Surge") ? ICON_CHECK : ICON_CROSS;
        surge.tip = () -> lines(EnumChatFormatting.WHITE + "Surge " + (s().getBoolean("Surge") ? "on" : "off"), "On: before every priority.");
        Widget redstone = add(96, 80, 18, 18, b -> send(GridMenu.REDSTONE, b == 1 ? -1 : 1, 0));
        redstone.item = () -> new ItemStack(s().getInteger("Redstone") == TileGrid.ALWAYS ? Items.redstone
            : Item.getItemFromBlock(s().getInteger("Redstone") == TileGrid.WITH_SIGNAL ? Blocks.redstone_torch : Blocks.unlit_redstone_torch));
        redstone.tip = () -> lines(EnumChatFormatting.WHITE + "Redstone: " + new String[] { "ignored", "works only with a signal",
            "works only without a signal" }[Math.max(0, Math.min(2, s().getInteger("Redstone")))]);
        Widget volts = add(116, 80, 52, 18, b -> send(GridMenu.VOLTS, b == 1 ? -1 : 1, 0));
        volts.text = () -> number(s().getLong("Volts")) + " V";
        volts.visible = () -> kind() == TileGrid.POINT;
        volts.tip = () -> lines(EnumChatFormatting.WHITE + "Voltage for cables: " + String.format("%,d V", s().getLong("Volts")),
            "GregTech machines always get their own", "voltage; cables get this one (it burns", "a cable rated lower).",
            EnumChatFormatting.GRAY + "Click: up. Right-click: down.");
        Widget charge = add(116, 80, 52, 18, b -> send(GridMenu.CHARGE_ME, 0, 0));
        charge.text = () -> s().getBoolean("ChargeMe") ? "Charging" : "Not me";
        charge.visible = () -> kind() == TileGrid.CONTROLLER;
        charge.tip = () -> lines(EnumChatFormatting.WHITE + (s().getBoolean("ChargeMe") ? "It charges your items" : "It leaves your items"),
            "Click to switch (for you only).");
        // The limit: steps of one amp of its tier (shift: 16), worked out when clicked (the tier comes from the server).
        java.util.function.Supplier<java.util.List<String>> limitTip = () -> lines(EnumChatFormatting.WHITE + "Limit: "
            + String.format("%,d EU/t", s().getLong("Limit")), "At most this much a tick through it.",
            EnumChatFormatting.GRAY + "Steps of one amp; shift: 16 amps. Wheel works too.");
        Widget minus = add(8, 104, 12, 16, b -> send(GridMenu.LIMIT, -limitStep(), 0));
        minus.text = () -> "-";
        minus.tip = limitTip;
        Widget box = add(20, 104, 60, 16, null);
        box.flat = true;
        box.text = () -> s().getBoolean("Capped") ? number(s().getLong("Limit")) : "max";
        box.wheel = d -> send(GridMenu.LIMIT, d * limitStep(), 0);
        box.tip = limitTip;
        Widget plus = add(80, 104, 12, 16, b -> send(GridMenu.LIMIT, limitStep(), 0));
        plus.text = () -> "+";
        plus.tip = limitTip;
        // The network page: every block of it, with place and status.
        Widget page = add(122, 104, 46, 16, b -> {
            listTop = 0;
            send(GridMenu.BLOCKS, 0, 0);
        });
        page.text = () -> listing() ? "Back" : "Blocks";
        page.visible = () -> !s().getString("Net")
            .isEmpty();
        page.tip = () -> lines(EnumChatFormatting.WHITE + (listing() ? "Back to this block" : "The network's blocks"),
            listing() ? "" : "Where each one is, what it moves,", listing() ? "" : "and whether redstone has it off.");
        for (int i = 0; i < ROWS; i++) {
            int r = i;
            Widget row = add(8, 137 + 10 * i, 160, 10, null);
            row.flat = true;
            row.visible = () -> listing() && listTop + r < c.blocks.size();
            row.wheel = d -> scroll(-d);
            row.tip = () -> blockTip(c.blocks.get(Math.min(c.blocks.size() - 1, listTop + r)));
        }
        Keyboard.enableRepeatEvents(true);
    }

    boolean listing() {
        return s().getBoolean("Listing") && !s().getString("Net")
            .isEmpty();
    }

    void scroll(int d) {
        listTop = Math.max(0, Math.min(Math.max(0, c.blocks.size() - ROWS), listTop + d));
    }

    static String kindName(NBTTagCompound e) {
        int k = Math.max(0, Math.min(3, e.getByte("K")));
        return KINDS[k] + (k == TileGrid.CONTROLLER ? "" : " (" + GridTier.NAMES[GridTier.clamp(e.getByte("T"))] + ")");
    }

    java.util.List<String> blockTip(NBTTagCompound e) {
        java.util.List<String> l = lines(EnumChatFormatting.WHITE + kindName(e) + (e.getBoolean("Here") ? EnumChatFormatting.GRAY + " (this one)" : ""),
            String.format("At %d %d %d in %s", e.getInteger("X"), e.getInteger("Y"), e.getInteger("Z"), e.getString("DN")),
            String.format("Moved %,d EU/s", e.getLong("M")));
        if (e.hasKey("C")) l.add(String.format("Stored %,d / %,d EU", e.getLong("S"), e.getLong("C")));
        if (e.getBoolean("Surge")) l.add("Surge: before every priority");
        else if (e.hasKey("P")) l.add("Priority " + e.getInteger("P"));
        if (e.getBoolean("Off")) l.add(EnumChatFormatting.RED + "Off: its redstone setting");
        return l;
    }

    /** The network page, in place of this block's numbers. */
    void drawList(NBTTagCompound s) {
        fontRendererObj.drawString(String.format("Last minute: in %s, out %s EU", number(s.getLong("MinuteIn")), number(s.getLong("MinuteOut"))),
            8, 126, 0x2E5D8A);
        scroll(0);
        int dim = mc.theWorld == null ? 0 : mc.theWorld.provider.dimensionId;
        for (int i = 0; i < ROWS && listTop + i < c.blocks.size(); i++) {
            NBTTagCompound e = c.blocks.get(listTop + i);
            int y = 138 + 10 * i;
            int k = Math.max(0, Math.min(3, e.getByte("K")));
            int colour = e.getBoolean("Here") ? 0x7A3FA0 : e.getBoolean("Off") ? 0x9A9A9A : 0x303030;
            fontRendererObj.drawString(SHORT[k] + (k == TileGrid.CONTROLLER ? "" : " " + GridTier.NAMES[GridTier.clamp(e.getByte("T"))]), 8, y,
                colour);
            String at = e.getInteger("X") + " " + e.getInteger("Y") + " " + e.getInteger("Z") + (e.getInteger("D") != dim ? "*" : "");
            fontRendererObj.drawString(at, 62, y, colour);
            String m = number(e.getLong("M"));
            fontRendererObj.drawString(m, 168 - fontRendererObj.getStringWidth(m), y, colour);
        }
        int total = s.getInteger("BlockCount");
        if (c.blocks.size() > ROWS || total > c.blocks.size()) {
            String more = (listTop + 1) + "-" + Math.min(c.blocks.size(), listTop + ROWS) + " of " + total;
            fontRendererObj.drawString(more, 168 - fontRendererObj.getStringWidth(more), 126 + 62, 0x707070);
        }
    }

    int limitStep() {
        long v = GridTier.volts(s().getInteger("Tier")) * (shift() ? 16 : 1);
        return (int) Math.min(Integer.MAX_VALUE, v);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        panel(guiLeft, guiTop, xSize, ySize);
        drawWidgets(mx, my);
        if (!s().getString("Net")
            .isEmpty()) swatch(guiLeft + 125, guiTop + 15, 40, 7, s().getInteger("Colour"));
        for (int i = 0; i < 3 && i < c.networks.size(); i++) {
            int rgb;
            try {
                rgb = Integer.parseInt(c.networks.get(i)[2]);
            } catch (RuntimeException e) {
                rgb = -1;
            }
            swatch(guiLeft + 160, guiTop + 48 + 10 * i, 6, 6, rgb);
        }
        name.drawTextBox();
    }

    /** A colour patch with a dark edge. */
    void swatch(int x, int y, int w, int h, int rgb) {
        if (rgb < 0) return;
        drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF202020);
        drawRect(x, y, x + w, y + h, 0xFF000000 | rgb);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        NBTTagCompound s = s();
        int k = Math.max(0, Math.min(3, kind()));
        String title = KINDS[k] + (k == TileGrid.CONTROLLER ? "" : " (" + GridTier.NAMES[GridTier.clamp(s.getInteger("Tier"))] + ")");
        fontRendererObj.drawString(title, 8, 5, TEXT);
        fontRendererObj.drawString("Network", 8, 18, TEXT);
        if (c.networks.isEmpty()) fontRendererObj.drawString("Your team has no networks loaded.", 8, 48, 0x707070);
        fontRendererObj.drawString("EU/t", 95, 108, 0x606060);
        if (listing()) {
            drawList(s);
            return;
        }
        int y = 126;
        fontRendererObj.drawString(String.format("This block: %,d EU/s", s.getLong("Moved")), 8, y, 0x303030);
        y += 10;
        if (s.hasKey("Capacity")) {
            fontRendererObj.drawString(String.format("Stored %s / %s EU", number(s.getLong("Stored")), number(s.getLong("Capacity"))), 8, y,
                0x303030);
            y += 10;
        }
        if (s.getString("Net")
            .isEmpty()) {
            fontRendererObj.drawString("Not on a network: it does nothing.", 8, y, 0x9A4A1A);
            return;
        }
        fontRendererObj.drawString(String.format("Network: in %s, out %s EU/s", number(s.getLong("In")), number(s.getLong("Out"))), 8, y,
            0x2E5D8A);
        y += 10;
        if (s.getInteger("Loss") > 0) {
            fontRendererObj.drawString(String.format("Lost on the way: %s EU/s (%d%%)", number(s.getLong("Lost")), s.getInteger("Loss")), 8,
                y, 0x9A4A1A);
            y += 10;
        }
        if (s.getLong("NetCapacity") > 0) {
            fontRendererObj.drawString(String.format("Stored %s / %s EU", number(s.getLong("NetStored")), number(s.getLong("NetCapacity"))), 8,
                y, 0x2E5D8A);
            y += 10;
        }
        fontRendererObj.drawString(s.getInteger("Plugs") + " plugs, " + s.getInteger("Points") + " points, " + s.getInteger("Cells")
            + " cells, " + s.getInteger("Controllers") + " ctrl", 8, y, 0x606060);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        widgetTip(mx, my);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        name.updateCursorCounter();
        String net = s().getString("Net");
        if (!net.equals(shownNet) && !name.isFocused()) {
            name.setText(net);
            shownNet = net;
        }
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        name.mouseClicked(mx, my, button);
        super.mouseClicked(mx, my, button);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int dir = wheel();
        if (dir != 0) wheelWidgets(mouseX(), mouseY(), dir);
    }

    @Override
    protected void keyTyped(char ch, int key) {
        if (name.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                if (!linkedToTyped()) sendText(GridMenu.NET, name.getText());
                name.setFocused(false);
                return;
            }
            if (key == Keyboard.KEY_ESCAPE) {
                name.setFocused(false);
                return;
            }
            name.textboxKeyTyped(ch, key);
            return;
        }
        super.keyTyped(ch, key);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }
}
