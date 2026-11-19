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
 * GregTech, no Angelica): it shows how the blocks and the pipe menu look, not how they work.
 */
public final class DevClient {

    public static final boolean ENABLED = Boolean.getBoolean("valiertweaks.devclient");
    private static final String WORLD = "valier-devclient";

    private boolean launched, quitting, closing;
    private int shots, ticks;

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
            if (mc.currentScreen instanceof GuiMainMenu) {
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
        if (t < 200 && (mc.currentScreen instanceof GuiIngameMenu || mc.currentScreen instanceof GuiChat)) mc.displayGuiScreen(null);
        mc.gameSettings.hideGUI = t < 200;
        mc.thePlayer.capabilities.isFlying = true;
        double x = DevScene.baseX, y = DevScene.baseY, z = DevScene.baseZ;
        if (t < 60) camera(mc, x + 4, y + 3.2, z - 3.5, 0F, 35F); // the tiers
        else if (t < 110) camera(mc, x + 2.5, y + 2.4, z + 2.5, 0F, 40F); // the mode plates
        else if (t < 150) camera(mc, x + 9.5, y + 4.5, z + 2.0, -15F, 40F); // the line
        else if (t < 200) camera(mc, x + 14.5, y + 2.2, z - 2.6, 0F, 25F); // spawner and crafter
        else camera(mc, x + 2.5, y + 1.6, z + 4.0, 0F, 30F); // beside the pipe whose menu opens
        if (shots == 0 && t >= 50) shot(mc, "valier-1-pipe-tiers.png");
        if (shots == 1 && t >= 100) shot(mc, "valier-2-face-modes.png");
        if (shots == 2 && t >= 140) shot(mc, "valier-3-pipe-line.png");
        if (shots == 3 && t >= 190) shot(mc, "valier-4-spawner-crafter.png");
        if (shots == 4 && t >= 236) org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20); // no tooltip in the shot
        if (shots == 4 && t >= 240) shot(mc, "valier-5-pipe-menu.png");
        if (shots == 5 && t >= 250 && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainerCreative)) {
            creativeTab(mc); // every Valier block and item, as the creative tab lists them
        }
        if (shots == 5 && t >= 275) shot(mc, "valier-6-creative-tab.png");
        if (shots == 6) {
            mc.displayGuiScreen(null);
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld((WorldClient) null);
            mc.displayGuiScreen(new GuiMainMenu());
            quitting = true;
        }
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
