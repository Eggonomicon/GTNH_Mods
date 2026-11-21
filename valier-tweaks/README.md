# Valier Tweaks

Gameplay additions for the Valier server. **Since 2.0.0 it is needed on the server and on every player's computer**
(it has its own blocks), and it includes what the old Valier Client mod did: players delete `valierclient-*.jar`.

## 2.0.0: one jar, its own blocks, and pipes

- **One jar for everyone.** Put `valiertweaks-2.0.x.jar` in the server's and every player's `mods` folder, and delete
  `valierclient-*.jar` (if it is left in, Forge stops at start-up with a "duplicate mods" screen naming both files).
  The newest version is always at https://github.com/Eggonomicon/GTNH_Mods/releases/latest/download/valiertweaks.jar
  (the same jar, named `valiertweaks.jar`; keep only one Valier Tweaks jar in `mods`).
- **Joining:** a server with Valier Tweaks 2.0 asks a joining player for 2.0.x (any 2.0 version: fixes that only change
  the server don't need a new download). A player with it can still join servers without it.
- **Loot spawners and XP crafters are real blocks now** (Valier Loot Spawner, Valier XP Crafter). Existing ones become
  the new blocks where they stand the first time their chunk loads with 2.0.0, keeping everything: mob, settings,
  upgrades, paid blocks, loot held; recipe, XP held, tank, slots, store. Old items (a wrench's vanilla spawner item, a
  crafter that was a GregTech tank item) place the new blocks. All clicks and recipes are as before.
- **Valier pipes:** item and fluid pipes in four tiers, see below.
- **Removing the mod later** deletes its blocks from the world. `/valiertweaks revertblocks confirm` (ops, console)
  first turns loot spawners and XP crafters back into marked vanilla spawners and GregTech tanks (crafters' stores drop
  on top; loot spawners only in loaded chunks; pipes can't be kept).

## Valier pipes (2.0.0)

Item pipes and fluid pipes that move things straight from block to block: nothing travels along the pipe, so a pipe
holds nothing and breaking one spills nothing. They are built to cost the server as little as possible:

- **Pipes never tick.** Placing, breaking or changing one only marks it; the pipes joined to it are worked out again at
  the end of that tick. Idle pipes cost nothing at all.
- **Only Extract and Request faces work**, each on its tier's interval, from one queue ordered by when each is due. A
  face that finds nothing to do waits twice as long each time (up to `pipeMaxSleepTicks`, 5 s); a change inside the
  block it touches wakes it at its next interval. All pipe work is capped at `pipeBudgetMicros` a tick; anything still
  due waits for the next tick.
- **Never loads a chunk.** A face whose block is in an unloaded chunk waits for that chunk.
- `/valiertweaks pipes` (ops) shows the networks, the working and sleeping faces, transfers and the time spent.

**Laying them:** a pipe joins pipes of its kind (any tier) and any block with an inventory (item pipes) or a tank (fluid
pipes) beside it. Sneak-right-click with a wrench to shut a side, or open it again. A pipe never joins a block in a
chunk claimed by a team its owner (whoever placed it) isn't in or allied with.

**Faces.** Where a pipe touches a block, a coloured plate shows that face's mode:

| Mode | Colour | What it does |
|---|---|---|
| Provide (new faces) | green | Passive. Request faces on the network may take what they ask for from this block. "Overflow" on: it also takes what Extract faces can't put anywhere else (a barrel as the network's buffer). |
| Insert | blue | Passive. Extract faces put things into this block. |
| Extract | orange | Every interval, takes up to its amount out of this block (by its filter) and sends it to Insert faces, then to Request faces short of it, then to overflow Provide faces. |
| Request | purple | Keeps what its filter lists stocked in this block (for each filter slot, a keep amount: a stack by default), taking only what's missing from Provide faces. |
| Off | grey | Leaves the block alone. |

Nothing moves until some face is Extract or Request. A chest on a Provide face (no setup) and a machine on a Request
face asking for iron: the machine is kept stocked from the chest.

**A face's menu:** right-click the arm touching a block (sneak-right-click with an empty hand cycles the mode). It has
the mode; the amount per transfer (up to the tier's most); the priority (higher first; equal ones take turns); redstone
(always, only with a signal, only without); the filter (9 ghost slots: click with an item to set, empty-handed to clear;
whitelist or blacklist; same or any damage value; same or any NBT; ore dictionary); for Request faces the keep amount of
the selected slot; for Provide faces overflow; and the **tag**. Fluid faces filter by fluid: a filter item holding one
(a bucket, a cell) names it; Request keeps that many mB (1,000 by default).

**Tags:** a face only trades with faces carrying the same tag (no tag with no tag). On Basic, Advanced and Elite pipes
that means channels within the pipes joined together. On **Wireless** pipes it means links: a tagged Wireless face
trades with every Wireless face of the same tag, anywhere, in any dimension (`pipeWirelessCrossDimension`), as long as
both are in loaded chunks. "001 in, 001 out". Tags are private to the pipe owner's ServerUtilities team (or to the owner
alone without a team), so nobody else can send to or take from yours.

**Exact transfers:** it takes out first (what really came out is what counts: some barrels and machines give a little
more or less than they show), then puts in as a vanilla hopper does (stacks first, never past the block's stack limit).
What nothing took goes back; if it can't, the face holds it and hands it on first next time (a few stacks at most,
`pipeHeldStacks`; it drops if the pipe breaks). A double chest is one inventory, and a face never moves things into the
block it takes them from. Slots and tanks follow the block's own rules for the side the pipe touches.

**Tiers and recipes** (amounts and intervals in the config, `pipes` section):

| Tier | Per transfer | Every | Recipe |
|---|---|---|---|
| Basic | 8 items / 1,000 mB | 20 ticks | 6 bronze plates, 2 glass panes, a hopper (item) or bucket (fluid): 8 pipes |
| Advanced | 32 / 4,000 mB | 10 ticks | 8 Basic around an LV conveyor (item) or LV pump (fluid): 8 |
| Elite | 64 / 16,000 mB | 5 ticks | 8 Advanced around an MV conveyor or MV pump: 8 |
| Wireless | 64 / 16,000 mB | 5 ticks | an Elite pipe, an ender pearl, an MV emitter and an MV sensor: 1 |

Loot spawners and XP crafters work with them: pipes can take from both, and one with only a Valier pipe pulling from it
(Extract or Provide) works (before, with nothing to push into, it would wait for "no output").

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

## Railcraft boilers don't explode (1.13.0)

Railcraft blows a steam boiler up when water reaches it while it is **superheated** (300 or more) and its water tank
is **empty**: typically the water supply ran out while the firebox kept burning, and then the water came back. Its
explosion has no entity behind it, so `mobGriefing false` doesn't stop it, and neither do GregTech's machine
explosion settings; Railcraft itself has no setting for it.

With this mod (`noRailcraftBoilerExplosions`, on by default) it doesn't explode:

- **What happens instead:** the boiler hisses and puffs smoke, takes the water and cools to 299 (as Railcraft has it
  just before its explosion), and keeps working. Nothing breaks. The server log gets a line with where it was.
- **Only Railcraft boilers:** it cancels an explosion (Forge's `ExplosionEvent.Start`) only when it starts exactly at a
  Railcraft boiler block (a firebox or tank) and has no entity behind it. Every other explosion is untouched.
- **Without Railcraft** it does nothing. Clients need nothing.

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
  From 1.14.0 storage (chests, barrels, drawers, machines) is offered everything first, and pipes and conduits only get
  what storage won't take.
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
- **What players see (2.0.0):** the Valier Loot Spawner block (a gold-framed cage) with its mob spinning inside
  (faster as the next batch comes up), and WAILA's name "Valier Loot Spawner (Zombie, Looting II)". 1.5.0 to 1.14.0
  showed a vanilla spawner with an empty cage instead (`lootSpawnerHoverName`, gone in 2.0.0).
- **Status:** sneak-right-click it with an empty hand to see its mob, its timing, whether it is working or waiting
  (and why) and when the next batch comes. On an ordinary spawner the same click says how to make one.
- **Protection:** it only works where the player could break the spawner, so claims keep others from converting
  yours. Ops always may.
- **No surprise mobs (fixed in 1.5.0):** EnderIO runs a spawner up to 6 times when a player breaks it, to spawn a last
  few mobs. The protection check above looks like a break to it. So a player who isn't an op could get a burst of real
  mobs when making a loot spawner, switching the emerald filter or picking one up, and breaking one did it for anyone.
  Now a loot spawner's own spawner part never runs: it needs a player within 0 blocks, and its real range is kept in
  the save. A plain spawner is held the same way for the moment it is being converted.
- **Breaking it (2.0.0):** players can't (in survival), and explosions and the Wither can't either: move it with a
  wrench. Creative players, a Matter Manipulator or another mod removing it: what it was keeping and the blocks paid
  into it drop. Before 2.0.0 a pickaxe broke it into EnderIO's broken spawner.
- **Blocked mobs:** the Wither and the Ender Dragon (`lootSpawnerBlockedMobs`).

How it is stored: since 2.0.0 it is its own block and tile (`valiertweaks.LootSpawner`), its state still in a small
compound (`ValierLootSpawner`) inside its spawn data. Before 2.0.0 it was an ordinary spawner marked that way; such a
spawner (an old save, or one a dolly moved) becomes the block as its chunk loads, or within 2 seconds if it turns up
some other way. Players' computers get a plain spawner tile for it, so vanilla draws the mob.

## XP crafters (1.9.0, menu and store 1.11.0, essence berries 1.12.0, cost settings 1.14.0)

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
- **Placed, it is the Valier XP Crafter block (2.0.0; before, it stayed a ULV fluid tank).** Its tank holds 32,000
  mB and takes only liquid XP, from pipes, buckets and its menu (EnderIO and OpenBlocks call it `xpjuice`). Its input
  slot takes only liquid XP containers (emptied into the tank each second, the empties kept for pipes to take) and
  essence berries; pipes and hoppers can take its empties and its store. A crafter that was a GregTech tank becomes
  this block within a second of its chunk loading, keeping its fluid (whatever it was, however much), slots and
  record; its covers drop.
- **Its menu (1.11.0):** right-click it, with an empty hand or holding anything except a GregTech tool (wrench,
  screwdriver, mallet, hammer, crowbar, wire cutter, soldering iron, jackhammer) or a cover, which go to the tank as
  GregTech has them (1.11.0 needed an empty hand). It is a chest window the server fills, so every player sees it
  without a mod:
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
  loot spawners do: storage first, pipes only what storage won't take, from 1.14.0, so a pipe bringing it berries
  doesn't carry its output off into other crafters' tanks), takes the liquid XP it needs from its tank and makes the item, as many as it has XP for (at most
  64 a second) and room in its store, and hands those over too. A recipe's cost is the liquid XP in its buckets: 1,000
  mB a bucket, so a tiny pile of gallium dust takes 8,000 mB. It takes XP only as it needs it, so with nothing to make,
  or its store full, the XP stays in the tank. Before 1.11.0 it had no store and made nothing with nowhere to put it.
- **Solid XP (1.12.0):** when its tank hasn't enough liquid XP (or holds another fluid), it takes **essence berries**
  (Tinkers' Concentrated Essence Berry, what CropsNH's essence berry crop drops) from the chests and inventories
  touching it, as few as it needs, 250 mB each (`xpCrafterSolidXp`): the same as GTNH's fluid extractor makes from
  one, and what eating one gives on average (6 to 19 XP, at the 20 mB a point EnderIO and OpenBlocks count). Pipe a
  berry farm straight into the crafter (a GregTech tank takes items into its input slot; from 1.13.1 the crafter uses
  berries there, first), or into a chest next to it. From 1.14.0 a crafter's tank takes only liquid XP containers and
  essence berries from pipes, hoppers and other crafters, so nothing else can block its input slot (plain tanks still
  take anything, as GregTech has it). In its menu, click liquid XP holding berries to put one in
  (shift: the stack).
- **Bigger than the tank:** XP it has taken towards the next craft is held by the crafter itself, so a craft can cost
  more than the tank holds (72,000 mB for full gallium dust).
- **Full dust:** sneak-right-click it with a **block of emerald** (`xpCrafterFullItem`, kept in it; not in creative)
  and it makes the whole dust instead of a tiny or small pile, for 9 or 4 times the liquid XP: gallium dust from
  72,000 mB, arsenic dust from 63,000, brick dust from 16,000 (`xpCrafterTinyTimes`, `xpCrafterSmallTimes`; GregTech
  makes a dust from 9 tiny or 4 small piles). Recipes that already make a whole item don't change.
- **Cheaper (or dearer) crafts (1.14.0):** `xpCrafterCostPercent` scales every recipe's cost (25: a quarter, so a tiny
  pile of gallium dust takes 2,000 mB), and `xpCrafterCosts` sets single items, by ore dictionary name or item name,
  e.g. `dustTinyGallium=2000`. Full dust still takes 9 or 4 times the cost. The menu, the item's tooltip and WAILA show
  the cost the server uses. Changed in the config, they take effect at the next restart. Sneak-right-click
  with the emerald block again, or sneak and hit it with an empty hand, to switch it off and get the block back.
