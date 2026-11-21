# Where Valier Tweaks' art comes from

Every texture in `src/main/resources/assets/valiertweaks/textures/` is drawn by `tools/gen_textures.py`, from code: no
image is used as input, and nothing is copied or traced from vanilla Minecraft, GregTech, EnderIO, Create or any other
mod. Run `python tools/gen_textures.py --preview` to draw them again; `docs/textures-preview.png` shows them side by
side.

| Texture | What it is |
|---|---|
| `blocks/loot_spawner.png` | A dark steel cage frame with a gold inner frame, gold bar tips and corner rivets; open between the bars |
| `blocks/xp_crafter_side.png` | Machine casing with a round gold-rimmed window onto glowing green liquid XP |
| `blocks/xp_crafter_top.png` | Casing with a funnel grate, green glowing through its slots |
| `blocks/xp_crafter_bottom.png` | Plain bevelled casing with corner rivets |
| `blocks/item_pipe_0..6.png` | Banded pipe metal in each tier's colour (bronze, steel, blue aluminium, ender violet, emerald, crimson; creative in diagonal pink, cyan and gold bands) with a dark middle seam |
| `blocks/fluid_pipe_0..6.png` | The same metals with a glass stripe showing water |
| `items/pipe_configurator.png` | A wooden clipboard with a steel clip; on its paper, a short blue pipe and two lines of notes |
| `blocks/pipe_plate.png` | A light grey bevelled plate with a dark centre, tinted at run time by the face's mode |
| `blocks/barrel_side_0..6.png`, `barrel_front_0..6.png`, `barrel_top_0..6.png` (2.3.0) | Wooden staves held by two metal bands and a rim in each tier's GregTech hull metal (iron, steel, aluminium, stainless steel, titanium, tungstensteel, chrome); the front with a recessed label panel; the lid with planks across and a small metal plate |
| `items/barrel_upgrade_1..6.png` (2.3.0) | A band of the tier's metal around a green up arrow |
| `items/satchel_0..6.png` (2.3.0) | A leather satchel with a strap loop, a flap edged in the tier's metal and a clasp (Creative: rainbow bands) |
| `items/flask_0..6.png` (2.3.0) | A round glass flask of blue fluid with a neck band and cap in the tier's metal |
| `items/satchel_upgrade_1..5.png`, `flask_upgrade_1..5.png` (2.3.0) | The barrel upgrade's band around a small satchel or a blue drop |
| `blocks/satchel_dock_*.png` (2.3.0) | A steel casing with corner rivets and a green status strip; the top a recessed tray, showing the satchel or flask when one is docked |
| `blocks/grid_plug_0..7.png`, `grid_point_0..7.png`, `grid_cell_0..7.png`, `grid_controller.png` (2.3.0) | A dark casing with a bevelled rim and rivets; in the tier's GregTech voltage colour a socket with two prongs (plug), a dot with four rays (point), charge bars (cell), a ring around a dot (controller) |
| `blocks/grid_light.png` (2.3.0) | A one-pixel ring just inside a Grid block's rim, white and brighter on the top and left; the game tints it in the network's colour |
| `gui/pipe_icons.png` | The pipe menu's icons, from `tools/gui_icons.py` (also code only): mode arrows, sheets, crates, a book, an eye, a camera, a tag, a black hole (void) and three bins (the spread settings); `docs/pipe-icons-preview.png` |

Rule: a new texture goes into the script. A hand-drawn one must be logged here, with who drew it.
