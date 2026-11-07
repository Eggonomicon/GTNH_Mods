# MM Free Power (with the Valier MM Mk IV)

Server-side tweaks to GTNH's Matter Manipulator. Clients need nothing.

- **Free power.** Every Matter Manipulator's EU draw succeeds without draining its charge, so it never needs
  charging (`mixin/MixinItemMatterManipulator`).
- **Valier MM Mk IV.** Put a Matter Manipulator MK III on its own in a crafting table. Out comes the same
  manipulator, with its charge, settings and AE/uplink links kept, renamed "Valier MM Mk IV" with a glint. It places
  blocks for free: it doesn't take them from your inventory, ME system or uplink.
  - **Free:** blocks with no tile entity. Examples:
    - Vanilla: stone, cobble, glass, wool, planks, logs, bricks, sandstone, clay, obsidian, torches.
    - Ztones: all of it, Myst included.
    - Et Futurum: concrete, concrete powder and glazed terracotta, plus cut, chiseled and grate copper, and its
      stone and wood.
    - Chisel: the stone, wood and glass variants.
    - Botania livingrock/livingwood/dreamwood/prismarine, Modern Markings, and BoP stone and wood.
  - **Still cost items:**
    - Anything with a tile entity: machines, chests, furnaces, hoppers.
    - Everything from GregTech and its add-ons (casings, coils, glass, ores), dreamcraft, Avaritia, Draconic
      Evolution, ExtraUtilities, IC2 and AE2.
    - Storage and ore blocks (ore dictionary `block…`/`ore…`, e.g. `blockDiamond`, `oreIron`).
    - A few blocks that break or recycle into valuable materials: glowstone, anvils, rails, TNT, quartz, Chisel's
      metal and gem variants, and Et Futurum netherite, amethyst and copper (the copper block itself and ore).
  - A Mk IV still takes costly blocks from your inventory as usual. A mixed build takes only the costly ones.
  - It is a marked MK III, not a new item, so players need no client mod. The name, lore and glint are plain item
    data.
- **Void list (1.2.0).** Blocks a Mk IV removes normally come back as drops (into your inventory, ME system or
  uplink, else onto the ground). Items on your void list are deleted instead. Each player has their own list, kept
  through deaths and restarts. It only applies while you hold a Mk IV; an ordinary manipulator never voids.

  | Command | What it does |
  |---|---|
  | `/mmvoid` | Shows your list. |
  | `/mmvoid cobble` | Turns voiding of cobblestone on or off (`ore:cobblestone`: every kind the ore dictionary calls cobblestone). |
  | `/mmvoid toggle` | Starts voiding the item you're holding, or stops if it's already voided (1.2.1). |
  | `/mmvoid add` | Voids the item you're holding (that exact kind, e.g. `minecraft:wool:14`). |
  | `/mmvoid add <item>` | Voids `modid:name` (any kind), `modid:name:meta`, or `ore:<name>`. |
  | `/mmvoid remove` | Stops voiding the item you're holding: takes off every entry that matches it, `ore:cobblestone` included (1.2.1). |
  | `/mmvoid remove <item\|number>` | Takes an entry off the list (numbers as `/mmvoid` shows them). |
  | `/mmvoid clear` | Empties the list. |
  | `/mmvoid off` / `on` | Pauses or resumes voiding without losing the list. |
  | `/mmvoid purge [item...]` | Ops and console: deletes dropped items lying on the ground that match (your own list if you name none), the manipulator's large drops included. Loaded chunks only, every dimension. |

  Voided items are gone for good. In move mode, a voided block that costs items can't be placed again at the
  destination; free blocks are placed anyway.