- **Status:** sneak-right-click it with an empty hand: what it makes, the XP each takes, whether it is working or
  waiting and why, its tank and the XP it holds, how many it has made and what its store holds.
- **Breaking it** (however: a player, an explosion, a Matter Manipulator) drops the XP crafter item with its recipe,
  emerald block, the XP it held and the liquid XP in its tank (2.0.0), and its store and slots as items; placed again,
  it carries on.
- **What players see:** the crafter block, NEI's recipe and listing (also under "@Valier"), the Valier creative tab,
  and WAILA's lines: what it makes, the cost, the status, its tank, the XP held, the count and its store (the server
  keeps each crafter's info ready, refreshed every second and whenever it changes, since WAILA asks on its network
  thread). WAILA option "Valier XP crafters" turns the lines off.

Stored in each world's `data/valiertweaks_xpcrafters.dat`, by position (the block holds the tank and slots).

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
| `noRailcraftBoilerExplosions` | `true` | Railcraft boilers vent steam instead of exploding when water reaches them hot and dry. Off: Railcraft's explosion. |
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
| `xpcrafter` / `xpCrafter` | `true` | XP crafters can be made and work. Off: none can be made, and existing ones stop (keeping what they know). |
| `xpCrafterTank` | `gregtech:gt.blockmachines:817` | The GregTech tank three of which (with two LV pumps, an LV conveyor, an LV robot arm, a crafting table and an LV circuit) craft one (GT++'s ULV fluid tank). Any GregTech tank works. Not an item: none can be made. (1.11.0 removed `xpCrafterItem`, the diamond block of the old recipe.) |
| `xpCrafterFluids` | `xpjuice` | Fluids that count as liquid XP. Recipes made only of containers of these are what it can make. |
| `xpCrafterSolidXp` | `TConstruct:oreBerries:5=250` | Items it takes as liquid XP (`mod:item:meta=mB`), from inventories touching it or its menu. By default Tinkers' Concentrated Essence Berry, 250 mB. |
| `xpCrafterFullItem` | `minecraft:emerald_block` | Switches full dust on (sneak-right-click, or its menu; kept in it, given back when switched off). |
| `xpCrafterTinyTimes` | `9` | Times the XP for a whole dust instead of a tiny pile. |
| `xpCrafterSmallTimes` | `4` | Times the XP for a whole dust instead of a small pile. |
| `xpCrafterCostPercent` | `100` | Every XP recipe's liquid XP, in percent of GTNH's XP bucket recipe (1 to 1000). |
| `xpCrafterCosts` | (empty) | Items whose craft costs a set amount instead, as `item=mB`: an ore dictionary name (`dustTinyGallium=2000`) or `mod:item[:meta]`. Entries that set nothing are named in the server log. |
| `macerator` / `chainArmourFullDust` | `true` | Chain helmets macerate into 1 steel dust, chain leggings into 2. |
| `pipes` / `pipeItems` | `8, 32, 64, 64` | Items one Extract or Request face moves per transfer at most, per tier (Basic, Advanced, Elite, Wireless). |
| `pipeFluids` | `1000, 4000, 16000, 16000` | The same for fluid (mB). |
| `pipeTicks` | `20, 10, 5, 5` | Ticks between a working face's transfers, per tier. |
| `pipeMaxSleepTicks` | `100` | Longest wait of a face that keeps finding nothing to do (20 to 1200). |
| `pipeBudgetMicros` | `2000` | Most time a tick spent on pipes, in microseconds; the rest waits a tick. |
| `pipeNetworkMax` | `4096` | A network of more pipes than this does nothing (a safety limit). |
| `pipeWirelessCrossDimension` | `true` | Wireless pipes link across dimensions. |
| `pipeHeldStacks` | `4` | Stacks a face may hold back before the oldest drops. |

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
- **Railcraft boiler self-test:** `valiertweaks selftest railboiler` (same flag), in an empty chunk of the test world.
  It builds Railcraft's smallest boiler (a solid firebox with a low pressure tank on it) as a player would, waits for
  it to form, puts dirt two blocks from it, then sets it off through Railcraft's own code: the boiler is made
  superheated with an empty water tank, water is poured into the firebox, and the firebox ticks.
  - **On (the setting):** Railcraft cooled it to 299 (its explosion code ran), the explosion is stopped, and the
    boiler, its water, the dirt and the formed boiler are all still there.
  - **Other explosions:** an explosion with no entity behind it a few blocks away still breaks its dirt.
  - **Off**, to show the test reaches Railcraft's real explosion: the same steps blow the dirt away.
  - The setting is put back and the area cleared.
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
    small one (brick dust), none for iron dust. Cost settings (1.14.0): with `dustTinyGallium=2000` and charcoal at 300
    set, and the rest at 50%, tiny gallium takes 2,000 (full gallium dust 18,000), charcoal 300, tiny arsenic 3,500 and
    iron dust 1,000; entries that set nothing are ignored, and with the settings put back the costs are GTNH's again.
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
  - **Menu (1.11.0), on a crafter of its own:** a right-click opens it with an empty hand or holding dirt (1.11.1),
    not holding a GregTech wrench or a cover (an LV pump). It
    shows the 27 things it can make, cheapest first, with costs, its empty tank and its status, and no pages.
    Clicking tiny gallium dust chooses it (it shines), again stops it, a shift-click chooses too. Icons can't be taken
    (click, number key, Q) or covered. A liquid XP bucket poured in fills the tank, the gauge shows it and the empty
    bucket stays on the cursor; dirt is refused. The 2 it makes from 16,000 mB wait in its store and show in the menu;
    with its store full it takes no XP and says why in red. Its store can be taken from (shift-click, click, number
    key) but not filled. Full dust from emerald blocks on the cursor (the gallium icon becomes gallium dust at 72,000
    mB) and back. Where protected it can be looked at but not changed or taken from. Further than 8 blocks it closes.
    Broken, its store drops with the crafter item.
  - **Essence berries (1.12.0):** a Concentrated Essence Berry counts as 250 mB, another ore berry doesn't. From a chest
    of 40 berries a crafter takes 32 for a tiny pile of gallium dust (which goes into the same chest), then the other 8
    towards the next, leaving the dirt in it. The tank's liquid XP goes first, then berries. With water in its tank it
    still crafts from berries, then says its tank holds something else. Clicking the menu's liquid XP holding berries
    puts one in, a shift-click the rest. The test also logs what GTNH's fluid extractor, centrifuge and macerator make
    of a berry (17 Nov: the fluid extractor 250 mB of liquid XP, the others nothing).
    From 1.13.1: the tank accepts berries from below as a pipe offers them, and 40 berries in the crafter's own tank
    are used, 21 to finish a tiny pile it held 2,750 mB towards, the other 19 towards the next.
  - **WAILA (1.10.0):** the data WAILA's server sends for the crafter (every provider registered for the tank, as
    WAILA asks them) says what it makes and the cost as soon as it is chosen; working, the 4,000 mB held and 3 made;
    gallium dust at 72,000 mB with full dust on; with its store full and nothing touching it, why, the 8,000 mB in its
    tank and the 576 stored items; nothing
    once it is forgotten, and nothing for a plain tank. With `valierclient` in the test server's `mods` too, Valier
    Client's name and line providers are run on that data, and the lines a player reads are checked (see its README).
  - **Chain armour:** the macerator recipes give 1, 2, 1 and 2 steel dust for helmet, leggings, boots and chestplate,
    also as a macerator looks them up.
