package com.valier.tweaks.storage;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.IFluidHandler;

import mods.railcraft.common.blocks.machine.ITankTile;
import mods.railcraft.common.blocks.machine.TileMultiBlock;

/**
 * Railcraft's tanks for a Flask in hand (2.3.2): only a tank's valves are fluid handlers, so a click on its wall or gauge
 * goes to the formed tank's own tanks (what Railcraft's bucket clicks use). The tank shows the change on its next sync,
 * as after a bucket. Only touched with Railcraft loaded.
 */
final class RailTanks {

    private RailTanks() {}

    /** A Railcraft tank block, formed or not. */
    static boolean is(TileEntity te) {
        return te instanceof ITankTile;
    }

    /** The formed tank te is part of, else null. */
    static IFluidHandler of(TileEntity te) {
        if (!(te instanceof ITankTile)) return null;
        if (te instanceof TileMultiBlock && !((TileMultiBlock) te).isStructureValid()) return null;
        return ((ITankTile) te).getTankManager();
    }
}
