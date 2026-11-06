# GTNH_Mods

Small **server-side** mods for [GT: New Horizons](https://www.gtnewhorizons.com/), written for the Valier server.
Players don't need to install anything: every mod here works with a stock GTNH client.

| | |
|---|---|
| **GTNH version** | **2.9.0** (Minecraft 1.7.10, Forge 10.13.4.1614) |
| **Tested on** | a dedicated GTNH 2.9.0 server on Java 25 (Temurin), via lwjgl3ify |
| **Side** | server only (`acceptableRemoteVersions = "*"`) |
| **License** | MIT (see `LICENSE`) |

Not affiliated with the GTNH team. Other GTNH versions may work but haven't been tested. In particular, two mods hook
into specific versions and may need updating for others: MM Free Power into Matter Manipulator 0.1.75-GTNH, and
Valier Claims into ServerUtilities 2.4.22.

## The mods

| Mod | Version | What it does | Details |
|---|---|---|---|
| **MM Free Power** (`mmfreepower`) | 1.2.0 | Matter Manipulators never need EU. Crafting an MK III on its own makes the **Valier MM Mk IV**, which places blocks without machines for free. Each player can give their Mk IV a void list (`/mmvoid`, e.g. `/mmvoid cobble`) so removed blocks they don't want are deleted instead of dropped; ops can purge matching drops from the ground. | [mmfreepower/README.md](mmfreepower/README.md) |
| **Valier Claims** (`valierclaims`) | 1.2.0 | Hostile mobs don't spawn in claimed chunks, by default. Every ServerUtilities team starts with hostile spawns blocked in its claims, and can change that in its team settings (passive animals, bats and water mobs too). Also stops spawns that ServerUtilities misses: zombie reinforcements, village sieges, Thaumcraft dark nodes' zombies and Wither Witch cats (configurable). Logs any hostile mob that still turns up in such a claim, with what put it there (a spawner, another mod, walking in). Also fixes ServerUtilities forgetting a team's animal and bat choices at each restart. | [valier-claims/README.md](valier-claims/README.md) |
| **Valier Tweaks** (`valiertweaks`) | 1.0.0 | Small gameplay fixes. A Tinkers' Construct lumber axe fells the whole tree, even if it breaks partway, so no floating trees are left. The extra logs drop normally and claims still protect them. | [valier-tweaks/README.md](valier-tweaks/README.md) |
| **Valier Loot** (`valierloot`) | 1.2.0 | Per-player loot chests, Lootr-style. Every player gets their own copy of each worldgen chest, plus a weighted random roll from the LootGames tier tables. Sneak-break or Matter Manipulator to remove one. Restores pre-install chests from a backup. | [valier-loot/README.md](valier-loot/README.md) |
| **Valier World Fix** (`valierworldfix`) | 1.1.0 | `/worldfix pregen`: pregenerates **and populates** the world out to a radius, only while nobody is online. It pauses when players log in and when free disk space runs low. It fixes the "generated but unpopulated" chunks a pregen can leave, which freeze the server when players walk into them. | [valier-worldfix/README.md](valier-worldfix/README.md) |

## Installing

1. Download the jars from the [latest release](https://github.com/Eggonomicon/GTNH_Mods/releases/latest) (with
   `SHA256SUMS.txt` to check them), or build them (below).
2. Put them in the server's `mods` folder and restart the server.
3. Each mod writes its config to `config/` on first start; see its README for the settings and commands.

Clients need nothing. To update a mod, stop the server, swap the jar, then start it again. Don't leave two versions of
the same mod in `mods`.

## Building

Each folder is its own Gradle project using the GTNH buildscript (RetroFuturaGradle).

```
cd valier-loot
./gradlew assemble          # first build needs internet; later ones can add --offline
```

- **Requirements:** JDK 25.
- **Output:** the jar is in `build/libs/` (the one without `-dev` or `-sources`).
- **Version:** set the `VERSION` environment variable to stamp it; otherwise it comes from git tags.
- **MM Free Power and Valier Claims** compile against jars from a GTNH 2.9 server (the Matter Manipulator and
  ServerUtilities, among others). Pass `-PgtnhInstance=<server folder>`; see their READMEs.

## Testing

Each mod has a self-test that runs inside a real GTNH server, using fake players: start a test copy of your server
with the mod's `-D...selftest=true` flag and run its self-test command. Each mod's README gives the exact steps and the
number of checks that passed. Never run the self-tests on a live server: they build test chests and areas in the world.