- **Ores drop (1.3.0).** On its own a Matter Manipulator deletes every ore it removes, with no drops. That covers
  every naturally generated GregTech, GT++ and BartWorks ore (big and small) and any block with an ore dictionary
  name starting with "ore" (vanilla and other mods' ores). With this mod, ores drop like any other block:
  - Big GregTech ores give raw ore (GregTech's `oredropbehavior` setting decides).
  - Small ores give their dusts, crushed ores and gems.
  - Diamond, coal and redstone ores give diamonds, coal and redstone.
  
  This applies to every manipulator tier, the Mk IV included (its void list still applies). The manipulator's only
  other "never drop" blocks are invisible helpers with no item (GregTech render blocks, light-source air).
- **Fortune (1.3.0).** Manipulators mine with the Fortune level enchanted on them.
  - **Adding Fortune:** put any Matter Manipulator in a crafting table together with an enchanted book that has
    Fortune. You get the same manipulator (charge, settings, links and Mk IV marker kept) with that Fortune level, and
    the book is used up. Only an upgrade works.
  - **Anvils:** they work for creative players only. A survival anvil won't accept it without a client mod.
  - **Tooltip:** clients show "Fortune III" as for any enchanted item, and it survives the manipulator saving its
    settings.
  - **Everyone at once:** `mining/baseFortune` gives every manipulator a Fortune level without enchanting.

## Config (`config/mmfreepower.cfg`, section `mk4`)

| Setting | Meaning |
|---|---|
| `enabled` | Free placement and the Mk IV recipe on or off. |
| `excludedMods` | Mods whose blocks always cost items. |
| `excludedBlocks` | `modid:name` patterns (`*` wildcards) that always cost items. |
| `excludedOrePrefixes` | Ore dictionary words (`block`, `ore`, …) that mark storage and ore blocks as costly. |
| `allowedOreNames` | Ore dictionary names (`*` wildcards) that are building blocks anyway (`blockGlass*`, `blockWool*`, …). |
| `alwaysFree` | `modid:name` patterns that are always free (tile entities still cost). |

Section `mining`:

| Setting | Meaning |
|---|---|
| `dropOres` | Ores a manipulator removes drop as usual (default `true`); `false` keeps the manipulator's own deleting. |
| `fortuneBooks` | The manipulator plus Fortune book crafting recipe (default `true`). |
| `baseFortune` | Fortune level every manipulator mines with at least, enchanted or not (default `0`, up to 10). |

Changes need a server restart.

## How it works

- `MkIV`: the marker (`ValierMk4` tag), the free-block rule and the consumption hook.
- `mixin/MixinMMInventory`: every item a manipulator places goes through `MMInventory.tryConsumeItems`. For a Mk IV,
  free items count as taken (as the manipulator does for creative players) and only the rest is taken for real.
  Every item it hands back goes through `MMInventory.givePlayerItems`, where `MkIVVoid` takes out what the holder's
  void list names.
- `MkIVVoid` and `VoidCommand`: the void lists (in each player's `PlayerPersisted` data, `ValierMMVoid`), matching,
  and `/mmvoid`.
- `mixin/MixinItemMatterManipulator`: `setState` rebuilds the item's NBT from the manipulator's own state; the Mk IV
  marker, name and glint are put back afterwards.
- `MkIVRecipe`: the shapeless MK III to Mk IV recipe, registered on the server only.
- `mixin/MixinAbstractBuildable` (in `AbstractBuildable.removeBlock`):
  - **Ore check:** the manipulator asks `GTUtility.isOre` and checks ore dictionary names for "ore" to decide whether
    to delete a block. Both answers are wrapped to say "not an ore".
  - **Drops:** the `Block.getDrops` call is wrapped too. It uses the block's real world metadata instead of the
    manipulator's spec, where a natural GregTech ore's 8032 becomes 32, a "placed" ore that gets no Fortune. It sets
    the player as Forge's `Block.harvesters`, as `harvestBlock` does, since GregTech reads that. It passes the held
    manipulator's Fortune level.
  - **Harvest event:** it gets the same Fortune level.
- `MMFortune` and `FortuneRecipe`: the Fortune level on a manipulator or book, and the manipulator plus book recipe.
  `MkIVCraftSync` sends any manipulator in a crafting output slot to the client, since clients don't know these
  server-side recipes.

## Build and test

- **Build:** `gradlew --offline assemble` (JDK 25) builds `build/libs/mmfreepower-*.jar`.
  - It compiles against the Matter Manipulator, UniMixins, fastutil, IC2 and AE2 jars of a GTNH 2.9 server (see
    `dependencies.gradle`).
  - Point it at your server folder with `-PgtnhInstance=<folder>`; by default it is `../../GTNH_Instance`.
- **Self-test:** on the test server, start it with `-Dmmfreepower.selftest=true` and run `mmfreepower selftest` in the
  console. It covers the recipe, the marker surviving state saves, which real blocks are free, and taking items
  through the manipulator's own inventory code. For the void list it hands drops back through that code with a fake
  player's list set (cobblestone, dirt by name, one wool colour, paused, an ordinary MK III), runs `/mmvoid toggle`
  and `/mmvoid remove` with items in the fake player's hand, and purges real drops near spawn.
  - **Ores and Fortune (1.3.0):** it places vanilla, natural GregTech and small GregTech ores and removes them through
    the manipulator's own `removeBlock`, then crafts Fortune onto an MK III and a Mk IV. It checks that Fortune
    survives state saves, and mines 20 diamond ores and 20 GregTech iron ores at Fortune III. That part uses a plain
    EntityPlayerMP, because GregTech gives fake players no Fortune.
  - **Result:** 93 checks, all passing on 7 Nov 2026.
- **Verdict dump:** `mmfreepower dump` (same flag) writes every block variant's verdict and reason to
  `mmfreepower-verdicts.tsv` in the server folder. That's how to check a mod's blocks before changing the config.
  On 2 Nov: 6 945 free, 8 003 costing (most of those are machines and GregTech).
- **Not covered:** a real player building with it in-game.
