# Valier Tweaks

Server-side. Clients need nothing. Small gameplay fixes for the Valier server.

## Lumber axes fell the whole tree

A Tinkers' Construct **lumber axe** fells a tree one log at a time and stops the moment the axe breaks, or the player
switches items. The rest of the tree is left floating. Tinkers has no setting for this.

With this mod, once a lumber axe chop stops early, the rest of the tree comes down too:

- **Same tree shape:** it walks the tree the way Tinkers does (each log, then the 3x3 around it on its own layer and
  the one above), starting from the logs Tinkers already broke.
- **Like breaking by hand:** the extra logs drop normally and the axe takes no more wear (it's broken, or not in
  hand).
- **Protection respected:** each extra log fires the normal block-break event, so claims and other protection mods
  still apply. A log they protect stays, and the rest of the tree still comes down.
- **Only real tree chops:** only chops Tinkers itself treated as a tree are finished (enough leaves at the top, player
  not sneaking). Breaking a single log, or a log wall without leaves, works as before.
- **A cap:** at most `maxExtraLogs` (1024) extra logs per tree. A lumber axe also fells logs that touch the tree, such as
  a log wall built against it.

The server log says when it finishes a tree: `Finished a tree for <player> at x y z: N more log(s), after the lumber
axe broke`.

Tinkers' Construct is optional: without it, the mod does nothing. It finds Tinkers' classes by name and has no hard
dependency.

## Peaceful personal dimensions (1.1.0)

Minecraft 1.7.10 has one difficulty for the whole server. This lets a player make just their own Personal Space
dimension peaceful:

| Command | What it does |
|---|---|
| `/pdim peaceful` | Shows whether the personal dimension you're in is peaceful. |
| `/pdim peaceful on` / `off` | Switches it for the personal dimension you're in. |
| `/pdim peaceful on\|off <dimension>` | Ops and the console: switch any loaded personal dimension. |
| `/pdim list` | Lists the peaceful dimensions. |

- **While on:** no hostile mob (anything Minecraft counts as a monster) can spawn there, whatever spawns it: natural
  spawning, spawners, eggs, commands or other mods. Hostile mobs already there, such as ones saved in chunks, are
  removed within a quarter of a second and drop nothing. Animals are left alone. As with peaceful difficulty, that
  includes bosses such as the Wither, so switch it off for a boss fight.
- **Who may switch it:** anyone who could break blocks where they stand, the same rule Personal Space uses for its
  portal settings. Claim your dimension's chunks to keep visitors from changing it. Ops always may.
- **Only personal dimensions:** it refuses everywhere else (the overworld, the Nether, planets).
- **Saved** with the world (`data/valiertweaks_peaceful.dat`).

Personal Space is optional: without it, `/pdim peaceful` simply finds no personal dimensions.

## No ash from boilers (1.2.0)

GregTech's solid fuel boilers put ash or dark ash into their output slot as they burn coal, charcoal, coke and so on.
With this mod they don't:

- **Which boilers:** the small coal boiler and the high pressure coal boiler, and GT++'s LV, MV and HV advanced
  boilers. GregTech's other boilers (large boilers, lava, solar) never made ash.
- **Fuel works as before:** the same items count as fuel, last as long and make as much steam. Only the ash is gone.
- **Ash already in a boiler** stays until someone takes it out.
- **Not changed:** coke ovens make no ash anyway. Machines whose recipes list ash, such as the Bricked Blast Furnace,
  the Pyrolyse Oven and the Industrial Coke Oven, still make it.

It patches two GregTech methods at load time (mixins, loaded through UniMixins' late mixin loader, only when GregTech
is installed):

- **Small and high pressure coal boilers:** `MTEBoilerBronze.combustFuel`, which picks how much ash a burned item
  leaves, returns none.
- **Advanced boilers:** in `MTEAdvancedBoilerBase.consumeFuel`, the two calls that add tiny ash to the output slot add
  nothing.

## Config (`config/valiertweaks.cfg`)

| Setting | Default | Meaning |
|---|---|---|
| `finishLumberAxeTrees` | `true` | Fell the rest of a tree when a lumber axe chop stops early. |
| `maxExtraLogs` | `1024` | At most this many extra logs per tree. |
| `logsPerTick` | `128` | Extra logs broken per tick (Tinkers' own rate). |
| `pdim` / `peacefulDimensions` | `true` | The `/pdim peaceful` switch. Off: no dimension is kept peaceful. |
| `boilers` / `noBoilerAsh` | `true` | Solid fuel boilers make no ash. Off: GregTech's normal ash. |

## Build and test

- **Build:** `gradlew --offline assemble` (JDK 25). It compiles against a GTNH 2.9 server's Tinkers' Construct,
  UniMixins, GregTech, GTNHLib and ModularUI 2 jars (see `dependencies.gradle`). Point it at your server folder with
  `-PgtnhInstance=<folder>`; by default it is `../../GTNH_Instance`.
- **Self-test:** on a test server started with `-Dvaliertweaks.selftest=true`, run `valiertweaks selftest trees`. It
  builds four trees and a log wall, and fake players chop them with real Tinkers lumber axes:
  - A, with an axe that has 3 uses left: the whole tree falls, every log drops, and the axe broke.
  - B, with a fresh axe: Tinkers fells it all, and this mod does nothing.
  - C, a log wall without leaves: no more than Tinkers' own 3x3 goes.
  - D, a sneaking player: only the clicked log goes.
  - E, with one log protected by a cancelled break event: everything but that log falls.

  The results are checked 3 seconds later, and the area is cleared afterwards.
- **Peaceful self-test:** `valiertweaks selftest peaceful` (same flag). The test world's overworld stands in for a
  personal dimension.
  - **Command:** it refuses outside a personal dimension and refuses a non-op naming a dimension. A player may switch
    it only where a break would be allowed.
  - **Spawning:** with peaceful on, a new zombie can't spawn, natural spawning is denied and a cow is unaffected. A
    zombie that was already there is gone 10 ticks later.
  - **Saving:** the list saves and loads.
- **Boiler self-test:** `valiertweaks selftest boilers` (same flag). It places a small coal boiler, a high pressure coal
  boiler and an LV advanced boiler as a player would, then runs coal through each boiler's own fuel step.
  - **Ash off (the setting):** 60 coal burn and each heats the boiler, and the ash slot stays empty. 4 coal blocks
    (normally a full dust of dark ash each) leave none either.
  - **Ash back on**, to show the test reaches GregTech's ash code: the same 60 coal leave ash, and a coal block leaves
    one dust of dark ash.
  - The setting is put back and the boilers are removed.
