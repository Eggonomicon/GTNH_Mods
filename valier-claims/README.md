# Valier Claims

Server-side. Clients need nothing.

**Hostile mobs don't spawn in claimed chunks**, by default. Each team can change that for its own claims in its team
settings, including blocking passive animals, bats and water mobs too. The mod also stops some hostile spawns that
ServerUtilities misses, such as zombie reinforcements and the zombies from Thaumcraft dark aura nodes.

## How it works

ServerUtilities can already block spawns in a team's claims. It has two team settings for it, in **My Team →
Settings → Server Utilities**:

| Setting | Meaning |
|---|---|
| Disable Mob Spawns Within Claimed Chunks | `true`: the types below don't spawn in the team's claims. `false`: everything spawns. |
| Creature Types To Block Within Claimed Chunks | A list of `mob` (hostile), `animal` (passive), `ambient` (bats), `water_creature` (squid). Empty, or all four: nothing spawns. |

Every team starts with spawning allowed, though. This mod gives each team **hostile mobs blocked** as its starting
point, once:

- **New teams** get it when they are created.
- **Existing teams** (made before the mod was installed) get it the first time the server starts with the mod. A team
  that had already turned spawn blocking on keeps its own settings.
- After that, the team's own choice stands. A team that turns blocking off or adds animals keeps that through restarts.
  A marker in the team's own data (`valierclaims` in its ServerUtilities team file) records that it had its defaults.

It also fixes a ServerUtilities 2.4.22 bug.
- **The bug:** ServerUtilities saves a team's creature list as `MOB`, `ANIMAL`, … but looks the names up in lower case
  when it loads. So every entry came back as `mob`, and a team that blocked passive animals or bats lost that at each
  restart.
- **The fix:** the mod keeps its own copy of the list in the team's data and puts it back after the team loads.

ServerUtilities does the blocking itself, so it covers natural spawning: the regular "mobs appear in the dark" kind.

- **Mob spawner blocks** aren't affected (Minecraft 1.7.10 spawners skip this check), so spawner farms in a claim still
  work.
- **Animals placed when a chunk is first generated** aren't affected either.
- **Mobs already in a claim** stay. Nothing is removed.
- **A dark-room mob farm inside a claim** stops getting hostile spawns. Its team can turn "Disable Mob Spawns" off
  again.

This needs `blockMobSpawningInClaims=DEFAULT` in `serverutilities/serverutilities.cfg` (its default). `TRUE` or `FALSE`
there overrides every team's settings, and hides them from the settings screen.

## Hostile mobs that still turn up (1.1.0)

Blocking only stops natural spawning, so a hostile mob can still appear in a claim. Each one is logged to
`logs/valierclaims-spawns.log`, with what put it there:

| Source | Meaning |
|---|---|
| a mob spawner block | a dungeon or other spawner nearby (it needs a player within 16 blocks) |
| zombie reinforcements | on Hard, a zombie that is hit can call another zombie 7–40 blocks away, in the dark |
| a spawn egg, a command, a portal | what it says |
| a zombie killed a villager, a slime split, lightning, a village zombie siege | vanilla events |
| came in from chunk X,Z | it spawned outside and walked (or teleported) in; logged once per mob |
| mod code: `some.mod.Class.method` | another mod spawned it; the log line after it shows the code that called |
| natural spawning, which ServerUtilities should have blocked | shouldn't happen; please report it |

The log only covers claims that block hostile mobs. The same mob from the same source in the same chunk is logged at
most once every 10 minutes, so a spawner farm doesn't fill the file. Mobs saved in a chunk aren't logged when it
loads. `/valierclaims spawns [count]` shows the latest entries since the server started.

## Stopping spawns that skip ServerUtilities (1.2.0)

`blockSpawnsFrom` in the config lists sources to stop as well, inside claims that block hostile mobs. A stopped mob
never enters the world, and the log shows it as "stopped from spawning in" the claim. Stopped by default:

