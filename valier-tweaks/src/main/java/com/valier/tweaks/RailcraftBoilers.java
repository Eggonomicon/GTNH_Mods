package com.valier.tweaks;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.ExplosionEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Railcraft boilers don't explode ({@code noRailcraftBoilerExplosions}). Railcraft blows a boiler up when water reaches
 * it while it is superheated (300 or more) with an empty water tank: it sets the boiler's heat to 299, and on the next
 * tick the boiler block that took the water calls {@code world.createExplosion(null, x, y, z, 5 + 0.1 per tank, true)}.
 * That explosion has no entity behind it, so neither {@code mobGriefing} nor GregTech's machine explosion settings stop
 * it. This cancels it as it starts (Forge's {@link ExplosionEvent.Start}) when it starts exactly at a Railcraft boiler
 * block, and lets the boiler hiss and smoke instead. The boiler keeps the water and the 299 heat Railcraft gave it and
 * carries on. Every other explosion is left alone. Railcraft's classes are looked up by name, so this needs no
 * Railcraft at build time and does nothing without it.
 */
public final class RailcraftBoilers {

    static final String BOILER_CLASS = "mods.railcraft.common.blocks.machine.beta.TileBoiler";

    /** Railcraft's boiler tile class (tanks and fireboxes), or null without Railcraft. */
    private final Class<?> boiler;

    /** Explosions stopped since the server started (the self-test reads it). */
    static int stopped;

    RailcraftBoilers(Class<?> boiler) {
        this.boiler = boiler;
    }

    /** A handler if Railcraft's boiler class is there, else null. */
    static RailcraftBoilers make() {
        try {
            return new RailcraftBoilers(Class.forName(BOILER_CLASS, false, RailcraftBoilers.class.getClassLoader()));
        } catch (Throwable e) {
            ValierTweaks.LOG.warn("Railcraft is loaded but its boiler class isn't there; Railcraft boilers can still explode ({})", e.toString());
            return null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onExplosion(ExplosionEvent.Start event) {
        if (!TweaksConfig.noRailcraftBoilerExplosions) return;
        World world = event.world;
        Explosion e = event.explosion;
        if (world == null || world.isRemote || e == null || e.exploder != null) return;
        int x = MathHelper.floor_double(e.explosionX), y = MathHelper.floor_double(e.explosionY),
            z = MathHelper.floor_double(e.explosionZ);
        // Railcraft explodes at the block's corner, exactly.
        if (x != e.explosionX || y != e.explosionY || z != e.explosionZ) return;
        if (y < 0 || y >= 256 || !world.blockExists(x, y, z)) return;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te == null || !boiler.isInstance(te)) return;
        event.setCanceled(true);
        stopped++;
        vent(world, x, y, z);
        ValierTweaks.LOG.info(
            "Stopped a Railcraft boiler explosion at {} {} {} in dimension {} (water reached it superheated and dry); it vents steam instead",
            x, y, z, world.provider.dimensionId);
    }

    /** A hiss and a puff of smoke, so players see something happened. */
    static void vent(World world, int x, int y, int z) {
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.fizz", 1.0F, 0.6F);
        if (world instanceof WorldServer)
            ((WorldServer) world).func_147487_a("largesmoke", x + 0.5, y + 1.0, z + 0.5, 24, 0.4, 0.5, 0.4, 0.02);
    }
}
