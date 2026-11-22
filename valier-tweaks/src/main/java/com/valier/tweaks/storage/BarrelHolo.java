package com.valier.tweaks.storage;

import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import net.dries007.holoInventory.compat.InventoryDecoder;
import net.dries007.holoInventory.compat.InventoryDecoderRegistry;

/**
 * HoloInventory (its Holo Glasses show what a block holds): a barrel shows its whole count, as JABBA's and Storage
 * Drawers' do, not the one stack its output slot gives automation. Read on the server thread (HoloInventory's tick).
 * Only touched when HoloInventory is loaded.
 */
public final class BarrelHolo {

    private BarrelHolo() {}

    public static void register() {
        InventoryDecoderRegistry.register(new InventoryDecoder(TileBarrel.class) {

            @Override
            public NBTTagList toNBT(IInventory inv) {
                NBTTagList list = new NBTTagList();
                BarrelStore st = ((TileBarrel) inv).store();
                if (st.item() != null && st.count() > 0) {
                    NBTTagCompound tag = st.item()
                        .writeToNBT(new NBTTagCompound());
                    tag.setInteger("Count", (int) Math.min(Integer.MAX_VALUE, st.count()));
                    list.appendTag(tag);
                }
                return list;
            }
        });
    }
}
