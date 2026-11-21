package com.valier.tweaks;

import static com.valier.tweaks.SelfTest.check;
import static com.valier.tweaks.SelfTest.log;
import static com.valier.tweaks.SelfTest.summary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mods.railcraft.common.blocks.machine.TileMultiBlock;
import mods.railcraft.common.blocks.machine.beta.EnumMachineBeta;
import mods.railcraft.common.blocks.machine.beta.TileBoiler;
import mods.railcraft.common.blocks.machine.beta.TileBoilerFirebox;
import mods.railcraft.common.blocks.machine.beta.TileBoilerFireboxSolid;
import mods.railcraft.common.blocks.machine.beta.TileBoilerTankLow;

/**
 * {@code valiertweaks selftest railboiler}: builds Railcraft's smallest boiler (a solid firebox with a low pressure tank
 * on it) as a player would, in an empty corner of the test world, waits for it to form, then sets it off through
 * Railcraft's own code: the firebox is made superheated (400) with an empty water tank, water is poured in
 * ({@code fill}), and the firebox ticks. Dirt blocks two blocks from it show whether anything broke.
 * <ul>
 * <li>With {@code noRailcraftBoilerExplosions} on: Railcraft cooled it to 299 (so its explosion code ran), the
 * explosion was stopped, the boiler, its water and the dirt are all still there and the boiler is still formed.</li>
 * <li>Another explosion with no entity behind it, a few blocks away, still breaks its dirt.</li>
 * <li>With it off: the same steps blow the boiler's dirt away, as Railcraft has it (this shows the test reaches
 * Railcraft's real explosion).</li>
 * </ul>
 * The setting is put back and the area cleared after. Public: Forge's event bus calls its handlers from generated
 * classes.
 */
public final class RailBoilerSelfTest {

    final WorldServer world = DimensionManager.getWorld(0);
    final int x = ((SelfTest.CX + 8) << 4) + 8, z = ((SelfTest.CZ + 10) << 4) + 8; // far from the other tests
    final int ox = x + 7; // the other explosion's spot, in the same chunk
    final boolean setting = TweaksConfig.noRailcraftBoilerExplosions;
    final List<int[]> placed = new ArrayList<>();
    int y, ticks;
    double heatAfterFill;
    TileBoilerFireboxSolid firebox;
    TileBoilerTankLow tank;

