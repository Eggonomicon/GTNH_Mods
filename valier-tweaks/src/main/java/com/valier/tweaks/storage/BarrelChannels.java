package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

import com.valier.tweaks.ValierTweaks;

/**
 * Linked barrels: a channel is a name in a team (or one player's own, without a team; the same rule as Wireless pipe
 * tags), and every barrel on it uses the channel's one {@link BarrelStore}, wherever it is, in any dimension, loaded or
 * not. Its capacity is the sum of its barrels'. Saved with the overworld ({@value #NAME}.dat).
 * <p>
 * A barrel joining brings what it holds (only into an empty channel or one holding the same item). A barrel leaving
 * (unlinked, broken, picked up) takes away what the rest can't hold, as its own contents: nothing is ever lost.
 */
public final class BarrelChannels extends WorldSavedData {

    public static final String NAME = "valiertweaks_barrels";
    public static final int MAX_NAME = 32;

    private static BarrelChannels instance;
    private static World instanceWorld;

    final Map<String, Channel> channels = new HashMap<>();

    public BarrelChannels(String name) {
        super(name);
    }

    /** One channel: its store and its barrels (place key to tier). */
    public static final class Channel {

        public final String scope, name;
        public final BarrelStore store = new BarrelStore();
        final Map<String, Integer> members = new HashMap<>();
        boolean removed;

        Channel(String scope, String name) {
            this.scope = scope;
            this.name = name;
        }

        public int barrels() {
            return members.size();
        }

        void recount() {
            long s = 0;
            for (int tier : members.values()) s += BarrelTier.stacks(tier);
            store.stacks = Math.max(1, s);
        }
    }

    /** The overworld's channels (server only), loaded once. */
    public static BarrelChannels get() {
        World w = DimensionManager.getWorld(0);
        if (w == null) return null;
        if (instance != null && instanceWorld == w) return instance;
        BarrelChannels d = (BarrelChannels) w.mapStorage.loadData(BarrelChannels.class, NAME);
        if (d == null) {
            d = new BarrelChannels(NAME);
            w.mapStorage.setData(NAME, d);
        }
        instance = d;
        instanceWorld = w;
        return d;
    }

    /** How many channels there are (/valier cost; 0 before the overworld loads). */
    public static int count() {
        BarrelChannels d = get();
        return d == null ? 0 : d.channels.size();
    }

    /** Forgets the loaded channels (the overworld unloaded: the server is stopping). */
    static void forget() {
        instance = null;
        instanceWorld = null;
    }

    public static String clean(String name) {
        if (name == null) return "";
        StringBuilder b = new StringBuilder();
        for (char ch : name.toCharArray()) if (ch >= ' ' && ch != '§' && ch != 127) b.append(ch);
        String n = b.toString()
            .trim();
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }

    static String key(String scope, String name) {
        return scope + "|" + name.toLowerCase(java.util.Locale.ROOT);
    }

    static String place(TileBarrel t) {
        return t.getWorldObj().provider.dimensionId + ":" + t.xCoord + ":" + t.yCoord + ":" + t.zCoord;
    }

    public Channel find(String scope, String name) {
        return channels.get(key(scope, name));
    }