- **2.0.0 self-tests** (same flag): `valiertweaks selftest mod` (one jar: the stub `valierclient` mod id, language
  file, server proxy), `valiertweaks selftest pipes` (in an empty chunk, after loading the chunks around it so nothing
  is generated over the rigs) and `valiertweaks selftest pipeperf`.
  - **Pipes:** joining chests and pipes but not stone, a wrench shutting a side, new faces being Provide, Pull to Push
    moving every item with none lost, an iron whitelist, a Request face keeping 10 iron stocked, a double chest counted
    as one inventory, liquid XP between two crafters, Wireless tags linking one owner's pipes and not another's, a loot
    spawner working with only a Valier pipe pulling from it, idle faces sleeping longer, keep amounts on Pull and Push
    faces, existing-item filters, Push/Pull faces, damage and mod filters, and `/valiertweaks pipes`.
  - **Speed:** 512 chest-pipe-pipe-chest rigs (1,024 working faces: moving, idle and Request) cost 0.195 ms a tick
    (limit 1 ms).
  - **19 Nov 2026, Valier Tweaks 2.0.0 (as 2.0.0-pre2):** all nine steps on the test server, 276 checks, none failed
    (mod 6, lootspawner 123, xpcrafter 70, pipes 18, pipeperf 2, railboiler 12, boilers 18, trees 12, peaceful 15).
    Converting a copy of the Valier world (3 loot spawners, 27 XP crafters) kept every setting, upgrade, paid block,
    crafter record and slot.
