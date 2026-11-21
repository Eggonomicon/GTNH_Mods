package com.valier.tweaks.grid;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * The colour each Grid network shows on its blocks, one of the 16 dye colours. A network gets one from its name until
 * someone picks another in a block's menu; only picked colours are saved (with the overworld, by team and network
 * name), so a network keeps its colour while none of its blocks are loaded. Server thread only.
 */
public final class GridColours extends WorldSavedData {

    public static final String NAME = "valiertweaks_grid";
    /** Dye colours by dye number (vanilla's), and their names. */
    static final int[] RGB = { 0x1E1B1B, 0xB3312C, 0x3B511A, 0x51301A, 0x253192, 0x7B2FBE, 0x287697, 0xABABAB, 0x434343, 0xD88198, 0x41CD34,
        0xDECF2A, 0x6689D3, 0xC354CD, 0xEB8844, 0xF0F0F0 };
    public static final String[] NAMES = { "Black", "Red", "Green", "Brown", "Blue", "Purple", "Cyan", "Light grey", "Grey", "Pink",
        "Lime", "Yellow", "Light blue", "Magenta", "Orange", "White" };

    private final Map<String, Integer> chosen = new HashMap<>();
    private static GridColours instance;
    private static World instanceWorld;

    public GridColours(String name) {
        super(name);
    }

    /** The overworld's colours (null without one). */
    static GridColours get() {
        World w = DimensionManager.getWorld(0);
        if (w == null) return null;
        if (instance != null && instanceWorld == w) return instance;
        GridColours d = (GridColours) w.mapStorage.loadData(GridColours.class, NAME);
        if (d == null) {
            d = new GridColours(NAME);
            w.mapStorage.setData(NAME, d);
        }
        instance = d;
        instanceWorld = w;
        return d;
    }

    /** The colour (dye number) of the network called name in scope; -1 for no network. */
    public static int of(String scope, String name) {
        if (name == null || name.isEmpty()) return -1;
        GridColours d = get();
        Integer c = d == null ? null : d.chosen.get(GridNets.key(scope, name));
        return c != null ? c : byName(name);
    }

    /** The colour a network has until one is picked: from its name (never black, which the casing would hide). */
    static int byName(String name) {
        return 1 + Math.floorMod(name.toLowerCase(Locale.ROOT)
            .hashCode(), 15);
    }

    /** Picks colour (a dye number; -1: back to the one from its name) for the network called name in scope. */
    static void set(String scope, String name, int colour) {
        GridColours d = get();
        if (d == null || name.isEmpty()) return;
        String k = GridNets.key(scope, name);
        if (colour < 0 || colour == byName(name)) d.chosen.remove(k);
        else d.chosen.put(k, colour & 15);
        d.markDirty();
    }

    /** A dye number as RGB (-1 stays -1). */
    public static int rgb(int colour) {
        return colour < 0 ? -1 : RGB[colour & 15];
    }

    public static String name(int colour) {
        return colour < 0 ? "none" : NAMES[colour & 15];
    }

    /** The colour picked for a network key, or null (for the self-test's saving check). */
    Integer picked(String scope, String name) {
        return chosen.get(GridNets.key(scope, name));
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        chosen.clear();
        NBTTagList list = nbt.getTagList("Colours", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            chosen.put(e.getString("K"), e.getByte("C") & 15);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Integer> e : chosen.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("K", e.getKey());
            t.setByte("C", (byte) (int) e.getValue());
            list.appendTag(t);
        }
        nbt.setTag("Colours", list);
    }
}
