package com.valier.tweaks.client;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.valier.tweaks.ValierTweaks;
import com.valier.tweaks.pipe.PipeActionMessage;
import com.valier.tweaks.storage.BarrelChannels;
import com.valier.tweaks.storage.BarrelMenu;
import com.valier.tweaks.storage.BarrelTier;

/**
 * A barrel's menu ({@link BarrelMenu}): what it holds and how full it is; lock, void overflow and keep; its channel
 * (type a name or click one of your team's, then Link; Unlink takes it off).
 */
public class GuiBarrel extends ValierScreen {

    final BarrelMenu c;
    GuiTextField name;

    public GuiBarrel(BarrelMenu c) {
        super(c);
        this.c = c;
        xSize = 176;
        ySize = 222;
    }

    @Override
    void send(int action, int a, int b) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, a, b, null, null));
    }

    void sendText(int action, String text) {
        ValierTweaks.NET.sendToServer(new PipeActionMessage(c.windowId, action, 0, 0, null, text));
    }

    @Override
    public void initGui() {
        super.initGui();
        widgets.clear();
        Widget lock = add(8, 48, 18, 18, b -> send(BarrelMenu.LOCK, 0, 0));
        lock.item = () -> new ItemStack(Items.iron_door);
        lock.badge = () -> c.locked ? ICON_CHECK : ICON_CROSS;
        lock.tip = () -> lines(EnumChatFormatting.WHITE + (c.locked ? "Locked" : "Not locked"),
            c.locked ? "It keeps its item when empty: nothing else goes in." : "Empty, it takes any item.");
        Widget voids = add(28, 48, 18, 18, b -> send(BarrelMenu.VOID, 0, 0));
        voids.item = () -> new ItemStack(Blocks.obsidian);
        voids.badge = () -> c.voidOverflow ? ICON_CHECK : ICON_CROSS;
        voids.tip = () -> lines(EnumChatFormatting.WHITE + "Void overflow " + (c.voidOverflow ? "on" : "off"),
            c.voidOverflow ? "Full, it still takes its item and destroys it" : "Full, it takes no more",
            c.voidOverflow ? "(a sink for cobblestone and the like)." : "(on: a sink for cobblestone and the like).");
        stepper(88, 48, 18, 44, BarrelMenu.KEEP, 1, 64, () -> number(c.keep),
            () -> lines(EnumChatFormatting.WHITE + "Keep " + String.format("%,d", c.keep), "Hoppers, pipes and other automation",
                "leave this many in it.", EnumChatFormatting.GRAY + "Shift: by 64. Mouse wheel works too."));
        name = new GuiTextField(fontRendererObj, guiLeft + 9, guiTop + 85, 108, 12);
        name.setMaxStringLength(BarrelChannels.MAX_NAME);
        name.setText(c.channel);
        Widget link = add(122, 83, 46, 16, b -> {
            if (linkedToTyped()) send(BarrelMenu.UNLINK, 0, 0);
            else sendText(BarrelMenu.LINK, name.getText());
        });
        link.text = () -> linkedToTyped() ? "Unlink" : "Link";
        link.tip = () -> linkedToTyped()
            ? lines(EnumChatFormatting.WHITE + "Unlink", "It keeps what the channel's other", "barrels can't hold.")
            : lines(EnumChatFormatting.WHITE + "Link to \"" + name.getText() + "\"", "Every barrel on a channel shares one",
                "store: take one out anywhere, it's gone", "from all. Only your team's barrels.");
        for (int i = 0; i < 3; i++) {
            int k = i;
            Widget row = add(8, 102 + 10 * i, 160, 10, b -> {
                if (k < c.channels.size()) name.setText(c.channels.get(k)[0]);
            });
            row.flat = true;
            row.visible = () -> k < c.channels.size();
            row.text = () -> k < c.channels.size() ? trim(c.channels.get(k)[0] + ": " + c.channels.get(k)[1]) : "";
            row.colour = () -> k < c.channels.size() && c.channels.get(k)[0].equalsIgnoreCase(c.channel) ? 0xE6A0FF : 0xFFFFFF;
            row.tip = () -> k < c.channels.size() ? lines(EnumChatFormatting.WHITE + c.channels.get(k)[0], c.channels.get(k)[1],
                c.channels.get(k)[2] + " barrels", EnumChatFormatting.GRAY + "Click to pick it.") : null;
        }
        Keyboard.enableRepeatEvents(true);
    }

    boolean linkedToTyped() {
        return !c.channel.isEmpty() && c.channel.equalsIgnoreCase(name.getText()
            .trim());
    }

    String trim(String s) {
        return fontRendererObj.trimStringToWidth(s, 156);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mx, int my) {
        GL11.glColor4f(1, 1, 1, 1);
        panel(guiLeft, guiTop, xSize, ySize);
        slot(guiLeft + 8, guiTop + 18, false, false);
        // How full: a bar under the count.
        int x0 = guiLeft + 8, x1 = guiLeft + xSize - 8, y0 = guiTop + 38;
        drawRect(x0, y0, x1, y0 + 5, 0xFF373737);
        if (c.capacity > 0) {
            int w = (int) Math.min(x1 - x0 - 2, (x1 - x0 - 2) * c.count / c.capacity);
            drawRect(x0 + 1, y0 + 1, x0 + 1 + w, y0 + 4, c.voidOverflow ? 0xFFB070E0 : 0xFF6AC46A);
        }
        for (Object o : inventorySlots.inventorySlots) {
            net.minecraft.inventory.Slot s = (net.minecraft.inventory.Slot) o;
            slot(guiLeft + s.xDisplayPosition, guiTop + s.yDisplayPosition, false, false);
        }
        drawWidgets(mx, my);
        name.drawTextBox();
        item(c.item, guiLeft + 8, guiTop + 18);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString("Valier Barrel (" + BarrelTier.NAMES[BarrelTier.clamp(c.tier)] + ")", 8, 5, TEXT);
        String what = c.item == null ? (c.locked ? "Locked, empty" : "Empty") : c.item.getDisplayName();
        fontRendererObj.drawString(trimTo(what, 140), 28, 18, 0x303030);
        fontRendererObj.drawString(String.format("%,d / %,d", c.count, c.capacity), 28, 28, 0x505050);
        fontRendererObj.drawString("Keep", 60, 53, TEXT);
        fontRendererObj.drawString(c.channel.isEmpty() ? "Channel: not linked"
            : "Channel: " + c.channel + " (" + c.barrels + (c.barrels == 1 ? " barrel)" : " barrels)"), 8, 73, TEXT);
        if (!c.note.isEmpty()) fontRendererObj.drawString(trimTo(c.note, 160), 8, 132, 0x9A1A1A);
        else if (c.channels.isEmpty()) fontRendererObj.drawString("Your team has no channels yet.", 8, 104, 0x707070);
    }

    String trimTo(String s, int w) {
        return fontRendererObj.trimStringToWidth(s, w);
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        widgetTip(mx, my);
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
                if (!linkedToTyped()) sendText(BarrelMenu.LINK, name.getText());
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
    public void updateScreen() {
        super.updateScreen();
        name.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        super.onGuiClosed();
    }
}
