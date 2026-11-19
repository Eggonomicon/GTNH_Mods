package com.valier.tweaks;

import net.minecraft.util.StatCollector;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.Side;

/**
 * {@code valiertweaks selftest mod}: the parts of 2.0.0's one-jar setup a dedicated server can check. The join check
 * (both ways), the stub that takes the old Valier Client's mod id, the language file, and the server's proxy.
 */
final class ModSelfTest {

    private ModSelfTest() {}

    static void run() {
        boolean server = ValierTweaks.compatible("2.0.0", Side.CLIENT, "2.0.5")
            && ValierTweaks.compatible("v2.0.1-dev", Side.CLIENT, "2.0.0") && !ValierTweaks.compatible("2.1.0", Side.CLIENT, "2.0.0")
            && !ValierTweaks.compatible("1.14.0", Side.CLIENT, "2.0.0") && !ValierTweaks.compatible(null, Side.CLIENT, "2.0.0")
            && !ValierTweaks.compatible("dev", Side.CLIENT, "2.0.0");
        SelfTest.check(server, "join check on the server: a player needs Valier Tweaks 2.0.x to join a 2.0 server (2.0.0, "
            + "v2.0.1-dev in; 2.1.0, 1.14.0, none, \"dev\" out)");
        boolean client = ValierTweaks.compatible(null, Side.SERVER, "2.0.0") && ValierTweaks.compatible("2.0.9", Side.SERVER, "2.0.0")
            && !ValierTweaks.compatible("2.1.0", Side.SERVER, "2.0.0");
        SelfTest.check(client, "join check on a player's side: a server without Valier Tweaks is fine, a 2.0.x one too, a "
            + "2.1 one isn't");
        String ours = ValierTweaks.majorMinor(Tags.VERSION);
        SelfTest.check(ours != null, "this build's version " + Tags.VERSION + " reads as major.minor " + ours);

        ModContainer stub = Loader.instance()
            .getIndexedModList()
            .get("valierclient");
        ModContainer tweaks = Loader.instance()
            .getIndexedModList()
            .get(ValierTweaks.MODID);
        SelfTest.check(stub != null && tweaks != null && stub.getSource()
            .equals(tweaks.getSource()),
            "the old Valier Client's mod id is taken by this jar, so an old valierclient jar next to it is a duplicate ("
                + (stub == null ? "none" : stub.getSource()) + ")");
        String name = StatCollector.translateToLocal("valiertweaks.xpcrafter.name");
        SelfTest.check("Valier XP Crafter".equals(name), "the language file is in this jar (" + name + ")");
        SelfTest.check(ValierTweaks.proxy != null && ValierTweaks.proxy.getClass() == CommonProxy.class,
            "a dedicated server gets the common proxy, never the client one (" + ValierTweaks.proxy + ")");
    }
}
