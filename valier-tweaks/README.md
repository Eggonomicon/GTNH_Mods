# Valier Tweaks

Server-side. Clients need nothing (the optional [Valier Client](../valier-client/README.md) mod adds WAILA lines for
XP crafters). Small gameplay fixes for the Valier server.

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
- **Every side:** each stack is offered to all six neighbours in turn (below, above, north, south, west, east), and
  what doesn't fit in one goes on to the next. So with the chest below full, the loot goes into the chest beside it.
- **When everything is full** it keeps that batch itself (up to 27 stacks) and makes no more until the loot has gone.
  Nothing is ever dropped on the ground. Hoppers under it, EnderIO conduits set to extract and AE2 import buses can
  also pull from what it keeps. With nothing touching it, it makes nothing.
- **The loot:** the mob is made in memory only (never added to the world, so no AI, pathfinding or ticking), then put
  through what a player kill runs: the mob's normal drops, its equipment, the 2.5% rare drop (a wither skull, say), and
  the drop event other mods add loot through. So drops only a player kill gives (blaze rods) come too. There's no
  Looting and no XP. Big slimes split into small ones and those drop slimeballs, as when killed. A spawner that dresses
  its mobs (a skeleton's bow, Nether skeletons that are wither skeletons) does so here too.
- **No damaged items (1.4.0):** sneak-right-click a loot spawner with a **block of emerald** and it throws damaged
  items away: the worn swords, bows and armour mobs drop never come out, only undamaged loot. Anything damaged it was
  keeping goes too. The emerald block is kept in the spawner (not in creative): do it again to switch it off and get
  the block back, and it drops if the spawner is broken. Gear a mob picked up drops undamaged, so that still comes
  out.
- **Looting from lapis blocks (1.7.0):** sneak-right-click a loot spawner with a **block of lapis lazuli** to put
  one in. Its mobs' loot is then rolled with Looting, as if killed with a Looting sword: 1 block gives Looting I, 3
  give Looting II, 6 give Looting III (`lootSpawnerLootingBlocks`; more numbers allow higher levels). One goes in per
  click; the message says how many more the next level takes. The level shows in its status, in players' hover
  ("Valier Loot Spawner: Zombie, Looting II") and in the wrench's item. The blocks stay in it and drop when it is
  broken. Claims apply.
- **XP as bottles o' enchanting (1.7.0):** sneak-right-click a loot spawner with a **block of gold** and the XP its
  mobs would have given a player comes out as bottles o' enchanting with the loot, one per 7 XP
  (`lootSpawnerXpPerBottle`; a thrown bottle gives 3 to 11, 7 on average). What is left over waits for the next batch.
  The gold block is kept in it: do it again to switch it off and get it back; it drops if the spawner is broken.
- **Infernal Mobs (1.7.0):** a loot spawner's mobs can be Infernal Mobs elites, as real spawned mobs are (1 in 20 on
  a GTNH server, by Infernal Mobs' own settings). An elite's extra loot comes out too: the enchanted chainmail, iron,
  gold and diamond armour witches were known for in mob farms, tools, books, potions and the rest of Infernal Mobs'
  lists, all undamaged, and its extra XP counts for bottles. `lootSpawnerInfernalMobs` switches it off.
- **Special Mobs (1.7.0):** a loot spawner's mobs are swapped for Special Mobs variants as real spawned mobs are (by
  its own weights; on a GTNH server every blaze, and some zombies, creepers, skeletons and others), and the variants'
  own loot comes out. `lootSpawnerSpecialMobs` switches it off.
- **Speed from cursed earth (1.8.0):** sneak-right-click a loot spawner with Extra Utilities' **cursed earth** to put
  one in. Its batches then come faster: 1 gives Speed I (twice as often), 3 give Speed II (3 times), 6 give Speed III
  (4 times) (`lootSpawnerSpeedBlocks`; more numbers allow higher levels, each one more time as fast). The batch
  already counting down speeds up too. Cursed earth placed right under a loot spawner counts as one put in
  (`lootSpawnerSpeedBelow`), the way it speeds up a vanilla spawner. One goes in per click; the level shows in its
  status, players' hover and the wrench's item; the blocks stay in it and drop when it is broken. However fast, a
  loot spawner makes at most one batch a second. Claims apply.
