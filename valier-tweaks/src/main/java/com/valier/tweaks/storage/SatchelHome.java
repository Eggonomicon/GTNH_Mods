package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.valier.tweaks.pipe.TeamScope;

/**
 * Send home (2.3.3): a Satchel slot linked to one of the player's team's barrel channels ({@link BarrelChannels}).
 * Picked-up items of that slot fill it up to its keep amount; the rest go straight into the channel's store, wherever
 * its barrels are (loaded or not). What the channel can't take stays in the Satchel (void overflow as usual). Only the
 * team that linked it can send: a Satchel given to someone outside the team sends nothing.
 * <p>
 * Cost: a pickup of a linked slot's item is two map lookups more; nothing else ticks.
 */
public final class SatchelHome {

    private SatchelHome() {}

    static String scopeOf(EntityPlayer p) {
        return String.valueOf(TeamScope.of(p.getUniqueID()));
    }

    /** The store slot i sends to (null: none, the channel is gone, or p isn't in the team that linked it). */
    static BarrelStore store(EntityPlayer p, SatchelInventory inv, int i) {
        if (p == null || inv.fluid || i < 0 || i >= inv.size || inv.home[i] == null) return null;
        BarrelChannels d = BarrelChannels.get();
        if (d == null) return null;
        String scope = scopeOf(p);
        if (!scope.equals(inv.homeScope[i])) return null;
        BarrelChannels.Channel c = d.find(scope, inv.home[i]);
        return c == null ? null : c.store;
    }

    /** Slot i's items over its keep amount go home; returns how many went. */
    public static int flush(EntityPlayer p, SatchelInventory inv, int i) {
        BarrelStore st = store(p, inv, i);
        if (st == null || inv.items[i] == null) return 0;
        int n = inv.available(i, true);
        if (n <= 0 || !st.takes(inv.items[i])) return 0;
        long took = st.insert(inv.items[i], n, false);
        if (took > 0) inv.extract(i, (int) took, true, false);
        return (int) took;
    }

    /**
     * A stack being picked up: if a slot of inv holding it sends home, it goes into that slot (up to what fits, never
     * destroyed) and the slot then sends what is over its keep amount home, twice if needed (a full slot empties into
     * the channel, then takes the rest). Returns how many of s it took; the usual pickup has the rest.
     */
    static int pickup(EntityPlayer p, SatchelInventory inv, ItemStack s) {
        if (!inv.homes) return 0;
        int i = inv.find(s);
        if (i < 0 || inv.home[i] == null || store(p, inv, i) == null) return 0;
        int took = 0;
        for (int pass = 0; pass < 2 && took < s.stackSize; pass++) {
            took += inv.take(s, s.stackSize - took, false, false);
            flush(p, inv, i);
        }
        return took;
    }

    /** The team's channels that take what slot i holds (any, if it's empty), by name. */
    static List<BarrelChannels.Channel> choices(EntityPlayer p, SatchelInventory inv, int i) {
        List<BarrelChannels.Channel> out = new ArrayList<>();
        BarrelChannels d = BarrelChannels.get();
        if (d == null) return out;
        for (BarrelChannels.Channel c : d.of(scopeOf(p))) if (inv.items[i] == null || c.store.takes(inv.items[i])) out.add(c);
        return out;
    }

    /** The menu's Send home button: the next channel that takes the slot's item (after the last: off). */
    static void next(EntityPlayer p, SatchelInventory inv, int i) {
        List<BarrelChannels.Channel> list = choices(p, inv, i);
        if (list.isEmpty()) {
            ItemSatchel.tell(p, "Your team has no barrel channel that takes " + (inv.items[i] == null ? "items"
                : inv.items[i].getDisplayName()) + ": link barrels in a barrel's menu first.");
            set(inv, i, null, null);
            return;
        }
        int at = -1;
        String scope = scopeOf(p);
        if (inv.home[i] != null && scope.equals(inv.homeScope[i])) {
            for (int k = 0; k < list.size(); k++) if (list.get(k).name.equalsIgnoreCase(inv.home[i])) at = k;
        }
        if (at + 1 >= list.size()) set(inv, i, null, null);
        else set(inv, i, list.get(at + 1).name, scope);
        flush(p, inv, i);
    }

    static void set(SatchelInventory inv, int i, String name, String scope) {
        inv.home[i] = name;
        inv.homeScope[i] = name == null ? null : scope;
        inv.save();
    }
}
