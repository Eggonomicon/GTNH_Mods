package com.valier.tweaks.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
 * A barrel as an AE2 storage bus sees it (only loaded with AE2): its whole count, not one stack, and AE2 is told when
 * it changes, at most every 10 ticks (with the barrel's label, {@link BarrelSync}), as one change of the difference.
 * One per store, so every storage bus on any barrel of a channel sees the channel's count.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
final class BarrelAe implements IStorageMonitorable, IMEMonitor<IAEItemStack>, BarrelStore.Watcher {

    private final BarrelStore store;
    private final Map<IMEMonitorHandlerReceiver<IAEItemStack>, Object> listeners = new HashMap<>();
    /** What AE2 was last told: the item and count. */
    private ItemStack postedItem;
    private long postedCount;

    private BarrelAe(BarrelStore store) {
        this.store = store;
        remember();
    }

    /** The store's monitor (made once and kept with it). */
    static BarrelAe of(BarrelStore store) {
        for (BarrelStore.Watcher w : store.watchers) if (w instanceof BarrelAe) return (BarrelAe) w;
        BarrelAe m = new BarrelAe(store);
        store.watchers.add(m);
        return m;
    }

    private void remember() {
        postedItem = store.item == null ? null : store.item.copy();
        postedCount = store.available(true);
    }

    private IAEItemStack ae(ItemStack s, long n) {
        IAEItemStack a = AEApi.instance()
            .storage()
            .createItemStack(s);
        if (a != null) a.setStackSize(n);
        return a;
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
        ItemStack s = input.getItemStack();
        long took = store.insert(s, input.getStackSize(), mode == Actionable.SIMULATE);
        if (took >= input.getStackSize()) return null;
        IAEItemStack left = input.copy();
        left.setStackSize(input.getStackSize() - took);
        return left;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        if (request == null || !store.matches(request.getItemStack())) return null;
        long k = Math.min(request.getStackSize(), store.available(true));
        if (k <= 0) return null;
        if (mode == Actionable.MODULATE) store.take(k);
        IAEItemStack out = request.copy();
        out.setStackSize(k);
        return out;
    }

    @Override
    public IItemList getAvailableItems(IItemList out) {
        long n = store.available(true);
        if (n > 0 && store.item != null) {
            IAEItemStack a = ae(store.item, n);
            if (a != null) out.add(a);
        }
        return out;
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
        return s != null && store.matches(s.getItemStack());
    }

    @Override
    public boolean canAccept(IAEItemStack s) {
        return s != null && store.takes(s.getItemStack());
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
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver l) {
        listeners.remove(l);
    }

    /** The store changed (every 10 ticks at most): AE2 gets the difference since it was last told. */
    @Override
    public void storeChanged() {
        if (listeners.isEmpty()) {
            remember();
            return;
        }
        long now = store.available(true);
        boolean sameItem = postedItem == null || store.item == null || BarrelStore.same(postedItem, store.item);
        java.util.List<IAEItemStack> changes = new java.util.ArrayList<>(2);
        if (sameItem) {
            ItemStack item = store.item != null ? store.item : postedItem;
            if (item != null && now != postedCount) changes.add(ae(item, now - postedCount));
        } else {
            if (postedCount > 0) changes.add(ae(postedItem, -postedCount));
            if (now > 0) changes.add(ae(store.item, now));
        }
        remember();
        changes.removeAll(Collections.singleton(null));
        if (changes.isEmpty()) return;
        for (Iterator<Map.Entry<IMEMonitorHandlerReceiver<IAEItemStack>, Object>> it = listeners.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<IMEMonitorHandlerReceiver<IAEItemStack>, Object> e = it.next();
            if (!e.getKey()
                .isValid(e.getValue())) {
                it.remove();
                continue;
            }
            e.getKey()
                .postChange(this, changes, null);
        }
    }
}
