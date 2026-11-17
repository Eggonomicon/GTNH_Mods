# Valier Client

**Client-side, optional.** A small add-on players can install to see more of what the Valier server's mods do. Servers
never need it, and nothing breaks for players without it.

## WAILA shows XP crafters

A Valier XP Crafter (from [Valier Tweaks](../valier-tweaks/README.md) 1.10.0 or later) is a GregTech fluid tank to the
game, so WAILA normally names it "Ultra Low Voltage Fluid Tank". With this mod, looking at one shows:

```
Valier XP Crafter
Makes: Tiny Pile of Gallium Dust
8,000 mB of liquid XP each
Working
Tank: 12,000 mB of Liquid XP
Holding 4,000 mB towards the next
Made: 1,234
```

- **Name:** "Valier XP Crafter", in the colour WAILA gives names.
- **What it makes** and the liquid XP each one takes. With full dust on (an emerald block in it) the cost line says
  "(full dust)".
- **What it is doing:** Working (green); Waiting for liquid XP, or Waiting: everything touching it is full (yellow);
  Waiting: nothing touching it takes items, Waiting: its tank holds something else, or XP crafters are off on this
  server (red). Nothing yet: "Makes nothing yet" and how to choose.
- **Its tank, the XP it already took towards the next craft, and how many it has made**, when there are any.
- **The tank's own WAILA lines** (facing and so on) follow as before.
- **Turning it off:** WAILA's settings, module "Valier", option "Valier XP crafters".

### How it works

Players' WAILA asks the server for a block's data while they look at it. Valier Tweaks adds a `ValierXpCrafter`
compound to that data for XP crafters (made ready on the server thread, at most a second old). This mod reads it and
changes WAILA's name and lines. Any other block, or a server without Valier Tweaks 1.10.0, sends no such compound, so
nothing changes. It uses no network channel of its own.

## Installing

1. Download `valierclient-1.0.0.jar` from the [latest release](https://github.com/Eggonomicon/GTNH_Mods/releases/latest)
   (or build it, below).
2. Put it in your GTNH 2.9.0 client's `mods` folder (the instance's `.minecraft/mods`) and restart the game.

Needs WAILA (part of GTNH) and GregTech. Without WAILA it does nothing. It connects to any server, with or without it.
Harmless on a server too (no client-only code), though it does nothing useful there.

## Build and test

- **Build:** `gradlew --offline assemble` (JDK 25). It compiles against a GTNH 2.9 instance's WAILA jar (see
  `dependencies.gradle`); point it at the instance with `-PgtnhInstance=<folder>`, by default `../../GTNH_Instance`.
- **Test:** Valier Tweaks' `valiertweaks selftest xpcrafter` checks it when this jar is in the test server's `mods`
  too. WAILA registers this mod's name and line providers on the server as well, so the test hands them the data
  WAILA's server sends for a real crafter and checks the lines a player would read: the name, what it makes, the cost,
  the status and its colour, the XP held and the count, the full dust cost, a crafter with nothing touching it, the
  option switched off, and a plain tank left alone. What WAILA draws on screen is only seen in game.
