package com.valier.tweaks;

import cpw.mods.fml.common.Mod;

/**
 * Valier Client is part of Valier Tweaks since 2.0.0. This takes its mod id, so a player who still has the old
 * {@code valierclient-*.jar} is stopped at start-up by Forge's "duplicate mods" screen, which names both files (the old
 * one would add its NEI, WAILA and creative tab parts a second time). It does nothing else.
 */
@Mod(
    modid = "valierclient",
    version = Tags.VERSION,
    name = "Valier Client (now part of Valier Tweaks)",
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class OldClientMod {}
