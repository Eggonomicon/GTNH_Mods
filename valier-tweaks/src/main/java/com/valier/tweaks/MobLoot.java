package com.valier.tweaks;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * The loot of one mob, without the mob: it is made in memory (never added to the world, so no AI, pathfinding or
 * entity ticking), then put through what a player kill runs in {@code EntityLivingBase.onDeath}: dropFewItems,
 * dropEquipment, the 2.5% rare drop (a player kill, no Looting), and Forge's LivingDropsEvent, so loot other mods add
 * comes too. The XP a player kill gives can be counted too ({@link #roll} with an {@code xp} counter).
 * <p>
 * While that runs, anything the mob tries to add to the world is stopped ({@link Guard}): items join the loot, and
 * mobs (a slime's smaller slimes, a spider jockey's skeleton) are killed the same way for their loot, up to
 * {@link #MAX_MOBS} per roll.
 */
public final class MobLoot {

    static final int MAX_MOBS = 24;

    private static final GameProfile KILLER = new GameProfile(UUID.fromString("5a1e7c00-0000-4000-8000-00000000100f"),
        "[Loot Spawner]");
    /** One killer per world (Forge's own cache keeps only the first world's), dropped when its world unloads. */
    private static final java.util.Map<World, FakePlayer> killers = new java.util.HashMap<>();

    private static final Method DROP_FEW = ReflectionHelper.findMethod(EntityLivingBase.class, null,
        new String[] { "dropFewItems", "func_70628_a" }, boolean.class, int.class);
    private static final Method DROP_EQUIPMENT = ReflectionHelper.findMethod(EntityLivingBase.class, null,
        new String[] { "dropEquipment", "func_82160_b" }, boolean.class, int.class);
    private static final Method DROP_RARE = ReflectionHelper.findMethod(EntityLivingBase.class, null,
        new String[] { "dropRareDrop", "func_70600_l" }, int.class);
    private static final Method CAN_DROP_LOOT = ReflectionHelper.findMethod(EntityLivingBase.class, null,
        new String[] { "func_146066_aG" });
    private static final Method GET_XP = ReflectionHelper.findMethod(EntityLivingBase.class, null,
        new String[] { "getExperiencePoints", "func_70693_a" }, EntityPlayer.class);

    /** Set while a roll runs: the world, and what tried to join it. */
    private static World active;
    private static final List<ItemStack> joinedItems = new ArrayList<>();
    private static final List<EntityLivingBase> joinedMobs = new ArrayList<>();
    private static int joinedXp; // XP orbs a mod tried to add (Infernal Mobs' elites drop one)
    private static final Set<String> warned = new HashSet<>();

    private MobLoot() {}

    /** One thing a spawner can spawn: the entity name, the NBT laid over it (or null), and its weight. */
    public static final class Candidate {

        final String type;
        final NBTTagCompound props;
        final boolean egg;
        final int weight;

        Candidate(String type, NBTTagCompound props, boolean egg, int weight) {
            this.type = type;
            this.props = props;
            this.egg = egg;
            this.weight = Math.max(1, weight);
        }
    }

    /**
     * What a spawner with this NBT spawns, the way vanilla picks it: its SpawnPotentials if it has any, else its
     * EntityId. A mob whose NBT is empty (apart from the loot spawner mark) and whose spawner had no SpawnData of its
     * own ({@code egg}) is dressed by onSpawnWithEgg first, as vanilla does.
     */
    static List<Candidate> candidates(NBTTagCompound spawner, boolean egg) {
        List<Candidate> out = new ArrayList<>();
        NBTTagList potentials = spawner.getTagList("SpawnPotentials", 10);
        for (int i = 0; i < potentials.tagCount(); i++) {
            NBTTagCompound p = potentials.getCompoundTagAt(i);
            String type = p.getString("Type");
            if (type.isEmpty()) continue;
            NBTTagCompound props = (NBTTagCompound) p.getCompoundTag("Properties").copy();
            props.removeTag(LootSpawnerTile.MARK);
            out.add(new Candidate(type, props, egg && props.hasNoTags(), p.getInteger("Weight")));
        }
        if (out.isEmpty()) {
            String type = spawner.getString("EntityId");
            NBTTagCompound props = (NBTTagCompound) spawner.getCompoundTag("SpawnData").copy();
            props.removeTag(LootSpawnerTile.MARK);
            if (!type.isEmpty()) out.add(new Candidate(type, props, egg && props.hasNoTags(), 1));
        }
        return out.isEmpty() ? Collections.<Candidate>emptyList() : out;
    }

    static Candidate pick(List<Candidate> candidates, Random rand) {
        if (candidates.isEmpty()) return null;
        int total = 0;
        for (Candidate c : candidates) total += c.weight;
        int r = rand.nextInt(total);
        for (Candidate c : candidates) {
            r -= c.weight;
            if (r < 0) return c;
        }
        return candidates.get(0);
    }

    static boolean blocked(String type) {
        for (String b : TweaksConfig.lootSpawnerBlockedMobs) if (b.trim().equalsIgnoreCase(type)) return true;
        return false;
    }

    /** The loot of one kill of this candidate at x y z (its smaller slimes, riders and so on included). */
    static List<ItemStack> roll(WorldServer world, double x, double y, double z, Candidate c) {
        return roll(world, x, y, z, c, null, 0);
    }

    /**
     * The same, killed as if with a Looting {@code looting} weapon, adding the XP those kills give a player to
     * {@code xp[0]} (when not null).
     */
    static List<ItemStack> roll(WorldServer world, double x, double y, double z, Candidate c, int[] xp, int looting) {
        List<ItemStack> out = new ArrayList<>();
        if (c == null || blocked(c.type)) return out;
        World before = active;
        active = world;
        joinedItems.clear();
        joinedMobs.clear();
        joinedXp = 0;
        try {
            Entity entity = EntityList.createEntityByName(c.type, world);
            if (!(entity instanceof EntityLiving)) return out; // a minecart, an arrow: nothing to loot
            entity.setLocationAndAngles(x, y, z, world.rand.nextFloat() * 360.0F, 0.0F);
            if (c.egg) {
                // A baby zombie may climb onto a real chicken nearby (a chicken jockey) and mark it as one: undo that.
                List<?> chickens = world.getEntitiesWithinAABB(EntityChicken.class, entity.boundingBox.expand(6.0, 4.0, 6.0));
                boolean[] jockey = new boolean[chickens.size()];
                for (int i = 0; i < jockey.length; i++) jockey[i] = ((EntityChicken) chickens.get(i)).func_152116_bZ();
                ((EntityLiving) entity).onSpawnWithEgg((IEntityLivingData) null);
                if (entity.ridingEntity != null && entity.ridingEntity.addedToChunk) entity.mountEntity(null);
                for (int i = 0; i < jockey.length; i++) ((EntityChicken) chickens.get(i)).func_152117_i(jockey[i]);
            } else if (c.props != null && !c.props.hasNoTags()) {
                NBTTagCompound tag = new NBTTagCompound();
                entity.writeToNBTOptional(tag);
                for (Object key : c.props.func_150296_c()) {
                    tag.setTag((String) key, c.props.getTag((String) key).copy());
                }
                entity.readFromNBT(tag);
                entity.setLocationAndAngles(x, y, z, entity.rotationYaw, 0.0F);
            }
            // Special Mobs swaps a spawned mob for one of its variants a tick after it joins the world; this one never
            // joins, so ask it now. The variant it spawns is caught by Guard (into joinedMobs) and the original dies.
            if (TweaksConfig.lootSpawnerSpecialMobs) Special.replace((EntityLiving) entity);
            ArrayDeque<EntityLivingBase> queue = new ArrayDeque<>();
            if (!entity.isDead) queue.add((EntityLivingBase) entity);
            queue.addAll(joinedMobs); // a jockey's rider, added by onSpawnWithEgg
            joinedMobs.clear();
            int killed = 0;
            while (!queue.isEmpty() && killed++ < MAX_MOBS) {
                kill(world, queue.poll(), out, xp, looting);
                queue.addAll(joinedMobs);
                joinedMobs.clear();
            }
            out.addAll(joinedItems);
            if (xp != null) xp[0] += joinedXp;
        } catch (Throwable t) {
            if (warned.add(c.type)) ValierTweaks.LOG.warn("Loot spawner: rolling the loot of " + c.type + " failed", t);
        } finally {
            active = before;
            joinedItems.clear();
            joinedMobs.clear();
            joinedXp = 0;
        }
        return out;
    }

    private static void kill(WorldServer world, EntityLivingBase mob, List<ItemStack> out, int[] xp, int looting)
        throws Exception {
        // Infernal Mobs decides as a mob joins the world whether it is an elite; this one never joins, so ask it here.
        if (TweaksConfig.lootSpawnerInfernalMobs) Infernal.process(mob);
        try {
            killNow(world, mob, out, xp, looting);
        } finally {
            Infernal.forget(mob); // it forgets elites in its drop event, but not if another mod cancelled that
        }
    }

    /** Test hook: the class of every mob killed for loot, while not null. */
    static List<String> killedLog;

    private static void killNow(WorldServer world, EntityLivingBase mob, List<ItemStack> out, int[] xp, int looting)
        throws Exception {
        if (killedLog != null) killedLog.add(mob.getClass()
            .getName());
        FakePlayer killer = killers.get(world);
        if (killer == null) killers.put(world, killer = new FakePlayer(world, KILLER));
        killer.setPosition(mob.posX, mob.posY, mob.posZ);
        ReflectionHelper.setPrivateValue(EntityLivingBase.class, mob, 100, "recentlyHit", "field_70718_bc");
        ReflectionHelper.setPrivateValue(EntityLivingBase.class, mob, (EntityPlayer) killer, "attackingPlayer",
            "field_70717_bb");
        mob.captureDrops = true;
        mob.capturedDrops.clear();
        int special = 0;
        try {
            if ((Boolean) CAN_DROP_LOOT.invoke(mob) && world.getGameRules()
                .getGameRuleBooleanValue("doMobLoot")) {
                DROP_FEW.invoke(mob, true, looting);
                DROP_EQUIPMENT.invoke(mob, true, looting);
                special = mob.getRNG()
                    .nextInt(200) - looting;
                if (special < 5) DROP_RARE.invoke(mob, special <= 0 ? 1 : 0);
            }
        } finally {
            mob.captureDrops = false;
        }
        ArrayList<EntityItem> drops = new ArrayList<>(mob.capturedDrops);
        mob.capturedDrops.clear();
        boolean cancelled = false;
        try {
            cancelled = ForgeHooks.onLivingDrops(mob, DamageSource.causePlayerDamage(killer), drops, looting, true, special);
        } catch (Throwable t) {
            String key = EntityList.getEntityString(mob) + " drops event";
            if (warned.add(key)) ValierTweaks.LOG.warn("Loot spawner: a mod's drop handler failed for " + key, t);
        }
        if (!cancelled) {
            for (EntityItem item : drops) {
                ItemStack stack = item.getEntityItem();
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) out.add(stack.copy());
            }
        }
        // The XP vanilla drops as the mob's death ends (onDeathUpdate): for a player kill, if it drops loot at all.
        if (xp != null && (Boolean) CAN_DROP_LOOT.invoke(mob) && world.getGameRules()
            .getGameRuleBooleanValue("doMobLoot")) {
            try {
                xp[0] += Math.max(0, (Integer) GET_XP.invoke(mob, killer));
            } catch (Throwable t) {
                String key = EntityList.getEntityString(mob) + " XP";
                if (warned.add(key)) ValierTweaks.LOG.warn("Loot spawner: counting the XP of " + key + " failed", t);
            }
        }
        // Loot some mods hand straight to the killer.
        for (int i = 0; i < killer.inventory.getSizeInventory(); i++) {
            ItemStack stack = killer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() != null && stack.stackSize > 0) out.add(stack.copy());
            killer.inventory.setInventorySlotContents(i, null);
        }
        mob.setHealth(0.0F);
        mob.setDead(); // a slime splits here; its smaller slimes are caught by onJoin
    }

    /**
     * While a roll runs, nothing it makes may join the world: items join the loot instead, and mobs are killed for
     * theirs. Entities loaded with a chunk (already added to it) are left alone. Runs even if another mod already
     * cancelled the join.
     */
    public static final class Guard {

        @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
        public void onJoin(EntityJoinWorldEvent event) {
            // Any world: a drop handler may spawn into the killer's world. Nothing else runs during a roll.
            if (active == null || event.world.isRemote || event.entity.addedToChunk) return;
            Entity e = event.entity;
            if (e instanceof EntityItem) {
                ItemStack stack = ((EntityItem) e).getEntityItem();
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) joinedItems.add(stack.copy());
            } else if (e instanceof EntityLivingBase && !(e instanceof EntityPlayer)) {
                joinedMobs.add((EntityLivingBase) e);
            } else if (e instanceof EntityXPOrb) {
                joinedXp += Math.max(0, ((EntityXPOrb) e).getXpValue());
            }
            event.setCanceled(true);
        }

        @SubscribeEvent
        public void onUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
            killers.remove(event.world);
        }
    }

    /**
     * Infernal Mobs, found by name: {@code processEntitySpawn} (what it runs as a mob joins the world: by its own
     * rarity settings the mob may become an elite) and {@code removeEntFromElites}. Its own drop handler, which our
     * drop event reaches, then drops the elite's loot and XP into the world, where {@link Guard} catches them.
     */
    static final class Infernal {

        private static boolean looked;
        private static Object core;
        private static Method process, remove, isRare;

        private static boolean ready() {
            if (!looked) {
                looked = true;
                try {
                    Class<?> c = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore", false,
                        MobLoot.class.getClassLoader());
                    core = c.getMethod("instance")
                        .invoke(null);
                    process = c.getMethod("processEntitySpawn", EntityLivingBase.class);
                    remove = c.getMethod("removeEntFromElites", EntityLivingBase.class);
                    isRare = c.getMethod("getIsRareEntity", EntityLivingBase.class);
                } catch (Throwable t) {
                    core = null; // not installed
                }
            }
            return core != null;
        }

        static boolean present() {
            return ready();
        }

        static void process(EntityLivingBase mob) {
            if (!ready()) return;
            try {
                process.invoke(core, mob);
            } catch (Throwable t) {
                if (warned.add("infernal")) ValierTweaks.LOG.warn("Loot spawner: Infernal Mobs failed to set up a mob", t);
            }
        }

        static boolean isElite(EntityLivingBase mob) {
            try {
                return ready() && (Boolean) isRare.invoke(null, mob);
            } catch (Throwable t) {
                return false;
            }
        }

        static void forget(EntityLivingBase mob) {
            if (!ready()) return;
            try {
                remove.invoke(null, mob);
            } catch (Throwable ignored) {}
        }

        static Object core() {
            return ready() ? core : null;
        }
    }

    /**
     * Special Mobs, found by name: {@code MobHelper.canReplace} and {@code ReplacementEntry.replace}, what its join
     * handler queues and runs a tick later. The variant it makes is spawned into the world (Guard catches it) and the
     * original is set dead.
     */
    static final class Special {

        private static boolean looked;
        private static Method canReplace, replace;
        private static java.lang.reflect.Constructor<?> entry;

        private static boolean ready() {
            if (!looked) {
                looked = true;
                try {
                    ClassLoader cl = MobLoot.class.getClassLoader();
                    canReplace = Class.forName("toast.specialMobs.MobHelper", false, cl)
                        .getMethod("canReplace", EntityLiving.class);
                    Class<?> e = Class.forName("toast.specialMobs.ReplacementEntry", false, cl);
                    entry = e.getConstructor(EntityLiving.class);
                    replace = e.getMethod("replace");
                } catch (Throwable t) {
                    canReplace = null; // not installed
                }
            }
            return canReplace != null;
        }

        static boolean present() {
            return ready();
        }

        /** Swaps in a variant if Special Mobs would; true if it did (the original is then dead). */
        static boolean replace(EntityLiving mob) {
            if (!ready()) return false;
            try {
                if (!(Boolean) canReplace.invoke(null, mob)) return false;
                replace.invoke(entry.newInstance(mob));
                return mob.isDead;
            } catch (Throwable t) {
                if (warned.add("special")) ValierTweaks.LOG.warn("Loot spawner: Special Mobs failed to swap a mob", t);
                return false;
            }
        }
    }

    static boolean sameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem() && a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }

    /** Merges equal stacks (up to their stack size). */
    static List<ItemStack> merge(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            s = s.copy();
            for (ItemStack o : out) {
                if (s.stackSize <= 0) break;
                if (!sameItem(o, s)) continue;
                int move = Math.min(s.stackSize, o.getMaxStackSize() - o.stackSize);
                o.stackSize += move;
                s.stackSize -= move;
            }
            while (s.stackSize > 0) {
                int n = Math.min(s.stackSize, Math.max(1, s.getMaxStackSize()));
                ItemStack part = s.copy();
                part.stackSize = n;
                out.add(part);
                s.stackSize -= n;
            }
        }
        return out;
    }
}
