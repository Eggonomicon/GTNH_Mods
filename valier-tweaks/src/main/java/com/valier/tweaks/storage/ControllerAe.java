package com.valier.tweaks.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/**
 * A Barrel Controller's network as an AE2 storage bus sees it (only loaded with AE2): every barrel's whole count, one
 * bus for the lot. Items AE2 puts in go where the controller sends them (exactly that item: AE2 keeps each item apart).
 * <p>
 * Cost: nothing while no storage bus listens. While one does, each store it covers tells it of changes at most every 10
 * ticks ({@link BarrelSync}), as the difference since the last time; when barrels join or leave, the bus is told what
 * they add or take away, found a tick later from {@link TileBarrelController#refreshWatched()}.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class ControllerAe implements IStorageMonitorable, IMEMonitor<IAEItemStack> {

    private final TileBarrelController ctrl;
    private final Map<IMEMonitorHandlerReceiver<IAEItemStack>, Object> listeners = new HashMap<>();
    /** The stores it watches (only while someone listens), each with what AE2 was last told of it. */
    private final IdentityHashMap<BarrelStore, Seen> seen = new IdentityHashMap<>();

    private ControllerAe(TileBarrelController ctrl) {
        this.ctrl = ctrl;
    }

    static ControllerAe of(TileBarrelController c) {
        if (c.ae == null) c.ae = new ControllerAe(c);
        return (ControllerAe) c.ae;
    }

    /** Called by the controller without touching AE2's classes unless AE2 made its view. */
    static final class Hook {

        private Hook() {}

        static void rebuilt(TileBarrelController c) {
            ((ControllerAe) c.ae).rebuilt();
        }

        static void gone(TileBarrelController c) {
            ControllerAe a = (ControllerAe) c.ae;
            a.detach();
            a.listeners.clear();
            TileBarrelController.aeWatched.remove(c);
        }
    }

    private static IAEItemStack ae(ItemStack s, long n) {
        IAEItemStack a = AEApi.instance()
            .storage()
            .createItemStack(s);
        if (a != null) a.setStackSize(n);
        return a;
    }

    // ---- watching the stores ---------------------------------------------------------------------------------------

    /** One store as AE2 was last told of it. */
    private final class Seen implements BarrelStore.Watcher {

        final BarrelStore st;
        ItemStack item;
        long count;

        Seen(BarrelStore st) {
            this.st = st;
            remember();
        }

        void remember() {
            item = st.item == null ? null : st.item.copy();
            count = st.available(true);
        }

        @Override
        public void storeChanged() {
            List<IAEItemStack> ch = new ArrayList<>(2);
            long now = st.available(true);
            boolean same = item == null || st.item == null || BarrelStore.same(item, st.item);
            if (same) {
                ItemStack it = st.item != null ? st.item : item;
                if (it != null && now != count) ch.add(ae(it, now - count));
            } else {
                if (count > 0) ch.add(ae(item, -count));
                if (now > 0) ch.add(ae(st.item, now));
            }
            remember();
            post(ch);
        }
    }

    /** Starts watching the network's stores (AE2 reads the whole list itself when it starts listening). */
    private void attach() {
        for (BarrelStore st : ctrl.stores) {
            if (seen.containsKey(st)) continue;
            Seen s = new Seen(st);
            st.watchers.add(s);
            seen.put(st, s);
        }
    }

    private void detach() {
        for (Seen s : seen.values()) s.st.watchers.remove(s);
        seen.clear();
    }

    /** The network was found again: AE2 is told what joining stores add and leaving ones take away. */
    private void rebuilt() {
        if (listeners.isEmpty()) {
            detach();
            return;
        }
        IdentityHashMap<BarrelStore, Seen> next = new IdentityHashMap<>();
        List<IAEItemStack> ch = new ArrayList<>();
        for (BarrelStore st : ctrl.stores) {
            Seen s = seen.remove(st);
            if (s == null) {
                s = new Seen(st);
                st.watchers.add(s);
                if (s.count > 0 && s.item != null) ch.add(ae(s.item, s.count));
            }
            next.put(st, s);
        }
        for (Seen s : seen.values()) {
            s.st.watchers.remove(s);
            if (s.count > 0 && s.item != null) ch.add(ae(s.item, -s.count));
        }
        seen.clear();
        seen.putAll(next);
        post(ch);
    }

    private void post(List<IAEItemStack> ch) {
        ch.removeAll(Collections.singleton(null));
        if (ch.isEmpty() || listeners.isEmpty()) return;
        for (Iterator<Map.Entry<IMEMonitorHandlerReceiver<IAEItemStack>, Object>> it = listeners.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<IMEMonitorHandlerReceiver<IAEItemStack>, Object> e = it.next();
            if (!e.getKey()
                .isValid(e.getValue())) {
                it.remove();
                continue;
            }
            e.getKey()
                .postChange(this, ch, null);
        }
        if (listeners.isEmpty()) stopWatching();
    }

    private void stopWatching() {
        detach();
        TileBarrelController.aeWatched.remove(ctrl);
    }

    // ---- IStorageMonitorable --------------------------------------------------------------------------------------

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        return this;
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        return null;
    }

    // ---- IMEInventory ------------------------------------------------------------------------------------------------

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable mode, BaseActionSource src) {
        if (input == null) return null;
        long took = ctrl.insert(input.getItemStack(), input.getStackSize(), mode == Actionable.SIMULATE, true);
        if (took >= input.getStackSize()) return null;
        IAEItemStack left = input.copy();
        left.setStackSize(input.getStackSize() - took);
        return left;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        if (request == null) return null;
        long k = ctrl.take(request.getItemStack(), request.getStackSize(), mode == Actionable.SIMULATE);
        if (k <= 0) return null;
        IAEItemStack out = request.copy();
        out.setStackSize(k);
        return out;
    }

    @Override
    public IItemList getAvailableItems(IItemList out) {
        ctrl.network();
        for (BarrelStore st : ctrl.stores) {
            long n = st.available(true);
            if (n <= 0 || st.item == null) continue;
            IAEItemStack a = ae(st.item, n);
            if (a != null) out.add(a);
        }
        return out;
    }

    /** One item's amount, from the controller's lookup (AE2's default would list the whole network to find it). */
    public IAEItemStack getAvailableItem(IAEItemStack request, int iteration) {
        if (request == null) return null;
        long n = ctrl.available(request.getItemStack());
        if (n <= 0) return null;
        IAEItemStack a = request.copy();
        a.setStackSize(n);
        return a;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public IItemList<IAEItemStack> getStorageList() {
        return getAvailableItems(AEApi.instance()
            .storage()
            .createItemList());
    }

    // ---- IMEInventoryHandler ------------------------------------------------------------------------------------------

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(IAEItemStack s) {
        return s != null && !ctrl.manyPeek(s.getItemStack(), false)
            .isEmpty();
    }

    @Override
    public boolean canAccept(IAEItemStack s) {
        return s != null && ctrl.insert(s.getItemStack(), 1, true, true) > 0;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int pass) {
        return true;
    }

    // ---- IBaseMonitor -------------------------------------------------------------------------------------------------

    @Override
    public void addListener(IMEMonitorHandlerReceiver l, Object verification) {
        listeners.put(l, verification);
        TileBarrelController.aeWatched.add(ctrl);
        ctrl.network();
        attach();
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver l) {
        listeners.remove(l);
        if (listeners.isEmpty()) stopWatching();
    }

    /** For the self-test: stores watched and listeners. */
    int[] watching() {
        return new int[] { seen.size(), listeners.size() };
    }
}
