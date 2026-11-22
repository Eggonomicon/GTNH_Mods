package com.valier.tweaks.grid;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.TweaksConfig;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModAPIManager;

/**
 * How the Grid talks to other mods' energy: GregTech EU (machines at their own voltage, cables at the point's), IC2 EU
 * and CoFH RF. Each mod's classes are only touched when it is there ({@link Gt}, {@link Ic2}, {@link Rf}).
 */
public final class GridEnergy {

    static final boolean GT = Loader.isModLoaded("gregtech");
    static final boolean IC2 = Loader.isModLoaded("IC2");
    static final boolean RF = ModAPIManager.INSTANCE.hasAPI("CoFHAPI|energy");
    static final boolean BAUBLES = Loader.isModLoaded("Baubles") || Loader.isModLoaded("Baubles|Expanded");

    private GridEnergy() {}

    /** RF per EU, both ways (GregTech's own: 3.6). */
    static double rfPerEu() {
        return Math.max(0.01, TweaksConfig.gridRfPerEu);
    }

    /** Whether a plug or point reaches out to this neighbour (drawn as an arm): anything that carries EU or RF. */
    public static boolean connects(TileEntity te, ForgeDirection towards) {
        if (te == null || te.isInvalid() || te instanceof TileGrid) return false;
        if (GT && Gt.is(te)) return true;
        if (IC2 && Ic2.isTile(te)) return true;
        return RF && Rf.connects(te, towards.getOpposite());
    }

    /** What a neighbour is to a point: 0 nothing, 1 a GregTech machine or cable, 2 an IC2 sink, 3 an RF receiver. */
    static int kind(TileEntity te, ForgeDirection towards) {
        if (te == null || te.isInvalid() || te instanceof TileGrid) return 0;
        if (GT && Gt.is(te)) return 1;
        if (IC2 && Ic2.isSink(te)) return 2;
        if (RF && Rf.isReceiver(te, towards.getOpposite())) return 3;
        return 0;
    }

    /**
     * Sends up to budget EU into a neighbour, at most maxVolts a packet and amps packets (GregTech; IC2 the same way).
     * fixedVolts &gt; 0: the voltage to use where a neighbour doesn't say what it takes (a GregTech cable). Returns EU
     * used.
     */
    static long push(TileEntity te, int kind, ForgeDirection towards, long budget, long maxVolts, long amps, long fixedVolts) {
        if (budget <= 0) return 0;
        ForgeDirection side = towards.getOpposite(); // the neighbour's side we touch
        switch (kind) {
            case 1:
                return Gt.push(te, side, budget, maxVolts, amps, fixedVolts);
            case 2:
                return Ic2.push(te, side, budget, maxVolts, amps);
            default:
                return 0;
        }
    }

    /** What a neighbour would take (EU) this tick, at most cap; a GregTech cable says nothing, so: cap. */
    static long demand(TileEntity te, int kind, ForgeDirection towards, long cap, long maxVolts, long amps) {
        switch (kind) {
            case 1:
                return Gt.demand(te, towards.getOpposite(), cap, maxVolts, amps);
            case 2:
                return Math.min(cap, (long) Ic2.demand(te));
            case 3:
                return Math.min(cap, (long) Math.ceil(Rf.demand(te, towards.getOpposite()) / rfPerEu()));
            default:
                return 0;
        }
    }

    /** RF into a receiver, up to rf; returns RF taken. */
    static int pushRf(TileEntity te, ForgeDirection towards, int rf) {
        return Rf.push(te, towards.getOpposite(), rf);
    }

    /** Charges a player's items (inventory, armour, baubles) by up to eu (EU; RF items at the RF rate). Returns EU used. */
    static long charge(EntityPlayer p, long eu) {
        long used = 0;
        used += chargeAll(p.inventory.armorInventory, eu - used);
        used += chargeAll(p.inventory.mainInventory, eu - used);
        if (BAUBLES && used < eu) {
            try {
                IInventory b = Baubles.of(p);
                if (b != null) for (int i = 0; i < b.getSizeInventory() && used < eu; i++) used += chargeOne(b.getStackInSlot(i), eu - used);
            } catch (Throwable ignored) {}
        }
        return used;
    }