- **Cursed earth makes no real mobs (1.8.0):** Extra Utilities runs the vanilla countdown of any spawner standing on
  cursed earth and, when it reaches 0, spawns the spawner's mobs itself (persistent ones, no player needed). A loot
  spawner never used that countdown, so cursed earth didn't speed it up, but one left idle on cursed earth for about
  half an hour could start spawning real mobs. Its countdown is now kept full every tick, so it never gets there.
- **Taking the blocks back (1.7.0):** sneak and hit (left-click) a loot spawner with an empty hand: every lapis,
  cursed earth, gold and emerald block in it comes back to you, and Looting, Speed, bottles and the damaged-items
  filter switch off. The gold and emerald blocks can also be switched off on their own by sneak-right-clicking with
  that block again.
- **Existing loot spawners** get all of this as soon as the new version runs: every batch is rolled fresh.
- **Picking one up (1.5.0):** sneak-right-click a loot spawner with a **wrench** (GregTech's, EnderIO's Yeta wrench,
  AE2's quartz wrenches, BuildCraft's) and it comes off as an item called **Valier Loot Spawner** with its mob, say
  "Valier Loot Spawner (Zombie)". The item keeps its mob, its settings and its emerald block. The loot it was keeping
  drops. Place the item anywhere and it is a loot spawner again. Claims apply, as when making one. Placing works by
  hand (and by anything that places blocks the way a player does).
- **Changing its mob with a spawn egg (1.6.0, off by default):** with `lootSpawnerSpawnEggs` on, sneak-right-click
  a loot spawner with a spawn egg and it makes that mob's loot from now on, the mobs dressed as a vanilla spawner
  dresses them. The egg is used up (not in creative). Claims apply and blocked mobs are refused. What it was keeping,
  its timer and its emerald block stay. Plain spawners are left alone. (Botania's tooltip tip about spawn eggs is its
  own creative-only feature, and it doesn't change a loot spawner.)
- **What players see (1.5.0):** clients need nothing. The WAILA hover reads "Monster Spawner (Valier Loot Spawner:
  Zombie)". The price: the little mob inside the cage isn't drawn. Clients draw it from that same name, and no mob has
  it, so the cage stays empty, the way vanilla shows a spawner of a mob that isn't installed. With
  `lootSpawnerHoverName` off, the mob spins in the cage again (faster as the next batch comes up) and the hover says
  "Monster Spawner (Zombie)".
- **Status:** sneak-right-click it with an empty hand to see its mob, its timing, whether it is working or waiting
  (and why) and when the next batch comes. On an ordinary spawner the same click says how to make one.
- **Protection:** it only works where the player could break the spawner, so claims keep others from converting
  yours. Ops always may.
- **No surprise mobs (fixed in 1.5.0):** EnderIO runs a spawner up to 6 times when a player breaks it, to spawn a last
  few mobs. The protection check above looks like a break to it. So a player who isn't an op could get a burst of real
  mobs when making a loot spawner, switching the emerald filter or picking one up, and breaking one did it for anyone.
  Now a loot spawner's own spawner part never runs: it needs a player within 0 blocks, and its real range is kept in
  the save. A plain spawner is held the same way for the moment it is being converted.
- **Breaking it** (or blowing it up) drops whatever it was keeping, and the emerald block if it has one. It is still a
  spawner: it drops nothing else of its own (EnderIO turns it into a broken spawner). To keep it, use a wrench.
  Removed by a command or WorldEdit, what it kept is gone.
- **Blocked mobs:** the Wither and the Ender Dragon (`lootSpawnerBlockedMobs`).

How it is stored: in the world save it is an ordinary spawner of the same mob, marked by a small compound
(`ValierLootSpawner`) inside its spawn data. Vanilla keeps that through everything (saves, dollies, NBT tools). The
mod turns a marked spawner back into a loot spawner as its chunk loads, or within 2 seconds if it turns up some other
way. Without the mod, it is simply an ordinary spawner of the same mob again.

## XP crafters (1.9.0, menu and store 1.11.0)

GTNH has crafting recipes made only of XP buckets: a tiny pile of gallium dust from 8, a tiny pile of arsenic from 7,
a small pile of brick dust from 4, iron dust from 2, and so on (27 of them). An XP crafter makes them from liquid XP.