    void start() {
        world.theChunkProviderServer.loadChunk(x >> 4, z >> 4);
        y = world.getHeightValue(x, z);
        firebox = place(EnumMachineBeta.BOILER_FIREBOX_SOLID.getItem(), y, "solid firebox", TileBoilerFireboxSolid.class);
        tank = place(EnumMachineBeta.BOILER_TANK_LOW_PRESSURE.getItem(), y + 1, "low pressure tank", TileBoilerTankLow.class);
        if (firebox == null || tank == null) {
            finish();
            return;
        }
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        log("waiting for the boiler to form");
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        SelfTest.keepLoaded(world, x >> 4, z >> 4, x >> 4, z >> 4);
        boolean formed = firebox.isStructureValid() && tank.isStructureValid() && firebox.getMasterBlock() != null;
        if (!formed && ticks < 200) return;
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        try {
            check(formed, "the boiler forms (" + ticks + " ticks)");
            if (formed) checks();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] railboiler threw", t);
            check(false, "the Railcraft boiler checks ran without an exception (" + t + ")");
        } finally {
            finish();
        }
    }

    void checks() {
        TileBoilerFirebox master = (TileBoilerFirebox) firebox.getMasterBlock();

        // On: Railcraft's explosion is stopped.
        TweaksConfig.noRailcraftBoilerExplosions = true;
        dirt(x, true);
        int before = RailcraftBoilers.stopped;
        int filled = setOff(master);
        int stopped = RailcraftBoilers.stopped - before;
        check(filled > 0 && heatAfterFill == 299,
            "water poured into the hot, dry firebox: Railcraft took it and cooled the boiler to 299, so its explosion code ran ("
                + filled + " mB, heat " + heatAfterFill + " before its tick)");
        check(stopped == 1, "noRailcraftBoilerExplosions on: the explosion is stopped (" + stopped + ")");
        check(world.getTileEntity(x, y, z) == firebox && world.getTileEntity(x, y + 1, z) == tank,
            "the firebox and tank are still there");
        check(dirt(x, false) == 4, "the dirt around it is still there (" + dirt(x, false) + " of 4)");
        FluidStack water = water(master).getFluid();
        check(water != null && water.amount > 0 && firebox.isStructureValid() && firebox.getMasterBlock() == master,
            "the boiler keeps its water and stays formed (" + (water == null ? 0 : water.amount) + " mB)");

        // On: any other explosion still breaks blocks.
        dirt(ox, true);
        before = RailcraftBoilers.stopped;
        world.createExplosion(null, ox, y + 1, z, 3.0F, true);
        check(RailcraftBoilers.stopped == before && dirt(ox, false) < 4,
            "another explosion with no entity behind it still breaks blocks (" + dirt(ox, false) + " of 4 dirt left)");

        // Off: Railcraft's own explosion goes off (the test reaches it).
        TweaksConfig.noRailcraftBoilerExplosions = false;
        before = RailcraftBoilers.stopped;
        setOff(master);
        check(RailcraftBoilers.stopped == before && dirt(x, false) < 4,
            "noRailcraftBoilerExplosions off: the boiler explodes and breaks the dirt, as Railcraft has it (" + dirt(x, false)
                + " of 4 dirt left)");
    }

    /**
     * Superheated with an empty water tank, water poured in, a tick of the firebox; what it took. The heat right after
     * the water goes in is kept in {@link #heatAfterFill}: the tick then cools an unfuelled boiler a little more.
     */
    int setOff(TileBoilerFirebox master) {
        master.boiler.setHeat(400);
        water(master).setFluid(null);
        int filled = firebox.fill(ForgeDirection.UP, new FluidStack(FluidRegistry.WATER, 1000), true);
        heatAfterFill = master.boiler.getHeat();
        ((TileEntity) firebox).updateEntity(); // where Railcraft explodes
        return filled;
    }

    static FluidTank water(TileBoiler master) {
        return master.getTankManager()
            .get(0);
    }

    /** Puts (or counts) dirt two blocks from (cx, y + 1, z) on four sides. */
    int dirt(int cx, boolean put) {
        int n = 0;
        int[][] at = { { cx + 2, z }, { cx - 2, z }, { cx, z + 2 }, { cx, z - 2 } };
        for (int[] p : at) {
            if (put) {
                world.setBlock(p[0], y + 1, p[1], Blocks.dirt);
                placed.add(new int[] { p[0], y + 1, p[1] });
            }
            if (world.getBlock(p[0], y + 1, p[1]) == Blocks.dirt) n++;
        }
        return n;
    }

    /** Places a Railcraft machine as a player would; null if that failed. */
    <T extends TileMultiBlock> T place(ItemStack stack, int at, String name, Class<T> type) {
        FakePlayer player = FakePlayerFactory
            .get(world, new GameProfile(UUID.nameUUIDFromBytes("vtweaks-railboiler".getBytes()), "[RailBoilerTest]"));
        boolean ok = stack != null && stack.getItem() instanceof ItemBlock
            && ((ItemBlock) stack.getItem()).placeBlockAt(stack, player, world, x, at, z, 1, 0.5F, 1F, 0.5F,
                stack.getItem()
                    .getMetadata(stack.getItemDamage()));
        if (ok) placed.add(new int[] { x, at, z });
        TileEntity te = world.getTileEntity(x, at, z);
        boolean right = ok && type.isInstance(te);
        check(right, "placed a Railcraft " + name + " (" + te + ")");
        return right ? type.cast(te) : null;
    }

    void finish() {
        TweaksConfig.noRailcraftBoilerExplosions = setting;
        check(TweaksConfig.noRailcraftBoilerExplosions == setting, "noRailcraftBoilerExplosions is back to its setting (" + setting + ")");
        for (int[] p : placed) world.setBlockToAir(p[0], p[1], p[2]);
        boolean gone = true;
        for (int[] p : placed) gone &= world.isAirBlock(p[0], p[1], p[2]);
        for (Object o : world.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(x - 12, 0, z - 12, ox + 12, 256, z + 12)))
            ((EntityItem) o).setDead();
        check(gone, "the test blocks are removed (" + placed.size() + ")");
        summary();
    }
}
