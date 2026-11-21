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
| `gui/pipe_icons.png` | The pipe menu's icons, from `tools/gui_icons.py` (also code only): mode arrows, sheets, crates, a book, an eye, a camera, a tag, a black hole (void) and three bins (the spread settings); `docs/pipe-icons-preview.png` |

Rule: a new texture goes into the script. A hand-drawn one must be logged here, with who drew it.