- **Making one (1.11.0):** a shaped recipe (mirrored works too; `xpCrafterTank` sets the tank):

  | | | |
  |---|---|---|
  | ULV fluid tank | LV pump | LV conveyor |
  | ULV fluid tank | crafting table | any LV circuit |
  | ULV fluid tank | LV pump | LV robot arm |

  The tanks are GT++'s **Ultra Low Voltage Fluid Tank**, empty (one holding fluid doesn't fit, so none is lost) and not
  crafters already. The result is a "Valier XP Crafter": one of the tanks, renamed, with a tooltip saying what it makes.
  1.9.0 and 1.10.0 made it from one tank and a block of diamond; crafters made that way keep working.
- **Placed, it is still that tank:** clients and pipes see a ULV fluid tank (32,000 mB). Fill it with liquid XP the
  usual way (EnderIO, OpenBlocks; both call it `xpjuice`).
- **Its menu (1.11.0):** right-click it with an empty hand. It is a chest window the server fills, so every player sees
  it without a mod:
  - **Top row:** liquid XP (click it holding a bucket or cell of liquid XP to pour it in; shift-click pours the whole
    stack), a gauge of its tank, and its status (green working, yellow waiting, red stuck).
  - **The next three rows:** everything it can make (27), cheapest first, with the liquid XP each takes and what full
    dust would make. Click one to make it (it shines, "Making this now"); click it again to stop.
  - **The fifth row is its store:** what it made that nothing touching it took yet, up to 9 stacks. These are real
    items: take them (click, shift-click, a number key). Nothing can be put in.
  - **Bottom row:** full dust (click it holding a block of emerald to switch it on; click again to get the block back),
    pages if there are ever more than 27 things it can make, how many it has made, and how it works.
  - Clicks on the icons are buttons: nothing can be taken out of them, and the window is sent again after every click,
    so what the client guessed is put right. Where claims stop a player breaking it, they can look but not change it or
    take from its store.
- **Choosing what it makes** also works without the menu: sneak-right-click it with that item, such as a tiny pile of
  gallium dust. The item isn't used up. A dust no XP recipe makes is refused, with the list of what it can make.
  Claims apply.
- **What it does:** once a second it hands its store to the chests, inventories and pipes touching it (the same way
  loot spawners do), takes the liquid XP it needs from its tank and makes the item, as many as it has XP for (at most
  64 a second) and room in its store, and hands those over too. A recipe's cost is the liquid XP in its buckets: 1,000
  mB a bucket, so a tiny pile of gallium dust takes 8,000 mB. It takes XP only as it needs it, so with nothing to make,
  or its store full, the XP stays in the tank. Before 1.11.0 it had no store and made nothing with nowhere to put it.
- **Bigger than the tank:** XP it has taken towards the next craft is held by the crafter itself, so a craft can cost
  more than the tank holds (72,000 mB for full gallium dust).
- **Full dust:** sneak-right-click it with a **block of emerald** (`xpCrafterFullItem`, kept in it; not in creative)
  and it makes the whole dust instead of a tiny or small pile, for 9 or 4 times the liquid XP: gallium dust from
  72,000 mB, arsenic dust from 63,000, brick dust from 16,000 (`xpCrafterTinyTimes`, `xpCrafterSmallTimes`; GregTech
  makes a dust from 9 tiny or 4 small piles). Recipes that already make a whole item don't change. Sneak-right-click
  with the emerald block again, or sneak and hit it with an empty hand, to switch it off and get the block back.
- **Status:** sneak-right-click it with an empty hand: what it makes, the XP each takes, whether it is working or
  waiting and why, its tank and the XP it holds, how many it has made and what its store holds.
- **Breaking it** (with drops: a player, an explosion) drops the XP crafter item with its recipe, emerald block and
  the XP it held, and its store as items; placed again, it carries on. Removed without drops (a Matter Manipulator, a
  frame mover, a command), it is forgotten and stays a plain tank there, and its store drops where it was.
- **What players see:** the crafting table shows the crafter in its output slot (the server sends it; clients don't
  know the recipe, so NEI shows it only with [Valier Client](../valier-client/README.md) 1.1.0). The item's tooltip, the
  menu and the status say what it makes. WAILA shows the plain tank: it works out names on the player's computer, and
  no tank in GTNH shows a name the server sends.
