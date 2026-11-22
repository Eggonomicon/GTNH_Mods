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
        ySize = 235;
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
        upgrade(8, 37, LootMenu.LOOTING);
        upgrade(92, 37, LootMenu.SPEED);
        Widget xp = add(62, 51, 25, 12, b -> send(LootMenu.XP, 0, 0));
        xp.text = () -> c.xpItem == null && !c.xp ? "-" : c.xp ? "Yes" : "No";
        xp.colour = () -> c.xp ? 0x7CFC7C : 0xFF7070;
        xp.tip = this::xpTip;
        Widget damaged = add(144, 51, 25, 12, b -> send(LootMenu.DAMAGED, 0, 0));
        damaged.text = () -> c.filterItem == null && !c.noDamaged ? "-" : c.noDamaged ? "No" : "Yes";
        damaged.colour = () -> c.noDamaged ? 0xFF7070 : 0x7CFC7C;
        damaged.tip = this::damagedTip;
        Widget storeTab = add(8, 67, 52, 11, b -> {
            c.store = true;
            selected = -1;
            place();
            send(PipeActionMessage.LIST, LootMenu.STORE, 0);
        });
        storeTab.text = () -> "Store";
        storeTab.on = () -> c.store;
        storeTab.colour = () -> 0xFFE080;
        storeTab.tip = () -> lines(EnumChatFormatting.WHITE + "Store", "Loot nothing touching it took yet.", "Take it out; nothing goes in.");
        addListTabs(61, 67, FaceConfig.ALLOW, FaceConfig.VOID);
        addToolbar(80);
        place();
    }

    // ---- upgrades (2.3.1) ---------------------------------------------------------------------------------------------

    /** Looting or Speed: - takes one back, + puts one in (both from and to the inventory). */
    void upgrade(int x, int y, int action) {
        Widget minus = add(x + 56, y, 11, 12, b -> send(action, -1, 0));
        minus.text = () -> "-";
        minus.tip = () -> upgradeTip(action);
        Widget plus = add(x + 68, y, 11, 12, b -> send(action, 1, 0));
        plus.text = () -> "+";
        plus.tip = () -> upgradeTip(action);
    }

    String upgradeLabel(int action) {
        boolean l = action == LootMenu.LOOTING;
        int level = l ? c.looting : c.speed;
        return level > 0 ? (l ? "Looting " : "Speed ") + roman(level) : l ? "No Looting" : "No Speed";
    }

    List<String> upgradeTip(int action) {
        boolean l = action == LootMenu.LOOTING;
        int level = l ? c.looting : c.speed, in = l ? c.lootingIn : c.speedIn, below = !l && c.speedBelow ? 1 : 0;
        int[] tiers = l ? c.lootingTiers : c.speedTiers;
        ItemStack item = l ? c.lootingItem : c.speedItem;
        String name = item == null ? (l ? "Looting" : "Speed") + " items" : item.getDisplayName();
        List<String> out = lines(EnumChatFormatting.WHITE + upgradeLabel(action) + (!l && level > 0 ? " (" + (level + 1)
            + "× as fast)" : ""));
        out.add(l ? "Its mobs' loot is rolled as if killed" : "Its batches come faster.");
        if (l) out.add("with a Looting sword.");
        out.add(in + " × " + name + " in it" + (below > 0 ? ", and one under it" : "") + ".");
        out.add(level < tiers.length ? (tiers[level] - in - below) + " more for " + (l ? "Looting " : "Speed ") + roman(level + 1) + "."
            : "That's the most it can have.");
        if (item == null) out.add(EnumChatFormatting.GRAY + "None can be put in on this server.");
        else {
            out.add(EnumChatFormatting.GRAY + "+: put one in from your inventory.");
            out.add(EnumChatFormatting.GRAY + "-: take one back.");
        }
        return out;
    }

    List<String> xpTip() {
        List<String> out = lines(EnumChatFormatting.WHITE + "XP bottles: " + (c.xp ? "Yes" : "No"));
        out.add(c.xp ? "The XP its mobs would drop comes out" : "No XP comes out.");
        if (c.xp) out.add("as bottles o' enchanting, one per " + c.xpPerBottle + " XP.");
        if (c.xpItem == null) out.add(EnumChatFormatting.GRAY + "Can't be switched on this server.");
        else if (c.xp) out.add(EnumChatFormatting.GRAY + "Click: switch off (" + cost(c.xpItem) + " comes back).");
        else {
            out.add(EnumChatFormatting.GRAY + "Click: switch on. Takes " + cost(c.xpItem));
            out.add(EnumChatFormatting.GRAY + "from your inventory (it comes back when off).");
        }
        return out;
    }

    List<String> damagedTip() {
        List<String> out = lines(EnumChatFormatting.WHITE + "Damaged items: " + (c.noDamaged ? "No" : "Yes"));
        out.add("Worn tools, weapons and armour its");
        out.add(c.noDamaged ? "mobs drop are thrown away." : "mobs drop come out with the rest.");
        if (c.filterItem == null) out.add(EnumChatFormatting.GRAY + "Can't be switched on this server.");
        else if (c.noDamaged) out.add(EnumChatFormatting.GRAY + "Click: let them out (" + cost(c.filterItem) + " comes back).");
        else {
            out.add(EnumChatFormatting.GRAY + "Click: throw them away. Takes " + cost(c.filterItem));
            out.add(EnumChatFormatting.GRAY + "from your inventory (it comes back on Yes).");
        }
        return out;
    }

    static String cost(ItemStack s) {
        return (s.stackSize > 1 ? s.stackSize + " × " : "") + s.getDisplayName();
    }

    static String roman(int n) {
        String[] r = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
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
        if (!c.store) filterArea(guiLeft + 5, guiTop + 79, guiLeft + xSize - 5, guiTop + 139);
        drawSlots();
        drawWidgets(mx, my);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mx, int my) {
        fontRendererObj.drawString("Valier Loot Spawner", 8, 5, TEXT);
        fontRendererObj.drawString(trim(c.line1), 8, 16, 0x303030);
        fontRendererObj.drawString(trim(c.line2), 8, 26, c.line2.startsWith("Working") ? 0x2E7D32 : 0x9A4A1A);
        fontRendererObj.drawString(upgradeLabel(LootMenu.LOOTING), 8, 39, c.looting > 0 ? 0x303030 : 0x808080);
        fontRendererObj.drawString(upgradeLabel(LootMenu.SPEED), 92, 39, c.speed > 0 ? 0x303030 : 0x808080);
        fontRendererObj.drawString("XP bottles", 8, 53, 0x303030);
        fontRendererObj.drawString("Damaged", 92, 53, 0x303030);
        fontRendererObj.drawString(trim(info()), 8, 142, !c.store && !c.note.isEmpty() ? 0x9A1A1A : 0x606060);
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
            : "Voided in the last hour: " + number(c.voided);
        if (!c.note.isEmpty()) return c.note;
        if (c.list == FaceConfig.ALLOW) return fl().active() ? "Only what this list names comes out" : "Empty: everything comes out";
        return "What this list names is destroyed";
    }

    @Override
    public void drawScreen(int mx, int my, float partial) {
        super.drawScreen(mx, my, partial);
        if (widgetTip(mx, my)) return;
        if (mx >= guiLeft + 6 && mx < guiLeft + xSize - 6 && my >= guiTop + 14 && my < guiTop + 35 && !c.full.isEmpty()) {
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
