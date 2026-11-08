package com.valier.tweaks;

import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Peaceful for single Personal Space dimensions. Minecraft 1.7.10 has one difficulty for the whole server, so this
 * does in one dimension what peaceful does everywhere: hostile mobs (anything that is an {@link IMob}) can't spawn there,
 * whatever spawns them (natural spawning, spawners, eggs, commands, other mods), and hostile mobs already there,
 * such as ones saved in chunks, are removed within a quarter of a second. They drop nothing.
 * <p>
 * Players switch it with {@code /pdim peaceful on|off} ({@link PdimCommand}). The set of peaceful dimensions is saved
 * with the world ({@code data/valiertweaks_peaceful.dat}).
 */
public final class PeacefulDims {

    private static final String PERSONAL_PROVIDER = "me.eigenraven.personalspace.world.PersonalWorldProvider";
    private static final int SWEEP_TICKS = 5;

    private int ticks;

    /** Whether the world is a Personal Space dimension. */
    public static boolean isPersonal(World world) {
        return world != null && world.provider != null
            && PERSONAL_PROVIDER.equals(
                world.provider.getClass()
                    .getName());
    }

    public static boolean isPeaceful(World world) {
        return world != null && !world.isRemote && TweaksConfig.peacefulDimensions && Data.get().dims.contains(world.provider.dimensionId);
    }

    public static boolean isPeaceful(int dim) {
        return Data.get().dims.contains(dim);
    }

    /** Turns peaceful on or off for a dimension; true if that changed anything. */
    public static boolean setPeaceful(int dim, boolean on) {
        Data data = Data.get();
        boolean changed = on ? data.dims.add(dim) : data.dims.remove(dim);
        if (changed) data.markDirty();
        return changed;
    }

    public static Set<Integer> peacefulDims() {
        return new TreeSet<>(Data.get().dims);
    }

    static boolean hostile(Entity entity) {
        return entity instanceof IMob;
    }

    /** Natural spawning and vanilla spawners ask first. */
    @SubscribeEvent
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (hostile(event.entity) && isPeaceful(event.world)) event.setResult(Event.Result.DENY);
    }

    /**
     * Everything else (eggs, commands, mod spawners, reinforcements). Entities loaded with a chunk are already in the
     * chunk's lists (addedToChunk), and cancelling those would leave them half in the world; the sweep removes them.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onJoin(EntityJoinWorldEvent event) {
        if (!event.entity.addedToChunk && hostile(event.entity) && isPeaceful(event.world)) event.setCanceled(true);
    }

    /** Removes hostile mobs that are already in a peaceful dimension, as peaceful difficulty does. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % SWEEP_TICKS != 0 || !TweaksConfig.peacefulDimensions) return;
        for (int dim : Data.get().dims) {
            WorldServer world = DimensionManager.getWorld(dim);
            if (world != null) sweep(world);
        }
    }

    static int sweep(World world) {
        int removed = 0;
        for (Object o : world.loadedEntityList) {
            Entity e = (Entity) o;
            if (!e.isDead && hostile(e)) {
                e.setDead();
                removed++;
            }
        }
        return removed;
    }

    /** The saved set of peaceful dimension ids, kept in the overworld's shared map storage. */
    public static final class Data extends WorldSavedData {

        static final String NAME = "valiertweaks_peaceful";
        final Set<Integer> dims = new TreeSet<>();
        private static Data cached;
        private static Object cachedFor;

        public Data(String name) {
            super(name);
        }

        static Data get() {
            WorldServer overworld = DimensionManager.getWorld(0);
            if (overworld == null) return cached != null ? cached : new Data(NAME);
            if (cached != null && cachedFor == overworld.mapStorage) return cached;
            Data data = (Data) overworld.mapStorage.loadData(Data.class, NAME);
            if (data == null) {
                data = new Data(NAME);
                overworld.mapStorage.setData(NAME, data);
            }
            cached = data;
            cachedFor = overworld.mapStorage;
            return data;
        }

        @Override
        public void readFromNBT(NBTTagCompound tag) {
            dims.clear();
            for (int d : tag.getIntArray("Dims")) dims.add(d);
        }

        @Override
        public void writeToNBT(NBTTagCompound tag) {
            int[] arr = new int[dims.size()];
            int i = 0;
            for (int d : dims) arr[i++] = d;
            tag.setIntArray("Dims", arr);
        }
    }
}