| Entry | What it stops |
|---|---|
| `natural` | natural spawning that got past ServerUtilities anyway (a safety net) |
| `reinforcements` | zombies called in by a zombie that was hit (on Hard) |
| `siege` | village zombie sieges |
| `thaumcraft.common.tiles.TileNode` | Giant Brainy Zombies from Thaumcraft dark aura nodes |
| `crazypants.enderzoo.entity.EntityWitherWitch.spawnCat` | the cats an EnderZoo Wither Witch brings with it |

Not stopped by default, so these still work in a claim:
- mob spawner blocks and EnderIO powered spawners,
- mobs let out of soul vials,
- spawn eggs, commands and portals,
- conversions: a villager killed by a zombie, a slime splitting, lightning.

Any other mod's spawning shows in the log as `mod code: some.mod.Class.method`. Add that name, or its class or
package, to the list to stop it. The entry `mod` stops every other mod's spawning, mob farms and summoned bosses
included, so use it with care. Mobs that walk in from outside the claim can't be stopped this way: they are already in
the world.

## Config (`config/valierclaims.cfg`)

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Give teams their default spawn settings. |
| `applyToExistingTeams` | `true` | Also give them to teams that existed before the mod (once each, at server start). |
| `defaultBlocked` | `mob` | The creature types blocked by default: `mob`, `animal`, `ambient`, `water_creature`. Empty: nothing. |
| `logHostileMobs` | `true` | Log hostile mobs that turn up in claims that block them (see above). |
| `blockSpawnsFrom` | the five above | Sources to stop in claims that block hostile mobs: `natural`, `reinforcements`, `siege`, `spawner`, `spawn_egg`, `command`, `portal`, `villager_converted`, `slime_split`, `lightning`, `worldgen`, `mod`, or a class, method or package name. Empty: only log them. |

Changes need a server restart and only affect teams that haven't had their defaults yet. Use `reapply` (below) for
the others.

## Commands (ops)

- `/valierclaims status`: every team, how many chunks it has claimed, and what its claims block.
- `/valierclaims reapply <team|all>`: give a team (or every team) the default settings again, replacing its own
  choice.
- `/valierclaims spawns [count]`: the latest hostile mobs that turned up in blocking claims, and where they came from
  (default 10, up to 50).

## Build and test

- **Build:** `gradlew --offline assemble` (JDK 25). It compiles against the ServerUtilities and fastutil jars of a
  GTNH 2.9 server (see `dependencies.gradle`). Point it at your server folder with `-PgtnhInstance=<folder>`; by
  default it is `../../GTNH_Instance`.
- **Self-test:** on the test server, with `-Dvalierclaims.selftest=true`:
  1. Run `team create_server_team vctest`, `team create_server_team vclegacy` and `valierclaims selftest prepare`.
     Then stop the server.
  2. Start it again and run `valierclaims selftest check`.

  It uses Forge's own spawn check on real mobs (zombie, skeleton, creeper, spider, cow, pig, chicken, bat, squid) in
  a claimed chunk and an unclaimed one. It changes the team's settings the way `/team settings` does, and checks that
  a team's choice survives a restart. It also checks that a pre-mod team gets its defaults when the server starts, and
  that `reapply` works. 59 checks (36 + 23), all passing again with 1.2.0 on 4 Nov 2026.
- **Spawn-log self-test (1.1.0, extended in 1.2.0):** one boot, with `-Dvalierclaims.selftest=true`: run
  `team create_server_team vcspawn` and `valierclaims selftest spawnlog`.
  - It puts zombies into a claim with a command, a spawn egg and real zombie reinforcements (a zombie hit by a fake
    player at night on Hard), walks one in from outside, and checks that each is logged with the right source.
  - It checks that the reinforcements are stopped and the others aren't, and that a spawn from code named in
    `blockSpawnsFrom` is stopped.
  - It checks the default `blockSpawnsFrom` against the code that spawns Thaumcraft dark node zombies, Wither Witch
    cats, village siege zombies, EnderIO powered spawner mobs, soul vial mobs and spawner block mobs.
  - It also checks that a cow, a zombie outside the claim, a zombie loaded with its chunk and repeats within 10 minutes
    are not logged.
  - 34 checks, all passing on 4 Nov 2026 (93 in all with the two-boot test).
