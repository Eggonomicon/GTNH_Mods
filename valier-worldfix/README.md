# Valier World Fix

Server-side admin tool. Clients need nothing.

## Why

ServerUtilities' `/pregen` generated Valier's terrain but left about a quarter of it **unpopulated**: 151,820 chunks
with no trees, ores or structures. They were counted on 2 Nov 2026 with `tools/scan_unpopulated.py`
(`python -I tools/scan_unpopulated.py <world folder>`, read-only).
The server populates such a chunk the moment a player comes near, all at once on the main thread. Exploring therefore
froze the server; once it was for 56 seconds.

## `/worldfix pregen <radius> [blocks] [budgetMs] [minfree=GB]` (version 1.1.0)

Pregenerates **and** populates the overworld out to a radius around world spawn:

- **Units:** the radius is in chunks, or in blocks with `blocks`. For example `/worldfix pregen 625` is the same as
  `/worldfix pregen 10000 blocks`.
- **Order:** it walks square rings outward from spawn, keeping to the circle. Each chunk is generated if missing and
  populated if it isn't yet. At the edge, the +x/+z neighbours are generated too, one chunk past the radius.
- **When it runs:** only while nobody is online, within `budgetMs` per tick (default 40). It pauses the moment someone
  logs in.
- **Disk guard:** it pauses while the server's drive has less than `minfree` GB free (default 150).
- **Memory:** chunks it loads are let go again, so memory stays flat.
- **Restarts:** it remembers its place in `valierworldfix-task.properties` in the server folder and carries on by
  itself after a restart.
- **Raising the radius:** running `pregen` again with a larger radius carries on outward from where it got to.

`/worldfix status` shows progress, including free disk space. `/worldfix stop` stops it. The progress is also logged
every minute (`[ValierWorldFix]` in the server log).

**Size and time:** the world was about 6.7 KB per chunk on disk on 2 Nov (4.3 GB for 637,457 chunks). A circle of
radius r chunks holds about 3.14 × r² chunks:

| Radius (chunks) | Blocks | Chunks | Disk |
|---|---|---|---|
| 450 | 7,200 | 0.64 M | 4.3 GB (the 31 Oct pregen) |
| 625 | 10,000 | 1.2 M | ~8 GB |
| 1,250 | 20,000 | 4.9 M | ~33 GB |
| 2,500 | 40,000 | 19.6 M | ~130 GB |
| 10,000 | 160,000 | 314 M | ~2,100 GB (more than the drive; the disk guard stops it first) |

## Version 1.0.0: `/worldfix populate <chunk list>`

Populates only the chunks in a list file (`cx cz` per line), with the same empty-server-only rule. Valier ran it from
2 Nov on `worldfix\unpopulated.txt`. Version 1.1.0 replaced it with `pregen`.

## Build and test

- **Build:** `gradlew --offline assemble` (JDK 25).
- **Self-test:** on the test server with `-Dvalierworldfix.selftest=true`, run `worldfix selftest prepare`, then
  `worldfix pregen 40`, then (after it finishes) `worldfix selftest check 40`.

  The prepare step marks 25 chunks around spawn as never populated. The check confirms every chunk within the radius
  exists and is populated. It passed on 2 Nov: 5,025 of 5,025 chunks, 44 of them generated from nothing.