    private static long chargeAll(ItemStack[] stacks, long eu) {
        long used = 0;
        for (ItemStack s : stacks) {
            if (used >= eu) break;
            used += chargeOne(s, eu - used);
        }
        return used;
    }

    static long chargeOne(ItemStack s, long eu) {
        if (s == null || eu <= 0) return 0;
        if (IC2) {
            long n = Ic2.charge(s, eu);
            if (n >= 0) return n;
        }
        if (RF) {
            int rf = Rf.chargeItem(s, (int) Math.min(Integer.MAX_VALUE, eu * rfPerEu()));
            if (rf >= 0) return (long) Math.ceil(rf / rfPerEu());
        }
        return 0;
    }

    /** IC2's energy net: a plug joins it as a sink (so IC2 generators and cables feed it). */
    static void ic2Load(TileEntity te, boolean load) {
        if (IC2) Ic2.post(te, load);
    }

    // ---- GregTech ------------------------------------------------------------------------------------------------------

    static final class Gt {

        static boolean is(TileEntity te) {
            return te instanceof gregtech.api.interfaces.tileentity.IEnergyConnected;
        }

        /** A GregTech cable (its tile says nothing about what it takes: 0 V, no input side), else null. */
        static gregtech.api.metatileentity.implementations.MTECable cable(TileEntity te) {
            if (!(te instanceof gregtech.api.interfaces.tileentity.IGregTechTileEntity)) return null;
            Object m = ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) te).getMetaTileEntity();
            return m instanceof gregtech.api.metatileentity.implementations.MTECable ? (gregtech.api.metatileentity.implementations.MTECable) m
                : null;
        }

        static long demand(TileEntity te, ForgeDirection side, long cap, long maxVolts, long amps) {
            if (cable(te) != null) return cap; // a cable says only by taking
            if (te instanceof gregtech.api.interfaces.tileentity.IBasicEnergyContainer) {
                gregtech.api.interfaces.tileentity.IBasicEnergyContainer m = (gregtech.api.interfaces.tileentity.IBasicEnergyContainer) te;
                if (!m.inputEnergyFrom(side)) return 0;
                long v = Math.min(m.getInputVoltage(), maxVolts);
                if (v <= 0) return 0;
                long room = Math.max(0, m.getEUCapacity() - m.getStoredEU());
                long a = Math.min(amps, Math.max(0, m.getInputAmperage()));
                return Math.min(cap, Math.min(room + v, a * v));
            }
            return cap; // a cable: it says only by taking
        }

        static long push(TileEntity te, ForgeDirection side, long budget, long maxVolts, long amps, long fixedVolts) {
            gregtech.api.interfaces.tileentity.IEnergyConnected c = (gregtech.api.interfaces.tileentity.IEnergyConnected) te;
            gregtech.api.metatileentity.implementations.MTECable cable = cable(te);
            long v;
            if (cable != null) { // the voltage set at the point, never over the cable's own (it doesn't burn), in its amps
                v = Math.min(fixedVolts > 0 ? Math.min(fixedVolts, maxVolts) : maxVolts, cable.mVoltage);
                amps = Math.min(amps, Math.max(1, cable.mAmperage));
            } else if (!c.inputEnergyFrom(side)) return 0;
            else if (te instanceof gregtech.api.interfaces.tileentity.IBasicEnergyContainer) {
                gregtech.api.interfaces.tileentity.IBasicEnergyContainer m = (gregtech.api.interfaces.tileentity.IBasicEnergyContainer) te;
                v = Math.min(m.getInputVoltage(), maxVolts); // never over what it takes: nothing explodes
                amps = Math.min(amps, Math.max(0, m.getInputAmperage()));
            } else v = fixedVolts > 0 ? Math.min(fixedVolts, maxVolts) : maxVolts;
            if (v <= 0) return 0;
            long a = Math.min(amps, budget / v);
            if (a <= 0) return 0;
            long took = c.injectEnergyUnits(side, v, a);
            return Math.max(0, took) * v;
        }
    }

    // ---- IC2 -----------------------------------------------------------------------------------------------------------

    static final class Ic2 {

        static boolean isTile(TileEntity te) {
            return te instanceof ic2.api.energy.tile.IEnergyTile;
        }

        static boolean isSink(TileEntity te) {
            return te instanceof ic2.api.energy.tile.IEnergySink;
        }

        static double demand(TileEntity te) {
            return Math.max(0, ((ic2.api.energy.tile.IEnergySink) te).getDemandedEnergy());
        }

        static long push(TileEntity te, ForgeDirection side, long budget, long maxVolts, long amps) {
            ic2.api.energy.tile.IEnergySink s = (ic2.api.energy.tile.IEnergySink) te;
            if (!s.acceptsEnergyFrom(null, side)) return 0;
            int tier = s.getSinkTier();
            long v = Math.min(maxVolts, tier <= 0 ? 8 : tier > GridTier.COUNT ? maxVolts : GridTier.VOLTS[tier - 1]);
            long used = 0;
            for (long k = 0; k < amps && used + v <= budget; k++) {
                double want = s.getDemandedEnergy();
                if (want <= 0) break;
                double packet = Math.min(v, want);
                double left = s.injectEnergy(side, packet, v);
                long took = (long) Math.ceil(packet - Math.max(0, left));
                if (took <= 0) break;
                used += took;
            }
            return used;
        }

        static long charge(ItemStack s, long eu) {
            if (!(s.getItem() instanceof ic2.api.item.IElectricItem)) return -1;
            return (long) Math.ceil(ic2.api.item.ElectricItem.manager.charge(s, eu, Integer.MAX_VALUE, true, false));
        }

        static void post(TileEntity te, boolean load) {
            if (!(te instanceof ic2.api.energy.tile.IEnergyTile)) return;
            MinecraftForge.EVENT_BUS.post(load ? new ic2.api.energy.event.EnergyTileLoadEvent((ic2.api.energy.tile.IEnergyTile) te)
                : new ic2.api.energy.event.EnergyTileUnloadEvent((ic2.api.energy.tile.IEnergyTile) te));
        }
    }

    // ---- RF ------------------------------------------------------------------------------------------------------------

    static final class Rf {

        static boolean connects(TileEntity te, ForgeDirection side) {
            return te instanceof cofh.api.energy.IEnergyConnection && ((cofh.api.energy.IEnergyConnection) te).canConnectEnergy(side);
        }

        static boolean isReceiver(TileEntity te, ForgeDirection side) {
            return te instanceof cofh.api.energy.IEnergyReceiver && ((cofh.api.energy.IEnergyReceiver) te).canConnectEnergy(side);
        }

        static int demand(TileEntity te, ForgeDirection side) {
            return Math.max(0, ((cofh.api.energy.IEnergyReceiver) te).receiveEnergy(side, Integer.MAX_VALUE, true));
        }

        static int push(TileEntity te, ForgeDirection side, int rf) {
            if (rf <= 0) return 0;
            return Math.max(0, ((cofh.api.energy.IEnergyReceiver) te).receiveEnergy(side, rf, false));
        }

        static int chargeItem(ItemStack s, int rf) {
            if (!(s.getItem() instanceof cofh.api.energy.IEnergyContainerItem)) return -1;
            return Math.max(0, ((cofh.api.energy.IEnergyContainerItem) s.getItem()).receiveEnergy(s, rf, false));
        }
    }

    static final class Baubles {

        static IInventory of(EntityPlayer p) {
            return baubles.api.BaublesApi.getBaubles(p);
        }
    }
}