- **WAILA with Valier Client (1.10.0):** with WAILA on the server, a crafter adds what it makes and is doing to the
  block data WAILA sends a player looking at it (a `ValierXpCrafter` compound: the item, the cost, the status, its tank,
  the XP held, the count, and from 1.11.0 how many items its store holds). Players with the optional [Valier Client](../valier-client/README.md) mod see "Valier XP
  Crafter" and those lines; everyone else's WAILA ignores it. WAILA asks on its network thread, so the server keeps
  each crafter's info ready (refreshed every second and whenever a crafter changes) instead of reading the world
  there.

Stored in each world's `data/valiertweaks_xpcrafters.dat`, by position. Without the mod they are plain tanks.

## Whole steel dust from chain armour (1.9.0)

GregTech macerates a chain helmet into 5 small piles of steel dust and chain leggings into 7. With
`chainArmourFullDust` the helmet gives 1 steel dust and the leggings 2 (boots 1 and chestplates 2, as before), so a
full set still gives 6. Every GregTech macerator uses these recipes; they are changed at server start. Clients' NEI
still shows the small piles. The arc furnace (nuggets) is unchanged.

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
| `lootSpawnerFilterItem` | `minecraft:emerald_block` | What makes a loot spawner throw damaged items away (kept in it, given back when switched off). Empty or not an item: it can't be switched (ones already set stay set). |
| `lootSpawnerFilterItemCount` | `1` | How many of that item. |
| `lootSpawnerLootingItem` | `minecraft:lapis_block` | What gives a loot spawner Looting, one per click. Empty or not an item: none can be put in. |
| `lootSpawnerLootingBlocks` | `1, 3, 6` | How many it needs for Looting I, II, III (rising; more numbers, higher levels). |
| `lootSpawnerSpeedItem` | `ExtraUtilities:cursedearthside:0` | What gives a loot spawner Speed, one per click. Empty or not an item: none can be put in. |
| `lootSpawnerSpeedBlocks` | `1, 3, 6` | How many it needs for Speed I, II, III: 2, 3 and 4 times as often (rising; more numbers, higher levels). |
| `lootSpawnerSpeedBelow` | `true` | That item as a block right under a loot spawner counts as one put in. |
| `lootSpawnerXpItem` | `minecraft:gold_block` | What switches bottles o' enchanting on (kept in it, given back when switched off). Empty or not an item: it can't be switched. |
| `lootSpawnerXpItemCount` | `1` | How many of that item. |
| `lootSpawnerXpPerBottle` | `7` | XP per bottle o' enchanting. |
| `lootSpawnerInfernalMobs` | `true` | Infernal Mobs elites and their loot, as with real spawned mobs. |
| `lootSpawnerSpecialMobs` | `true` | Special Mobs variants and their loot, as with real spawned mobs. |
| `lootSpawnerWrench` | `true` | Sneak-right-click with a wrench picks a loot spawner up as a Valier Loot Spawner item. |
| `lootSpawnerSpawnEggs` | `false` | Sneak-right-click with a spawn egg changes a loot spawner's mob (egg used up). |
| `lootSpawnerHoverName` | `true` | The WAILA hover says "Valier Loot Spawner: Zombie" (and no mob is drawn in the cage). Off: the mob is drawn and the hover names only the mob. |
| `xpcrafter` / `xpCrafter` | `true` | XP crafters can be made and work. Off: none can be made, and existing ones stop (keeping what they know). |
| `xpCrafterTank` | `gregtech:gt.blockmachines:817` | The GregTech tank three of which (with two LV pumps, an LV conveyor, an LV robot arm, a crafting table and an LV circuit) craft one (GT++'s ULV fluid tank). Any GregTech tank works. Not an item: none can be made. (1.11.0 removed `xpCrafterItem`, the diamond block of the old recipe.) |
| `xpCrafterFluids` | `xpjuice` | Fluids that count as liquid XP. Recipes made only of containers of these are what it can make. |
| `xpCrafterFullItem` | `minecraft:emerald_block` | Switches full dust on (sneak-right-click, or its menu; kept in it, given back when switched off). |
| `xpCrafterTinyTimes` | `9` | Times the XP for a whole dust instead of a tiny pile. |
| `xpCrafterSmallTimes` | `4` | Times the XP for a whole dust instead of a small pile. |
| `macerator` / `chainArmourFullDust` | `true` | Chain helmets macerate into 1 steel dust, chain leggings into 2. |

## Build and test

- **Build:** `gradlew --offline assemble` (JDK 25). It compiles against a GTNH 2.9 server's Tinkers' Construct,
  UniMixins, GregTech, GTNHLib, ModularUI 2, AE2 and WAILA jars (see `dependencies.gradle`). Point it at your server folder with
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
  - **Damaged items:** an emerald block switches it to throwing damaged items away (one block kept in it, a worn
    sword it was keeping gone, the status says so, it survives a save) and switches it back, giving the block back.
    A protected one can't be switched, and a plain spawner only says how. Zombies with swords that drop worn give
    worn swords, none once it throws damaged items away, and swords that drop undamaged still come out.
  - **Wrench:** a real GregTech steel wrench counts (its hammer and a pickaxe don't). A sneaking fake player picks a
    loot spawner up as a "Valier Loot Spawner (Zombie)" item with the zombie's id, the loot it kept dropping. Not
    without sneaking, not where protected, not a plain spawner, not with `lootSpawnerWrench` off. Placed through
    Forge's own placement, the item is the same loot spawner (mob, its own settings, emerald block, a fresh wait), and
    NEI naming another mob for it changes nothing. A claim that stops placing keeps the item, and with loot spawners off
    it places a plain zombie spawner, dropping the emerald block. The hover packet names it and carries no spawn data;
    with `lootSpawnerHoverName` off it carries the mob and its spawn data.
  - **Spawn eggs:** with `lootSpawnerSpawnEggs` off (the default) an egg changes nothing. On, a skeleton egg makes a
    zombie loot spawner a skeleton one (one egg used, its loot and emerald block kept, saved as a skeleton spawner,
    the hover renamed). The same mob again, an egg with no mob, a blocked mob and a protected spawner use nothing, and
    a plain spawner is left alone.
  - **Every side:** with the chest below full, a batch goes into the chest beside it.
  - **Gold block:** XP is counted as for a player kill (10 blazes give 100, cows 1 to 3 each). Without a gold block
    no bottles; with one (kept in it), 4 blazes (40 XP) give 5 bottles with 5 XP kept, the next 4 give 6 more with
    3 kept. The status says so, it survives a save, a protected one can't be switched, switching it off gives the
    block back, and on a plain spawner it only says how.
  - **Lapis blocks:** 1, 3 and 6 give Looting I, II and III (the setting changes that), and zombies rolled with
    Looting III drop far more rotten flesh. One block per click, none where protected, none past the most. The
    status, the hover and the wrench's item say Looting III, a placed one keeps it, and breaking it drops its 6
    blocks.
  - **Infernal Mobs:** with its rarity set to 1 for the test, every witch is an elite: armour comes out, none of it
    damaged, the elites' extra XP counts, and none stay in its list. With `lootSpawnerInfernalMobs` off, no elites.
    (It is off for the rest of the test, so counts stay exact.)
  - **Special Mobs:** every blaze and some zombies come as its variants, killed for loot and never in the world or
    in its replacement queue. With `lootSpawnerSpecialMobs` off, plain blazes. (Off for the rest of the test too.)
  - **Taking back:** a sneaking hit with an empty hand gives back 3 lapis, the gold and the emerald block and switches
    those off. Not without sneaking, not where protected, and an empty one says so.
  - **Cursed earth:** 1, 3 and 6 give Speed I, II and III; batches of a 400-tick spawner come every 200 then 100
    ticks, and the one counting down speeds up too. One per click, none where protected, none past the most. The
    status, the hover, the wrench's item and a save keep Speed III; a sneaking hit gives the 6 back and its normal
    speed. One under it counts as one (not with `lootSpawnerSpeedBelow` off). On a plain spawner, only a hint. Extra Utilities
    counts a plain spawner on cursed earth down 100 at a time (300 to 200); a loot spawner's count is full again after
    each of its own ticks, so it never reaches 0 and no real mob comes out.
  - **No surprise mobs:** with a player beside them, a plain spawner counts as active and a loot spawner doesn't. Run
    6 times the way EnderIO does on a break, the loot spawner spawns nothing. A plain spawner isn't active while its
    conversion is being asked about. The real player range (16) is still saved and sent to clients.
  - **Saving:** it saves as a plain spawner with its loot inside and would load as one without the mod. It comes back
    after a dolly-style move and as its chunk loads, and clients get a plain spawner packet without the loot data.
  - **Settings:** a misspelled conversion item makes none (not free ones); blown up, what it kept (and its emerald
    block) drops; switched off, it is a plain spawner again (dressed as before, ready to spawn) and what it kept drops
    on top, emerald block included. A real chicken
    nearby is never taken as a baby zombie's mount during 2000 zombie rolls.
  - **Over 70 ticks:** a broken one drops what it kept and its emerald block (a permission check alone doesn't). Its chunk unloads and loads
    again with it still working, and one left running fills its chest on its own.
- **XP crafter self-test:** `valiertweaks selftest xpcrafter` (same flag), in an empty chunk of the test world.
  - **Recipes:** GTNH's XP bucket recipes are found with their costs (tiny gallium 8,000 mB, tiny arsenic 7,000, small
    brick 4,000, iron dust 2,000, charcoal 1,000). Full dust: 9 times for a tiny pile (gallium dust), 4 times for a
    small one (brick dust), none for iron dust.
  - **Crafting:** LV circuits are in the ore dictionary; three ULV fluid tanks, two LV pumps, an LV conveyor, an LV
    robot arm, a crafting table and an LV circuit make the crafter item (name and tooltip), mirrored too. Not with a
    conveyor for the arm, an LV tank, a crafter or a tank holding fluid among the tanks, nor the old tank and diamond
    block. In a crafting table it is in the output slot, the one the server sends to players. With Valier Client
    loaded, its copy of the recipe (for NEI) takes the same grid and not the wrong one.
  - **Working:** placed as a player places it, it is a crafter. An empty hand shows its status; a tiny pile of gallium
    chooses that (not without sneaking, not where protected); titanium dust is refused. 5,000 mB makes nothing yet,
    3,000 more make one into the chest, 20,000 make two more and keep 4,000. An emerald block switches to gallium dust
    from 72,000 mB, taken a tank at a time.
  - **Item and edge cases:** broken, it drops as the item with its recipe, emerald block and 4,000 mB, and placed again
    it carries on. A sneaking empty-hand hit gives the emerald block back. With nothing touching it what it makes waits
    in its store; with its store full no XP is taken (and it says why); with the chest full too it waits; with room in
    the chest a stack of its store goes in and it makes another. Its record saves, store too; removed without drops,
    it is forgotten and its 513 stored items drop where it was. A plain ULV tank only gets a hint.
  - **Menu (1.11.0), on a crafter of its own:** an empty-hand right-click opens it (not with something in hand). It
    shows the 27 things it can make, cheapest first, with costs, its empty tank and its status, and no pages.
    Clicking tiny gallium dust chooses it (it shines), again stops it, a shift-click chooses too. Icons can't be taken
    (click, number key, Q) or covered. A liquid XP bucket poured in fills the tank, the gauge shows it and the empty
    bucket stays on the cursor; dirt is refused. The 2 it makes from 16,000 mB wait in its store and show in the menu;
    with its store full it takes no XP and says why in red. Its store can be taken from (shift-click, click, number
    key) but not filled. Full dust from emerald blocks on the cursor (the gallium icon becomes gallium dust at 72,000
    mB) and back. Where protected it can be looked at but not changed or taken from. Further than 8 blocks it closes.
    Broken, its store drops with the crafter item.
  - **WAILA (1.10.0):** the data WAILA's server sends for the crafter (every provider registered for the tank, as
    WAILA asks them) says what it makes and the cost as soon as it is chosen; working, the 4,000 mB held and 3 made;
    gallium dust at 72,000 mB with full dust on; with its store full and nothing touching it, why, the 8,000 mB in its
    tank and the 576 stored items; nothing
    once it is forgotten, and nothing for a plain tank. With `valierclient` in the test server's `mods` too, Valier
    Client's name and line providers are run on that data, and the lines a player reads are checked (see its README).
  - **Chain armour:** the macerator recipes give 1, 2, 1 and 2 steel dust for helmet, leggings, boots and chestplate,
    also as a macerator looks them up.
