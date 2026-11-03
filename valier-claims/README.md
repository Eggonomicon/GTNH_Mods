# Valier Claims

Server-side. Clients need nothing.

**Hostile mobs don't spawn in claimed chunks**, by default. Each team can change that for its own claims in its team
settings, including blocking passive animals, bats and water mobs too.

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

## Config (`config/valierclaims.cfg`)

| Setting | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Give teams their default spawn settings. |
| `applyToExistingTeams` | `true` | Also give them to teams that existed before the mod (once each, at server start). |
| `defaultBlocked` | `mob` | The creature types blocked by default: `mob`, `animal`, `ambient`, `water_creature`. Empty: nothing. |

Changes need a server restart and only affect teams that haven't had their defaults yet. Use `reapply` (below) for
the others.

## Commands (ops)

- `/valierclaims status`: every team, how many chunks it has claimed, and what its claims block.
- `/valierclaims reapply <team|all>`: give a team (or every team) the default settings again, replacing its own
  choice.

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
  that `reapply` works. 59 checks (36 + 23), all passing on 3 Nov 2026.
