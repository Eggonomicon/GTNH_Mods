package com.valier.tweaks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
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
    /** Shots taken so far (DevScene, on the integrated server's thread, waits for each menu's). */
    static volatile int taken;

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
        GuiScreen open = mc.currentScreen;
        menuShot(mc, t, 9, DevScene.LOOT_MENU + 18, open instanceof GuiLootSpawner, "valier-9-loot-menu.png");
        if (shots == 10 && t >= DevScene.LOOT_MENU + 20 && open instanceof GuiLootSpawner && ((GuiLootSpawner) open).c.store) {
            ((GuiLootSpawner) open).pickList(com.valier.tweaks.pipe.FaceConfig.VOID); // its Void list
        }
        menuShot(mc, t, 10, DevScene.LOOT_MENU + 28, open instanceof GuiLootSpawner, "valier-10-loot-void-list.png");
        menuShot(mc, t, 11, DevScene.BARREL_MENU + 15, open instanceof GuiBarrel, "valier-11-barrel-menu.png");
        if (t >= DevScene.SATCHEL_MENU && t < DevScene.SATCHEL_MENU + 6 && !usedSatchel) usedSatchel = useInAir(mc, false);
        if (t >= DevScene.FLASK_MENU && t < DevScene.FLASK_MENU + 6 && !usedFlask) usedFlask = useInAir(mc, true);
        if (usedSatchel && t > DevScene.SATCHEL_MENU && t < DevScene.SATCHEL_MENU + 14) stayed(open, false);
        if (usedFlask && t > DevScene.FLASK_MENU && t < DevScene.FLASK_MENU + 14) stayed(open, true);
        menuShot(mc, t, 12, DevScene.SATCHEL_MENU + 14, satchel(open, false), "valier-12-satchel-menu.png");
        menuShot(mc, t, 13, DevScene.FLASK_MENU + 14, satchel(open, true), "valier-13-flask-menu.png");
        menuShot(mc, t, 14, DevScene.GRID_MENU + 16, open instanceof GuiGrid, "valier-14-grid-menu.png");
        if (shots == 15) {
            mc.displayGuiScreen(null);
            mc.theWorld.sendQuittingDisconnectingPacket();
            mc.loadWorld((WorldClient) null);
            mc.displayGuiScreen(new GuiMainMenu());
            quitting = true;
        }
    }

    /**
     * In a server's world (the launcher joined it; the player an op there), every menu opened the way a player opens
     * it, through the server: a Grid Cell (right-click; then linked to a network from its menu), an LV barrel (sneak +
     * right-click with an empty hand; then 64 iron put in, for its label and WAILA) and a filled Satchel (right-click in
     * the air). A screenshot and a log line for each, one of the barrel's WAILA and one of the blocks (a Plug and a Point
     * beside them, their arms reaching out to IC2 BatBoxes); then all of it
     * taken away again (the dropped blocks picked up, the inventory cleared, survival again, back where it stood) and quit.
     */
    public static final class Join {

        int ticks, dead, x, y, z, hand = -1, satchelAt = -1;
        double homeX, homeY, homeZ, aimX, aimY, aimZ;
        boolean quitting, closing, satchelShut;

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
            // A test character saved dead (it left on the death screen) comes back first; the steps wait for it.
            if (mc.thePlayer.getHealth() <= 0 || mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver) {
                if (dead++ % 40 == 0) {
                    ValierTweaks.LOG.info("[DevClient] the player is dead; respawning");
                    mc.thePlayer.respawnPlayer();
                    mc.displayGuiScreen(null);
                }
                return;
            }
            ticks++;
            net.minecraft.client.entity.EntityClientPlayerMP me = mc.thePlayer;
            String name = me.getCommandSenderName();
            if (ticks == 100) {
                x = net.minecraft.util.MathHelper.floor_double(me.posX) - 1;
                y = net.minecraft.util.MathHelper.floor_double(me.posY);
                z = net.minecraft.util.MathHelper.floor_double(me.posZ) + 3;
                homeX = me.posX;
                homeY = me.boundingBox.minY;
                homeZ = me.posZ;
                ValierTweaks.LOG.info("[DevClient] joined; Valier Tweaks " + cpw.mods.fml.common.Loader.instance()
                    .getIndexedModList()
                    .get(ValierTweaks.MODID)
                    .getVersion() + "; placing a Grid Cell, a barrel, a Plug and a Point (with BatBoxes) at " + x + " " + y + " " + z);
                me.sendChatMessage("/gamemode 1");
                me.sendChatMessage("/effect " + name + " 16 60 0"); // night vision: the shots are lit at any time of day
                me.sendChatMessage("/setblock " + x + " " + y + " " + z + " valiertweaks:grid_cell 0");
                me.sendChatMessage("/setblock " + (x + 2) + " " + y + " " + z + " valiertweaks:barrel 1");
                me.sendChatMessage("/setblock " + (x - 2) + " " + y + " " + z + " valiertweaks:grid_plug 0");
                me.sendChatMessage("/setblock " + (x - 2) + " " + (y + 1) + " " + z + " valiertweaks:grid_point 1");
                // IC2 BatBoxes beside the Plug (west) and above the Point: their arms reach out to them.
                me.sendChatMessage("/setblock " + (x - 3) + " " + y + " " + z + " IC2:blockElectric 0");
                me.sendChatMessage("/setblock " + (x - 2) + " " + (y + 2) + " " + z + " IC2:blockElectric 0");
            }
            if (ticks == 120) { // an empty hotbar slot for the empty hand and the Satchel (else the last one, emptied)
                for (int i = 0; i < 9 && hand < 0; i++) if (me.inventory.mainInventory[i] == null) hand = i;
                if (hand < 0) {
                    hand = 8;
                    me.inventory.mainInventory[hand] = null;
                    mc.playerController.sendSlotPacket(null, 36 + hand);
                }
                ValierTweaks.LOG.info("[DevClient] creative {}; the empty hand is hotbar slot {}", mc.playerController.isInCreativeMode(), hand);
            }
            // Where the player looks: south at the blocks, unless a step aims at one.
            if (ticks == 100 || ticks == 210 || ticks == 336) aim(x + 0.5, y + 0.5, z - 1.0);
            if (ticks == 356) aim(x - 0.5, y + 0.8, z + 0.5); // the whole row: BatBoxes, Plug and Point, cell, barrel
            if (ticks == 130) aim(x + 0.5, y + 0.5, z + 0.5);
            if (ticks == 212 || ticks == 340) aim(x + 2.5, y + 0.5, z + 0.5);
            if (ticks >= 100 && ticks < 380) look(me);

            // The Grid Cell's menu: right-click.
            if (ticks == 140) {
                boolean cell = mc.theWorld.getBlock(x, y, z) == com.valier.tweaks.ModBlocks.gridCell;
                ValierTweaks.LOG.info("[DevClient] the cell is " + (cell ? "there" : "NOT there") + "; opening its menu");
                click(mc, x, y, z);
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
                shot(mc, "valier-join-1-grid-menu.png");
                if (mc.currentScreen instanceof GuiGrid) ValierTweaks.LOG.info("[DevClient] menu state: {}", ((GuiGrid) mc.currentScreen).c.state);
            }
            if (ticks == 206) me.closeScreen();

            // The barrel's menu: sneak + right-click with an empty hand. The server is told of the sneak directly (the
            // key, pressed from here, doesn't reach it while the game window is in the background).
            if (ticks == 214) {
                me.inventory.currentItem = hand;
                me.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C0BPacketEntityAction(me, 1));
            }
            if (ticks == 220) {
                ValierTweaks.LOG.info("[DevClient] sneaking, hand {}; opening the barrel's menu", me.getHeldItem());
                click(mc, x + 2, y, z);
            }
            if (ticks == 224) me.sendQueue.addToSendQueue(new net.minecraft.network.play.client.C0BPacketEntityAction(me, 2));
            if (ticks == 246) org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20);
            if (ticks == 250) {
                if (mc.currentScreen instanceof GuiBarrel) {
                    com.valier.tweaks.storage.BarrelMenu b = ((GuiBarrel) mc.currentScreen).c;
                    ValierTweaks.LOG.info("[DevClient] the barrel menu opened: tier {}, {} of {}, channel \"{}\"", b.tier, b.count, b.capacity, b.channel);
                } else ValierTweaks.LOG.warn("[DevClient] the barrel menu did NOT open ({})", mc.currentScreen);
                shot(mc, "valier-join-2-barrel-menu.png");
            }
            if (ticks == 256) me.closeScreen();
            if (ticks == 260) me.sendChatMessage("/give " + name + " minecraft:iron_ingot 64");
            if (ticks == 280) { // the iron into the barrel (a plain right-click), for its label and WAILA
                int iron = -1;
                for (int i = 0; i < 9 && iron < 0; i++) {
                    net.minecraft.item.ItemStack s = me.inventory.mainInventory[i];
                    if (s != null && s.getItem() == net.minecraft.init.Items.iron_ingot) iron = i;
                }
                if (iron >= 0) {
                    me.inventory.currentItem = iron;
                    click(mc, x + 2, y, z);
                } else ValierTweaks.LOG.warn("[DevClient] no iron in the hotbar to put in the barrel");
            }

            // The Satchel's menu: a filled LuV Satchel (put in the hand as the creative inventory does), right-click in
            // the air.
            if (ticks == 290) {
                net.minecraft.item.ItemStack bag = DevScene.satchel();
                me.inventory.mainInventory[hand] = bag;
                mc.playerController.sendSlotPacket(bag, 36 + hand);
            }
            if (ticks == 300) {
                me.inventory.currentItem = hand;
                ValierTweaks.LOG.info("[DevClient] opening the Satchel's menu (right-click in the air) with {}", me.getHeldItem());
                mc.playerController.sendUseItem(me, mc.theWorld, me.getHeldItem());
            }
            if (ticks > 300 && ticks < 330) {
                boolean open = satchel(mc.currentScreen, false);
                if (open && satchelAt < 0) {
                    satchelAt = ticks;
                    ValierTweaks.LOG.info("[DevClient] the Satchel menu opened");
                }
                if (!open && satchelAt >= 0 && !satchelShut) {
                    satchelShut = true;
                    ValierTweaks.LOG.warn("[DevClient] the Satchel menu CLOSED by itself {} ticks after it opened", ticks - satchelAt);
                }
            }
            if (ticks == 326) org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20);
            if (ticks == 330) {
                if (satchel(mc.currentScreen, false)) {
                    com.valier.tweaks.storage.SatchelInventory inv = ((GuiSatchel) mc.currentScreen).inv();
                    ValierTweaks.LOG.info("[DevClient] the Satchel menu is open: {} slots, slot 1 {} x{}", inv.size, inv.items[0], inv.counts[0]);
                } else ValierTweaks.LOG.warn("[DevClient] the Satchel menu is NOT open ({})", mc.currentScreen);
                shot(mc, "valier-join-3-satchel-menu.png");
            }
            if (ticks == 334) me.closeScreen();

            // The barrel's WAILA (HUD shown, looking at it), then the blocks (no HUD).
            if (ticks == 350) shot(mc, "valier-join-4-barrel-waila.png");
            if (ticks == 356) mc.gameSettings.hideGUI = true;
            if (ticks == 366) shot(mc, "valier-join-5-blocks.png");
            if (ticks == 370) {
                mc.gameSettings.hideGUI = false;
                net.minecraft.tileentity.TileEntity te = mc.theWorld.getTileEntity(x, y, z);
                ValierTweaks.LOG.info("[DevClient] the cell shows network \"{}\", colour {}",
                    te instanceof com.valier.tweaks.grid.TileGrid ? ((com.valier.tweaks.grid.TileGrid) te).shownNet : "?",
                    te instanceof com.valier.tweaks.grid.TileGrid ? Integer.toHexString(((com.valier.tweaks.grid.TileGrid) te).shownColour) : "?");
                me.sendChatMessage("/setblock " + x + " " + y + " " + z + " air");
                me.sendChatMessage("/setblock " + (x + 2) + " " + y + " " + z + " air");
                me.sendChatMessage("/setblock " + (x - 2) + " " + (y + 1) + " " + z + " air");
                me.sendChatMessage("/setblock " + (x - 2) + " " + y + " " + z + " air");
                me.sendChatMessage("/setblock " + (x - 3) + " " + y + " " + z + " air");
                me.sendChatMessage("/setblock " + (x - 2) + " " + (y + 2) + " " + z + " air");
            }
            // The removed blocks drop as items (with what they held): walk over them, then clear the inventory.
            if (ticks == 385) tp(me, x - 2.0, homeY, z + 0.5);
            if (ticks == 395) tp(me, x + 0.5, homeY, z + 0.5);
            if (ticks == 405) tp(me, x + 2.5, homeY, z + 0.5);
            if (ticks == 420) {
                me.sendChatMessage("/clear " + name);
                me.sendChatMessage("/effect " + name + " clear");
                me.sendChatMessage("/gamemode 0"); // survival, the test server's default (it was set to creative for the check)
                tp(me, homeX, homeY, homeZ);
            }
            if (ticks == 440) {
                ValierTweaks.LOG.info("[DevClient] join check done; leaving");
                mc.theWorld.sendQuittingDisconnectingPacket();
                mc.loadWorld((WorldClient) null);
                mc.displayGuiScreen(new GuiMainMenu());
                quitting = true;
            }
        }

        void aim(double ax, double ay, double az) {
            aimX = ax;
            aimY = ay;
            aimZ = az;
        }

        /** Turns the player to the aim point (the client player's posY is its eye height). */
        void look(net.minecraft.client.entity.EntityClientPlayerMP me) {
            double dx = aimX - me.posX, dy = aimY - me.posY, dz = aimZ - me.posZ;
            me.rotationYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90F;
            me.rotationPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
        }

        /** A right-click on the block's north face, as the player's own click sends it. */
        static void click(Minecraft mc, int bx, int by, int bz) {
            mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), bx, by, bz, 2,
                net.minecraft.util.Vec3.createVectorHelper(bx + 0.5, by + 0.5, bz));
        }

        static void tp(net.minecraft.client.entity.EntityClientPlayerMP me, double tx, double ty, double tz) {
            me.sendChatMessage(String.format(java.util.Locale.ROOT, "/tp %s %.2f %.2f %.2f", me.getCommandSenderName(), tx, ty, tz));
        }

        static void shot(Minecraft mc, String name) {
            ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            ValierTweaks.LOG.info("[DevClient] screenshot " + name);
        }
    }

    /**
     * Shot number n of a menu at tick `at` (the mouse moved off the slots first, so no tooltip covers it). DevScene holds
     * its timeline at `at` until this shot is taken, so a later tick means it gave up waiting for the menu.
     */
    void menuShot(Minecraft mc, int t, int n, int at, boolean open, String name) {
        if (shots != n || t < at - 4) return;
        org.lwjgl.input.Mouse.setCursorPosition(mc.displayWidth - 20, 20);
        if (t < at) return;
        if (!open) {
            if (t == at) return; // not open yet
            ValierTweaks.LOG.warn("[DevClient] the menu for {} didn't open ({}); shot anyway", name, mc.currentScreen);
        }
        shot(mc, name);
    }

    /** A Satchel's menu is open (fluid: a Flask's). */
    static boolean usedSatchel, usedFlask;
    /** When the Satchel's (0) and Flask's (1) menus were first seen open, and whether they then closed by themselves. */
    static int[] openedAt = { -1, -1 };
    static boolean[] shut = new boolean[2];

    /**
     * A right-click in the air with the Satchel or Flask in the hand, as a player does it (2.3.1): the server then swaps
     * the held stack for a copy of itself, which closed their menus at once in 2.3.0. Looking straight up first (told to
     * the server before the click), so a Flask finds no block or fluid to use instead.
     */
    static boolean useInAir(Minecraft mc, boolean fluid) {
        net.minecraft.item.ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof com.valier.tweaks.storage.ItemSatchel) || mc.currentScreen != null) return false;
        mc.thePlayer.rotationPitch = -90F;
        mc.getNetHandler()
            .addToSendQueue(new net.minecraft.network.play.client.C03PacketPlayer.C05PacketPlayerLook(mc.thePlayer.rotationYaw, -90F,
                mc.thePlayer.onGround));
        ValierTweaks.LOG.info("[DevClient] right-click in the air with {}", held);
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
        return true;
    }

    /** Logs when the menu opened, and if it then closed by itself before its picture. */
    static void stayed(GuiScreen open, boolean fluid) {
        int i = fluid ? 1 : 0;
        boolean now = satchel(open, fluid);
        if (now && openedAt[i] < 0) {
            openedAt[i] = DevScene.tick;
            ValierTweaks.LOG.info("[DevClient] the {} menu opened", fluid ? "Flask" : "Satchel");
        }
        if (!now && openedAt[i] >= 0 && !shut[i]) {
            shut[i] = true;
            ValierTweaks.LOG.warn("[DevClient] the {} menu CLOSED by itself {} ticks after it opened", fluid ? "Flask" : "Satchel",
                DevScene.tick - openedAt[i]);
        }
    }

    static boolean satchel(GuiScreen open, boolean fluid) {
        if (!(open instanceof GuiSatchel)) return false;
        com.valier.tweaks.storage.SatchelInventory inv = ((GuiSatchel) open).inv();
        return inv != null && inv.fluid == fluid;
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
        taken = ++shots;
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        ValierTweaks.LOG.info("[DevClient] screenshot " + name);
    }
}