    /** A scope's channels, by name. */
    public List<Channel> of(String scope) {
        List<Channel> out = new ArrayList<>();
        for (Channel c : channels.values()) if (c.scope.equals(scope)) out.add(c);
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    /**
     * The channel a loaded barrel names (it is put back on it if its place is missing, say after a crash between
     * saves), or null when the channel is gone: then the barrel is unlinked.
     */
    Channel resolve(TileBarrel t) {
        Channel c = channels.get(key(t.scope, t.channel));
        if (c == null) return null;
        String p = place(t);
        Integer tier = c.members.get(p);
        if (tier == null || tier != t.tier) {
            c.members.put(p, t.tier);
            c.recount();
            markDirty();
        }
        return c;
    }

    /** Links t to the channel called name in scope. Returns null when done, else why not. */
    String join(TileBarrel t, String scope, String name) {
        if (t.tier == BarrelTier.CREATIVE) return "A Creative barrel can't be linked.";
        name = clean(name);
        if (name.isEmpty()) return "Type a channel name first.";
        Channel c = channels.get(key(scope, name));
        BarrelStore own = t.own;
        if (!t.channel.isEmpty()) {
            if (c != null && t.linked() == c) return null; // already on it
            leave(t); // it may keep some of the old channel's items now
            c = channels.get(key(scope, name));
        }
        if (c != null && !own.isEmpty() && c.store.item != null && !BarrelStore.same(c.store.item, own.item))
            return "\"" + c.name + "\" holds " + c.store.item.getDisplayName() + "; this barrel holds " + own.item.getDisplayName()
                + ". Empty it first.";
        if (c == null) {
            c = new Channel(scope, name);
            c.store.onChange = this::markDirty;
            c.store.item = own.item == null ? null : own.item.copy();
            c.store.count = own.count;
            c.store.copySettings(own);
            channels.put(key(scope, name), c);
        } else if (!own.isEmpty()) {
            if (c.store.item == null) {
                c.store.item = own.item.copy();
                c.store.item.stackSize = 1;
            }
            c.store.count += own.count;
        }
        c.members.put(place(t), t.tier);
        c.recount();
        own.item = null;
        own.count = 0;
        t.scope = scope;
        t.channel = c.name;
        t.linkTo(c);
        c.store.changed();
        markDirty();
        ValierTweaks.LOG.info("Barrel at {} joined channel {} ({} barrels)", place(t), c.name, c.members.size());
        return null;
    }

    /** Unlinks t: it keeps what the channel's other barrels can't hold (all of it when it was the last). */
    void leave(TileBarrel t) {
        Channel c = channels.get(key(t.scope, t.channel));
        BarrelStore own = t.own;
        if (c != null) {
            c.members.remove(place(t));
            c.recount();
            own.copySettings(c.store);
            if (c.members.isEmpty()) {
                own.item = c.store.item == null ? null : c.store.item.copy();
                own.count = c.store.count;
                channels.remove(key(c.scope, c.name));
                c.removed = true;
                c.store.count = 0;
                c.store.item = null;
            } else {
                long cap = c.store.capacity();
                if (c.store.count > cap && c.store.item != null) {
                    own.item = c.store.item.copy();
                    own.count = c.store.count - cap;
                    c.store.count = cap;
                } else if (own.locked && own.item == null && c.store.item != null) {
                    own.item = c.store.item.copy(); // locked, it keeps the channel's item at 0
                    own.count = 0;
                }
            }
            if (own.item == null) own.locked = false; // a lock without an item would refuse everything
            c.store.changed();
            markDirty();
            ValierTweaks.LOG.info("Barrel at {} left channel {}", place(t), c.name);
        }
        t.channel = "";
        t.linkTo(null);
        own.changed();
    }

    /** A linked barrel's tier changed (an upgrade): the channel holds more. */
    void retier(TileBarrel t) {
        Channel c = channels.get(key(t.scope, t.channel));
        if (c == null) return;
        c.members.put(place(t), t.tier);
        c.recount();
        c.store.changed();
        markDirty();
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        channels.clear();
        NBTTagList list = nbt.getTagList("Channels", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            Channel c = new Channel(t.getString("Scope"), t.getString("Name"));
            c.store.read(t.getCompoundTag("Store"));
            c.store.onChange = this::markDirty;
            NBTTagList m = t.getTagList("Barrels", 10);
            for (int j = 0; j < m.tagCount(); j++) {
                NBTTagCompound b = m.getCompoundTagAt(j);
                c.members.put(b.getString("At"), b.getInteger("Tier"));
            }
            c.recount();
            if (!c.members.isEmpty()) channels.put(key(c.scope, c.name), c);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Channel c : channels.values()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("Scope", c.scope);
            t.setString("Name", c.name);
            t.setTag("Store", c.store.write(new NBTTagCompound()));
            NBTTagList m = new NBTTagList();
            for (Map.Entry<String, Integer> e : c.members.entrySet()) {
                NBTTagCompound b = new NBTTagCompound();
                b.setString("At", e.getKey());
                b.setInteger("Tier", e.getValue());
                m.appendTag(b);
            }
            t.setTag("Barrels", m);
            list.appendTag(t);
        }
        nbt.setTag("Channels", list);
    }
}
