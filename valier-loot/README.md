# Valier Loot

Per-player loot chests for GT: New Horizons 2.9 (Minecraft 1.7.10), **server side only**. Each worldgen chest gives
every player their own copy of its loot, shown in a normal chest window. Players install nothing. The idea comes from
Lootr; none of Lootr's code is used.

## How it behaves

- **Which chests are loot.** A vanilla chest or trapped chest with items in it counts as loot when either:
  - its chunk loads from disk and no player has ever been near that chunk. Minecraft records this per chunk as
    `InhabitedTime`, which is 0 if no player has ever had the chunk in view. A chest there can't be one a player
    placed. This covers the whole pregenerated world outside the places people have already been.
  - it appears while a brand-new chunk is generated (structures being built), when someone explores past the
    pregenerated area.
- **Loot is locked away at once.** As soon as a loot chest is found, its contents move into the mod's saved data and
  the chest block is left empty. Whatever reaches the block directly then finds nothing, and nothing put into it can
  change the loot. That covers hoppers, pipes, OpenComputers drones, dollies, wrenches and IC2 or Wither explosions.
- **Opening.** Everyone who opens a loot chest gets a full copy of its loot, titled "Loot Chest". Each copy is kept, so
  what you take stays taken for you only.
- **Copies are not storage.** Players can put items into their copy, but an admin `reset` or `remove` clears copies.
- **Random loot per player.** By default (`bonus` mode), each player's copy holds the chest's own loot plus their own
  random roll from a weighted loot table. The roll is made the first time they open that chest. See *Random loot*
  below.
- **Double chests** count as one loot chest from either half, including a half that was empty. A chest that ends up
  next to a loot chest later (only possible by moving one with a dolly) is never made part of it, and won't open until
  it is moved away.
- **Breaking one on purpose** (to build a base in a village, say), as in Lootr:
  - **Sneak while mining it.** Mining it without sneaking is refused with a message explaining how.
  - **Or remove it with a Matter Manipulator.**

  Either way, the breaker's own share drops where the chest was: what's left in their copy, or the full loot if they
  never opened it. Everyone else loses their copy of that chest. Creative mode breaks it with no drop.
- **Protection.**
  - Machines (fake players) can't open or break loot chests, and vanilla explosions skip them.
  - No one can place a chest next to one.
  - Sneak-clicking a loot chest with a block in hand places the block, as in vanilla. With anything else in hand it
    does nothing.
- **Player chests are never touched.** Chests players place are recorded and never become loot. So are chests in any
  chunk a player has been near.
- **Saved data.** Each chunk keeps its own loot chests' loot and players' copies in its own save data, under
  `Level.ValierLoot`. A chest's loot is therefore always saved together with its emptied block, and only chunks that
  are loaded are kept in memory. There is no separate data file: the world's normal backups cover it. Removing the mod
  leaves loot chests empty, though the data stays in the chunks until they are next saved without the mod.

## Random loot

Settings are in the `randomloot` section of `config/valierloot.cfg`.

- **`mode`:**
  - `bonus` (the default): the chest's own loot plus the player's roll.
  - `replace`: only the player's roll, so every player gets different loot, as in Lootr.
  - `off`: everyone gets the chest's own loot.
- **Tables.** Rolls come from Forge chest-loot tables. These include:
  - `chest1` to `chest4`: LootGames' reward tiers, from cheap (rails, minecarts) to valuable (GregTech parts,
    enchanting tables).
  - `dungeonChest`, `villageBlacksmith`, `strongholdLibrary` and the other tables in
    `config/TooMuchLoot/loot/*.xml`.
  - Custom pools defined in this config.
- **`pools`:** which table a chest uses. Each rule is `dimension;distance;table[;min-max]`. A chest uses the rule for
  its own dimension with the largest distance from spawn it has reached. A dimension with no rules of its own uses the
  `*` rules. `min-max` sets how many items to roll; otherwise the table's own count is used. Defaults:

  | Where | Table |
  |---|---|
  | Overworld, 0 to 1 500 blocks from spawn | `chest1` |
  | Overworld, 1 500 to 4 000 | `chest2` |
  | Overworld, 4 000 to 7 000 | `chest3` |
  | Overworld, beyond 7 000 | `chest4` |
  | Nether | `chest2` |
  | Every other dimension | `chest3` |
- **`customPools`:** weighted entries of your own, one per line: `table;modid:item[:damage];weight;min;max`. For
  example, `valier_rare;minecraft:diamond;5;1;3` adds 1 to 3 diamonds at weight 5 to a table called `valier_rare`.
  Point a rule at it with an item count, e.g. `0;5000;valier_rare;1-2`.
- **Applying changes.** `/lootchest reload` re-reads the config. New rolls use it; copies players already have are
  kept, and `/lootchest reset` re-rolls a chest for everyone.
- **Double chests.** Each half rolls separately, so a double chest gets two rolls.

## Coverage

