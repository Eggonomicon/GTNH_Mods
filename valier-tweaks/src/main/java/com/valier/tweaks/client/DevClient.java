package com.valier.tweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * {@code gradlew runClient -PvalierDevClient}: makes a fresh flat creative world from the main menu, lets
 * {@link DevScene} build this mod's blocks, holds the camera on them, saves screenshots and quits. Plain Forge only (no
 * GregTech, no Angelica): it shows how the blocks and the menus look, not how they work. 2.2 and 2.3 add the barrels,
 * the Grid and the docks, and the loot, barrel, Satchel, Flask and Grid menus.
 */
public final class DevClient {

    public static final boolean ENABLED = Boolean.getBoolean("valiertweaks.devclient");
    /** {@code -Dvaliertweaks.devclient=join}: the {@link Join} check on a server the launcher joins. */
    public static final boolean JOIN = "join".equalsIgnoreCase(System.getProperty("valiertweaks.devclient"));
    private static final String WORLD = "valier-devclient";

    private boolean launched, quitting, closing;
    private int shots, ticks;

    public static void registerJoin() {
        FMLCommonHandler.instance()
            .bus()
            .register(new Join());
    }

    /** The title screen: vanilla's, or a mod's own (GTNH's Custom Main Menu). */
    static boolean titleScreen(Minecraft mc) {
        return mc.currentScreen instanceof GuiMainMenu || mc.theWorld == null && mc.currentScreen != null && mc.currentScreen.getClass()
            .getName()
            .toLowerCase()
            .contains("mainmenu");
    }

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new DevClient());
        FMLCommonHandler.instance()
            .bus()
            .register(new DevScene());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (quitting) {
            if (!closing && mc.theWorld == null && !mc.isIntegratedServerRunning()) {
                closing = true;
                mc.shutdown();
            }
            return;
        }
        if (!launched) {
            if (titleScreen(mc)) {
                launched = true;
                mc.gameSettings.pauseOnLostFocus = false;
                mc.gameSettings.showInventoryAchievementHint = false;
                mc.getSaveLoader()
                    .deleteWorldDirectory(WORLD);
                WorldSettings s = new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
                s.enableCommands();
                mc.launchIntegratedServer(WORLD, WORLD, s);
            }
            return;
        }
        if (++ticks > 20 * 60 * 4) {
            ValierTweaks.LOG.error("[DevClient] timed out");
            mc.shutdown();
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null || !DevScene.built) return;
        int t = DevScene.tick;
        boolean world = t < 200 || t >= 284 && t < DevScene.LOOT_MENU - 6; // shots of the world, without the HUD
        if (world && (mc.currentScreen instanceof GuiIngameMenu || mc.currentScreen instanceof GuiChat)) mc.displayGuiScreen(null);
        if (t >= 282 && t < 284 && mc.currentScreen != null) mc.displayGuiScreen(null); // the creative tab, done
        mc.gameSettings.hideGUI = world;
        mc.thePlayer.capabilities.isFlying = true;
        double x = DevScene.baseX, y = DevScene.baseY, z = DevScene.baseZ;
        double bx = x + DevScene.BARRELS_X, gz = z + DevScene.GRID_Z;
        if (t < 60) camera(mc, x + 4, y + 3.2, z - 3.5, 0F, 35F); // the tiers
        else if (t < 110) camera(mc, x + 2.5, y + 2.4, z + 2.5, 0F, 40F); // the mode plates
        else if (t < 150) camera(mc, x + 9.5, y + 4.5, z + 2.0, -15F, 40F); // the line
        else if (t < 200) camera(mc, x + 14.5, y + 2.2, z - 2.6, 0F, 25F); // spawner and crafter
        else if (t < 284) camera(mc, x + 2.5, y + 1.6, z + 4.0, 0F, 30F); // beside the pipe whose menu opens
        else if (t < 330) camera(mc, bx + 5.5, y + 2.0, z - 5.0, 0F, 12F); // the barrels, every tier, and the linked pair
        else if (t < DevScene.LOOT_MENU - 6) camera(mc, bx + 5.5, y + 2.6, gz - 6.5, 0F, 12F); // the Grid and the docks
        else if (t < DevScene.BARREL_MENU - 6) camera(mc, x + 14.5, y + 2.2, z - 2.6, 0F, 25F); // at the spawner (its menu)
        else if (t < DevScene.GRID_MENU - 6) camera(mc, bx + 10.5, y + 1.6, z - 2.5, 0F, 20F); // at the linked barrel
        else camera(mc, bx + 4.5, y + 1.8, gz - 3.5, 0F, 20F); // at the Grid cell
        if (shots == 0 && t >= 50) shot(mc, "valier-1-pipe-tiers.png");
        if (shots == 1 && t >= 100) shot(mc, "valier-2-face-modes.png");
        if (shots == 2 && t >= 140) shot(mc, "valier-3-pipe-line.png");
        if (shots == 3 && t >= 190) shot(mc, "valier-4-spawner-crafter.png");
        if (shots == 4 && t >= 236) org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20); // no tooltip in the shot
        if (shots == 4 && t >= 240) shot(mc, "valier-5-pipe-menu.png");
        if (shots == 5 && t >= 242 && mc.currentScreen instanceof GuiPipeFace) { // the same face, its Void list
            GuiPipeFace g = (GuiPipeFace) mc.currentScreen;
            if (g.c.list != com.valier.tweaks.pipe.FaceConfig.VOID) {
                g.c.list = com.valier.tweaks.pipe.FaceConfig.VOID;
                ValierTweaks.NET.sendToServer(new com.valier.tweaks.pipe.PipeActionMessage(g.c.windowId,
                    com.valier.tweaks.pipe.PipeActionMessage.LIST, com.valier.tweaks.pipe.FaceConfig.VOID, 0, null, null));
            }
        }
        if (shots == 5 && t >= 250) shot(mc, "valier-5b-void-list.png");
        if (shots == 6 && t >= 256 && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainerCreative)) {
            creativeTab(mc); // every Valier block and item, as the creative tab lists them
        }
        if (shots == 6 && t >= 281) shot(mc, "valier-6-creative-tab.png");
        if (shots == 7 && t >= 325) shot(mc, "valier-7-barrels.png");
        if (shots == 8 && t >= 365) shot(mc, "valier-8-grid-and-docks.png");
        menuShot(mc, t, 9, DevScene.LOOT_MENU + 18, GuiLootSpawner.class, "valier-9-loot-menu.png");
        if (shots == 10 && t >= DevScene.LOOT_MENU + 20 && mc.currentScreen instanceof GuiLootSpawner
            && ((GuiLootSpawner) mc.currentScreen).c.store) {
            ((GuiLootSpawner) mc.currentScreen).pickList(com.valier.tweaks.pipe.FaceConfig.VOID); // its Void list
        }
        menuShot(mc, t, 10, DevScene.LOOT_MENU + 28, GuiLootSpawner.class, "valier-10-loot-void-list.png");
        menuShot(mc, t, 11, DevScene.BARREL_MENU + 15, GuiBarrel.class, "valier-11-barrel-menu.png");
        menuShot(mc, t, 12, DevScene.SATCHEL_MENU + 14, GuiSatchel.class, "valier-12-satchel-menu.png");
        menuShot(mc, t, 13, DevScene.FLASK_MENU + 14, GuiSatchel.class, "valier-13-flask-menu.png");
        menuShot(mc, t, 14, DevScene.GRID_MENU + 16, GuiGrid.class, "valier-14-grid-menu.png");
        if (shots == 15) {
            mc.displayGuiScreen(null);
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld((WorldClient) null);
            mc.displayGuiScreen(new GuiMainMenu());
            quitting = true;
        }
    }

    /**
     * In a server's world (the launcher joined it; the player an op there): a Grid Cell and an LV barrel beside the
     * player, the cell's menu opened through the server and the cell linked to a network from it, screenshots of the menu
     * and of the blocks (the cell with its network's ring), the blocks taken away again, and quit.
     */
    public static final class Join {

        int ticks, x, y, z;
        boolean quitting, closing;

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (quitting) {
                if (!closing && mc.theWorld == null) {
                    closing = true;
                    mc.shutdown();
                }
                return;
            }
            if (mc.thePlayer == null || mc.theWorld == null) return;
            mc.gameSettings.pauseOnLostFocus = false;
            ticks++;
            if (ticks == 100) {
                x = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX) - 1;
                y = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posY);
                z = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ) + 3;
                ValierTweaks.LOG.info("[DevClient] joined; Valier Tweaks " + cpw.mods.fml.common.Loader.instance()
                    .getIndexedModList()
                    .get(ValierTweaks.MODID)
                    .getVersion() + "; placing a Grid Cell and a barrel at " + x + " " + y + " " + z);
                mc.thePlayer.sendChatMessage("/gamemode 1");
                mc.thePlayer.sendChatMessage("/setblock " + x + " " + y + " " + z + " valiertweaks:grid_cell 0");
                mc.thePlayer.sendChatMessage("/setblock " + (x + 2) + " " + y + " " + z + " valiertweaks:barrel 1");
            }
            if (ticks >= 100 && ticks < 350) { // look south, at them
                mc.thePlayer.rotationYaw = 0F;
                mc.thePlayer.rotationPitch = 25F;
            }
            if (ticks == 140) {
                boolean cell = mc.theWorld.getBlock(x, y, z) == com.valier.tweaks.ModBlocks.gridCell;
                ValierTweaks.LOG.info("[DevClient] the cell is " + (cell ? "there" : "NOT there") + "; opening its menu");
                mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z, 2,
                    net.minecraft.util.Vec3.createVectorHelper(x + 0.5, y + 0.5, z));
            }
            if (ticks == 170) {
                if (mc.currentScreen instanceof GuiGrid) {
                    ValierTweaks.LOG.info("[DevClient] the Grid menu opened; linking the cell to \"join-test\"");
                    ValierTweaks.NET.sendToServer(new com.valier.tweaks.pipe.PipeActionMessage(((GuiGrid) mc.currentScreen).c.windowId,
                        com.valier.tweaks.grid.GridMenu.NET, 0, 0, null, "join-test"));
                } else ValierTweaks.LOG.warn("[DevClient] the Grid menu did NOT open ({})", mc.currentScreen);
            }
            if (ticks == 196) org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20);
            if (ticks == 200) {
                ScreenShotHelper.saveScreenshot(mc.mcDataDir, "valier-join-1-grid-menu.png", mc.displayWidth, mc.displayHeight,
                    mc.getFramebuffer());
                if (mc.currentScreen instanceof GuiGrid) ValierTweaks.LOG.info("[DevClient] menu state: {}", ((GuiGrid) mc.currentScreen).c.state);
            }
            if (ticks == 210) mc.thePlayer.closeScreen();
            // The barrel's menu: sneak + right-click with an empty hand.
            if (ticks == 214) net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            if (ticks == 224) {
                mc.thePlayer.inventory.currentItem = 0;
                ValierTweaks.LOG.info("[DevClient] sneaking {}, hand {}; opening the barrel's menu", mc.thePlayer.isSneaking(),
                    mc.thePlayer.getHeldItem());
                mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x + 2, y, z, 2,
                    net.minecraft.util.Vec3.createVectorHelper(x + 2.5, y + 0.5, z));
            }
            if (ticks == 230) net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            if (ticks == 246) org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20);
            if (ticks == 250) {
                ValierTweaks.LOG.info("[DevClient] the barrel menu {}", mc.currentScreen instanceof GuiBarrel ? "opened" : "did NOT open ("
                    + mc.currentScreen + ")");
                ScreenShotHelper.saveScreenshot(mc.mcDataDir, "valier-join-2-barrel-menu.png", mc.displayWidth, mc.displayHeight,
                    mc.getFramebuffer());
            }
            if (ticks == 256) mc.thePlayer.closeScreen();
            if (ticks == 260) mc.thePlayer.sendChatMessage("/give " + mc.thePlayer.getCommandSenderName() + " minecraft:iron_ingot 64");
            if (ticks == 280) { // 64 iron into the barrel, so its label shows a count
                mc.thePlayer.inventory.currentItem = 0;
                mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x + 2, y, z, 2,
                    net.minecraft.util.Vec3.createVectorHelper(x + 2.5, y + 0.5, z));
            }
            if (ticks == 300) {
                net.minecraft.tileentity.TileEntity te = mc.theWorld.getTileEntity(x, y, z);
                ValierTweaks.LOG.info("[DevClient] the cell shows network \"{}\", colour {}",
                    te instanceof com.valier.tweaks.grid.TileGrid ? ((com.valier.tweaks.grid.TileGrid) te).shownNet : "?",
                    te instanceof com.valier.tweaks.grid.TileGrid ? Integer.toHexString(((com.valier.tweaks.grid.TileGrid) te).shownColour) : "?");
                mc.gameSettings.hideGUI = true;
            }
            if (ticks == 310) ScreenShotHelper.saveScreenshot(mc.mcDataDir, "valier-join-3-blocks.png", mc.displayWidth, mc.displayHeight,
                mc.getFramebuffer());
            if (ticks == 320) {
                mc.gameSettings.hideGUI = false;
                mc.thePlayer.sendChatMessage("/setblock " + x + " " + y + " " + z + " air");
                mc.thePlayer.sendChatMessage("/setblock " + (x + 2) + " " + y + " " + z + " air");
                mc.thePlayer.sendChatMessage("/kill @e[type=Item,r=8]");
                mc.thePlayer.sendChatMessage("/clear");
            }
            if (ticks == 350) {
                ValierTweaks.LOG.info("[DevClient] join check done; leaving");
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld((WorldClient) null);
                mc.displayGuiScreen(new GuiMainMenu());
                quitting = true;
            }
        }
    }

    /** Shot number n of a menu at tick `at` (the mouse moved off the slots first, so no tooltip covers it). */
    void menuShot(Minecraft mc, int t, int n, int at, Class<?> screen, String name) {
        if (shots != n || t < at - 4) return;
        org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20);
        if (t < at) return;
        if (!screen.isInstance(mc.currentScreen)) {
            if (t < at + 40) return; // not open yet
            ValierTweaks.LOG.warn("[DevClient] {} didn't open ({}); shot anyway", screen.getSimpleName(), mc.currentScreen);
        }
        shot(mc, name);
    }

    static void camera(Minecraft mc, double x, double eyeY, double z, float yaw, float pitch) {
        mc.thePlayer.setLocationAndAngles(x, eyeY - mc.thePlayer.yOffset, z, yaw, pitch);
        mc.thePlayer.motionX = mc.thePlayer.motionY = mc.thePlayer.motionZ = 0;
    }

    /** Opens the creative inventory on the Valier tab (its page and index set first, as the game remembers them). */
    static void creativeTab(Minecraft mc) {
        try {
            Class<?> c = net.minecraft.client.gui.inventory.GuiContainerCreative.class;
            java.lang.reflect.Field page = c.getDeclaredField("tabPage");
            page.setAccessible(true);
            page.setInt(null, com.valier.tweaks.ModBlocks.tab.getTabPage());
            java.lang.reflect.Field index;
            try {
                index = c.getDeclaredField("selectedTabIndex");
            } catch (NoSuchFieldException e) {
                index = c.getDeclaredField("field_147058_w");
            }
            index.setAccessible(true);
            index.setInt(null, com.valier.tweaks.ModBlocks.tab.getTabIndex());
        } catch (Throwable e) {
            ValierTweaks.LOG.warn("[DevClient] couldn't pick the Valier tab", e);
        }
        mc.displayGuiScreen(new net.minecraft.client.gui.inventory.GuiContainerCreative(mc.thePlayer));
    }

    void shot(Minecraft mc, String name) {
        shots++;
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        ValierTweaks.LOG.info("[DevClient] screenshot " + name);
    }
}
