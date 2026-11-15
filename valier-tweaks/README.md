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

## Loot spawners (1.3.0)

A mob farm spawns real mobs, which then have to be killed and their drops picked up: lag, and on a peaceful server
hostile spawners do nothing at all. A loot spawner skips the mobs and makes just their loot:

- **Making one:** sneak-right-click a mob spawner with a **block of diamond** (used up; not in creative). Any spawner
  works: one found in a dungeon or fortress, or one moved with JABBA's diamond dolly. The diamond block isn't placed.
- **What it does:** it stops spawning mobs. Every spawner cycle (its own timing; for a vanilla spawner, up to 4 mobs
  every 10 to 40 seconds) it makes the loot those mobs would have dropped and puts it into the inventories touching
  it, on any side: chests (double chests too), barrels, drawers, hoppers, machines, AE2 interfaces, GregTech item
  pipes, EnderIO item conduits and BuildCraft pipes.
- **When everything is full** it keeps that batch itself (up to 27 stacks) and makes no more until the loot has gone.
  Nothing is ever dropped on the ground. Hoppers under it, EnderIO conduits set to extract and AE2 import buses can
  also pull from what it keeps. With nothing touching it, it makes nothing.
- **The loot:** the mob is made in memory only (never added to the world, so no AI, pathfinding or ticking), then put
  through what a player kill runs: the mob's normal drops, its equipment, the 2.5% rare drop (a wither skull, say), and
  the drop event other mods add loot through. So drops only a player kill gives (blaze rods) come too. There's no
  Looting and no XP. Big slimes split into small ones and those drop slimeballs, as when killed. A spawner that dresses
  its mobs (a skeleton's bow, Nether skeletons that are wither skeletons) does so here too.
- **Looks the same:** clients need nothing; it is an ordinary spawner to them. The little mob spins faster as the next
  batch comes up.
- **Status:** sneak-right-click it with an empty hand to see its mob, its timing, whether it is working or waiting
  (and why) and when the next batch comes. On an ordinary spawner the same click says how to make one.
- **Protection:** it only works where the player could break the spawner, so claims keep others from converting
  yours. Ops always may.
- **Breaking it** (or blowing it up) drops whatever it was keeping. It is still a spawner: it drops nothing else, as
  before. Removed by a command or WorldEdit, what it kept is gone.
- **Blocked mobs:** the Wither and the Ender Dragon (`lootSpawnerBlockedMobs`).

How it is stored: in the world save it is an ordinary spawner of the same mob, marked by a small compound
(`ValierLootSpawner`) inside its spawn data. Vanilla keeps that through everything (saves, dollies, NBT tools). The
mod turns a marked spawner back into a loot spawner as its chunk loads, or within 2 seconds if it turns up some other
way. Without the mod, it is simply an ordinary spawner of the same mob again.

## Config (`config/valiertweaks.cfg`)

| Setting | Default | Meaning |
|---|---|---|
| `finishLumberAxeTrees` | `true` | Fell the rest of a tree when a lumber axe chop stops early. |
| `maxExtraLogs` | `1024` | At most this many extra logs per tree. |
| `logsPerTick` | `128` | Extra logs broken per tick (Tinkers' own rate). |
| `pdim` / `peacefulDimensions` | `true` | The `/pdim peaceful` switch. Off: no dimension is kept peaceful. |
| `boilers` / `noBoilerAsh` | `true` | Solid fuel boilers make no ash. Off: GregTech's normal ash. |
| `lootspawner` / `lootSpawners` | `true` | Loot spawners work. Off: none can be made, and existing ones become ordinary spawners of the same mob again within 2 seconds of loading, dropping what they kept. |
| `lootSpawnerItem` | `minecraft:diamond_block` | What it takes to make one (`mod:item` or `mod:item:meta`). Empty: free, with an empty hand. A name that isn't an item: none can be made (a warning is logged). |
| `lootSpawnerItemCount` | `1` | How many of that item. |
| `lootSpawnerSpeed` | `1.0` | 1 = as fast as the spawner would spawn mobs; 2 = twice as often. |
| `lootSpawnerNeedsPlayer` | `false` | Only work with a player within 16 blocks, like a vanilla spawner. Off: whenever its chunk is loaded. |
| `lootSpawnerBlockedMobs` | `WitherBoss`, `EnderDragon` | Mobs whose spawners can't become loot spawners. |

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
- **Loot spawner self-test:** `valiertweaks selftest lootspawner` (same flag), in an empty chunk of the test world.
  - **Clicks:** a sneaking fake player with diamond blocks makes one (one block used, none placed). Not sneaking does
    nothing, an empty hand shows the status or a hint, and a protected spawner or a Wither spawner is refused.
  - **Loot:** zombies, blazes (blaze rods), spiders, slimes (splitting) and wither skeletons from spawn data (the rare
    skull, no arrows). Nothing for a blocked mob or an arrow, and nothing joins the world.
  - **Output:** into a chest; kept when the chest is full and handed over later; nothing with nothing touching it;
    into a hopper beside it; pulled by a hopper under it; nothing can be put into it; a GregTech item pipe connects
    to it and takes its loot.
  - **Saving:** it saves as a plain spawner with its loot inside and would load as one without the mod. It comes back
    after a dolly-style move and as its chunk loads, and clients get a plain spawner packet.
  - **Settings:** a misspelled conversion item makes none (not free ones); blown up, what it kept drops; switched off,
    it is a plain spawner again (dressed as before, ready to spawn) and what it kept drops on top. A real chicken
    nearby is never taken as a baby zombie's mount during 2000 zombie rolls.
  - **Over 70 ticks:** a broken one drops what it kept (a permission check alone doesn't). Its chunk unloads and loads
    again with it still working, and one left running fills its chest on its own.
