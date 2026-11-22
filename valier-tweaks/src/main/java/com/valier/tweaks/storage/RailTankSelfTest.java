package com.valier.tweaks.storage;

import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.valier.tweaks.ModBlocks;
import com.valier.tweaks.SelfTest;
import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mods.railcraft.common.blocks.RailcraftBlocks;
import mods.railcraft.common.blocks.machine.MultiBlockPattern;
import mods.railcraft.common.blocks.machine.beta.EnumMachineBeta;
import mods.railcraft.common.blocks.machine.beta.TileTankBase;
import mods.railcraft.common.blocks.machine.beta.TileTankIronWall;

/**
 * The last part of {@code valiertweaks selftest satchels} (2.3.2): a Flask on a real Railcraft iron tank (Railcraft's
 * smallest), clicked on a wall (only a tank's valves are fluid handlers). Placed from Railcraft's own pattern; it forms
 * on its own ticks (up to 256: Railcraft tests a new structure on a block's clock), then: a click takes the tank's 8,000
 * mB of water (answered before the wall's own use, which opens its menu), a second click on the emptied tank puts it
 * back. Then the tank is removed and the step's summary logged. Public: Forge's event bus calls its handler from a
 * generated class.
 */
public final class RailTankSelfTest {

    final SatchelSelfTest t;
    final WorldServer world;
    int ox, oy, oz, sx, sy, sz, ticks;

    RailTankSelfTest(SatchelSelfTest t) {
        this.t = t;
        this.world = t.world;
    }

    /** Places the tank; true if it then waits for it to form (and finishes the step itself). */
    boolean start() {
        MultiBlockPattern p;
        int index = -1;
        try {
            Field f = TileTankBase.class.getDeclaredField("patterns");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<MultiBlockPattern> ps = (List<MultiBlockPattern>) f.get(null);
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < ps.size(); i++) {
                MultiBlockPattern q = ps.get(i);
                int v = q.getPatternWidthX() * q.getPatternHeight() * q.getPatternWidthZ();
                if (v < best) {
                    best = v;
                    index = i;
                }
            }
            p = ps.get(index);
        } catch (ReflectiveOperationException | RuntimeException e) {
            SelfTest.check(false, "Railcraft's tank patterns can be read (" + e + ")");
            return false;
        }
        sx = p.getPatternWidthX();
        sy = p.getPatternHeight();
        sz = p.getPatternWidthZ();
        ox = t.bx + 15 - sx; // the far corner of the step's chunk, clear of its other blocks
        oy = t.y;
        oz = t.bz + 15 - sz;
        clearBox();
        // Every tank position of the pattern as an iron tank wall (Railcraft's own placeIronTank leaves its master and top
        // positions, 'M' and 'T', empty, so that tank never forms); the water goes in once it has formed.
        java.util.Map<Character, Integer> blocks = new java.util.HashMap<>();
        for (char c : new char[] { 'B', 'M', 'T', 'W' }) blocks.put(c, EnumMachineBeta.TANK_IRON_WALL.ordinal());
        p.placeStructure(world, ox + p.getMasterOffsetX(), oy + p.getMasterOffsetY(), oz + p.getMasterOffsetZ(),
            RailcraftBlocks.getBlockMachineBeta(), blocks);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        SelfTest.log("waiting for a Railcraft iron tank (" + sx + "x" + sy + "x" + sz + ") to form");
        return true;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        SelfTest.keepLoaded(world, ox >> 4, oz >> 4, (ox + sx) >> 4, (oz + sz) >> 4);
        TileTankIronWall wall = wall();
        if (wall == null && ticks < 320) return; // Railcraft tests a new structure when a block's clock reaches 256
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            SelfTest.check(wall != null, "a Railcraft iron tank forms (" + ticks + " ticks" + (wall == null ? "; " + states() : "") + ")");
            if (wall != null) checks(wall);
        } catch (Throwable e) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] satchels (Railcraft tank) threw", e);
            SelfTest.check(false, "the Railcraft tank checks ran without an exception (" + e + ")");
        } finally {
            clearBox();
            t.clear();
            SelfTest.summary();
        }
    }

    /** A wall block of the formed tank, else null. */
    TileTankIronWall wall() {
        for (int x = ox; x < ox + sx; x++) for (int yy = oy; yy < oy + sy; yy++) for (int z = oz; z < oz + sz; z++) {
            TileEntity te = world.getTileEntity(x, yy, z);
            if (te instanceof TileTankIronWall && ((TileTankIronWall) te).isStructureValid() && RailTanks.of(te) != null) {
                return (TileTankIronWall) te;
            }
        }
        return null;
    }

    void checks(TileTankIronWall wall) {
        IFluidHandler tank = RailTanks.of(wall);
        tank.drain(ForgeDirection.UNKNOWN, Integer.MAX_VALUE / 2, true);
        tank.fill(ForgeDirection.UNKNOWN, new FluidStack(FluidRegistry.WATER, 8000), true);
        int x = wall.xCoord, y = wall.yCoord, z = wall.zCoord;
        SelfTest.check(!(wall instanceof IFluidHandler) && ItemSatchel.tank(wall, ForgeDirection.WEST.ordinal()) != null,
            "a Railcraft tank's wall isn't a fluid handler, but a Flask finds the tank through it");
        ItemStack hand = new ItemStack(ModBlocks.flask, 1, SatchelTier.LV);
        t.player.inventory.currentItem = 0;
        t.player.inventory.setInventorySlotContents(0, hand);
        boolean answered = t.use(hand, x, y, z, false);
        SatchelInventory h = SatchelInventory.of(hand);
        int inTank = water(tank);
        SatchelSelfTest.check(answered && h.fluids[0] != null && h.fluids[0].amount == 8000 && inTank == 0,
            "a Flask right-clicking a Railcraft iron tank's wall takes its 8,000 mB of water, before the wall opens its menu ("
                + (h.fluids[0] == null ? 0 : h.fluids[0].amount) + " mB in the Flask, " + inTank + " left)");
        t.use(hand, x, y, z, false);
        inTank = water(tank);
        SatchelSelfTest.check(h.isEmpty(0) && inTank == 8000, "a second click on the emptied tank puts the water back (" + inTank + " mB)");
        t.clearInventory();
    }

    /** What its blocks say (for a failure): each tank block's state. */
    String states() {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (int x = ox; x < ox + sx; x++) for (int yy = oy; yy < oy + sy; yy++) for (int z = oz; z < oz + sz; z++) {
            TileEntity te = world.getTileEntity(x, yy, z);
            if (!(te instanceof TileTankBase)) continue;
            if (n++ < 3) b.append(te.getClass()
                .getSimpleName())
                .append(' ')
                .append(((TileTankBase) te).getState())
                .append(", ");
        }
        return b.append(n)
            .append(" tank blocks")
            .toString();
    }

    static int water(IFluidHandler tank) {
        FluidTankInfo[] info = tank.getTankInfo(ForgeDirection.UNKNOWN);
        if (info == null || info.length == 0 || info[0].fluid == null) return 0;
        return info[0].fluid.amount;
    }

    void clearBox() {
        for (int x = ox - 1; x <= ox + sx; x++) for (int yy = oy; yy < oy + sy + 1; yy++) for (int z = oz - 1; z <= oz + sz; z++) {
            if (!world.isAirBlock(x, yy, z)) world.setBlockToAir(x, yy, z);
        }
    }
}