In Valier's pregenerated overworld (counted 1 Nov 2026), vanilla chests are 59% of the containers holding loot and
80% of the loot. That includes villages, dungeons, strongholds, Roguelike Dungeons and Thaumcraft barrows. In other
dimensions nearly every structure uses them too: Twilight Forest, the Nether, the End, Galacticraft and GalaxySpace
dungeons. No GTNH structure generates loot in barrels.

**Not covered yet**, in order of value:
1. Chest minecarts in mineshafts: 35% of the overworld's loot containers.
2. Galacticraft and GalaxySpace boss treasure chests (filled when the boss dies).
3. Thaumcraft urns and crates: loot drops when broken, with no container.

## Commands (ops)

`/lootchest <info|reset|remove|add|stats|reload> [x y z [dim]]`. Without coordinates it uses the chest you are
looking at. Commands act on both halves of a double chest.

| Command | What it does |
|---|---|
| `info` | Whether the chest is loot, how many players have their own copy, and which random-loot table it uses. |
| `reset` | Throws away every player's copy, so everyone gets the full loot (and a new roll) again. |
| `remove` | Makes it an ordinary chest again, with the original loot back in it. |
| `add` | Turns an ordinary chest into a loot chest; what is in it now becomes the loot list. Good for event chests. |
| `stats` | Counts of loot chests in a dimension's loaded chunks. |
| `reload` | Re-reads `config/valierloot.cfg`, random-loot settings included. |
| `import` | Server console only: restores chests from before the mod from a backup (see above). |

## Restoring chests from before the mod (`/lootchest import`, server console only)

Chests in chunks players had visited before the mod was installed were left as ordinary chests. To give them their
original loot back as loot chests:

`/lootchest import <candidates file> <backup world folder> [apply]`

- **Candidates file:** one `dim x y z` line per chest. A scan of the world against old backups produces it (Valier's
  is in `C:\Minecraft\GTNH_Server\loot-import\`).
- **Backup world folder:** region files from a backup taken before players looted much (`region\`, `DIMnn\region\`),
  read without ever being written.
- **Checks on each chest:** it is restored only if all of these hold. Anything else is skipped and listed in the report.
  - It is still there and isn't already loot or a player's chest.
  - The backup has loot in it.
  - What's in it now is part of that loot, i.e. looted or untouched, not used as storage.
  - The other half of its double chest, if it has one, is restored too or is empty.
- **The original loot comes back in full.** Players who looted the chest before get a copy again like everyone else.
- **Dry run first:** without `apply`, nothing changes. Either way a report (`import-dryrun.txt` or
  `import-applied.txt`) is written next to the candidates file.

## Config (`config/valierloot.cfg`)

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | true | Master switch. |
| `maxInhabitedTicks` | 0 | How many ticks players may have spent near a chunk for its chests to still count as loot. Keep 0. Chunks saved while a player is near are always marked as visited. |
| `protectFromBreaking` | true | Only creative can break loot chests outright; explosions skip them. |
| `breakWhileSneaking` | true | Players can still break one by sneaking while mining it; their share drops. |
| `matterManipulatorBreaks` | true | A player's Matter Manipulator can remove loot chests; their share drops. |
| `blockAdjacentChests` | true | No chests next to loot chests (they would form a vanilla double chest). |
| `registerNewChunks` | true | Also catch chests that structures generate in brand-new chunks. |
| `title` | Loot Chest | Window title. |
| `firstOpenMessage` | (one line) | Chat message a player sees the first time they open a loot chest. Empty turns it off. |

## Build and test

- **Build:** `gradlew --offline assemble` (JDK 25) builds `build/libs/valierloot-*.jar`.
- **Self-test:** runs across three boots of the test server (`..\..\test-server`) with `-Dvalierloot.selftest=true`:
  - boot 1: `lootchest selftest prepare`, `lootchest selftest setup`
  - boot 2: `lootchest selftest gen`, `lootchest selftest run`, `lootchest selftest run2`
  - boot 3: `lootchest selftest verify`

  Each check logs a `[ValierLoot SelfTest] PASS/FAIL` line and each step a SUMMARY line. The self-test commands do
  nothing without that flag.
- **Not covered by the self-test** (it uses fake players): the chest window as a real client sees it, closing open
  windows on `reset`/`remove`, and marking watched chunks as visited when they are saved.
- **Reviews:** two independent code reviews looked for dupes, loot theft and lag. All their findings are fixed: locking
  loot at once, double-chest handling, sneak-click tools, chunk-based storage, and no chunk loading while locking.

## Install on Valier

1. Copy the jar into `GTNH_Instance\mods`.
2. Restart **while nobody is online**. Minecraft only records a player's time near a chunk when they leave it or
   every ~7 minutes, and the old server (without this mod) can't fix that up when it saves on shutdown. A player who
   reached new ground just before the restart could otherwise have a freshly placed chest taken for loot.

Clients need nothing.
