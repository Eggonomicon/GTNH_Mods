package com.valier.tweaks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.event.world.BlockEvent;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.tileentities.boilers.MTEBoiler;
import gregtech.common.tileentities.boilers.MTEBoilerBronze;
import gregtech.common.tileentities.boilers.MTEBoilerSteel;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;
import gtPlusPlus.xmod.gregtech.common.tileentities.generators.MTEAdvancedBoilerBase;
import tconstruct.library.crafting.ToolBuilder;
import tconstruct.tools.TinkerTools;

/**
 * Test-server-only checks, enabled with {@code -Dvaliertweaks.selftest=true}: {@code valiertweaks selftest trees}
 * builds trees and a log wall in an empty corner of the world, has fake players chop them with real Tinkers' lumber
 * axes, and checks the result 3 seconds (60 ticks) later:
 * <ul>
 * <li>A: an axe with 3 uses left: the whole tree falls (Tinkers' part plus the rest), every log drops, the axe broke.</li>
 * <li>B: a fresh axe: Tinkers fells it all, nothing is left for this mod.</li>
 * <li>C: a log wall without leaves: Tinkers' own 3x3 at most (it isn't a tree), and nothing more from this mod.</li>
 * <li>D: a sneaking player: only the clicked log goes.</li>
 * <li>E: an axe with 3 uses left and one log above that point protected (its break event cancelled, as a claim
 * would): the rest falls, that log stays.</li>
 * </ul>
 * Each check logs one {@code [ValierTweaks SelfTest] PASS/FAIL} line, then a SUMMARY line; the area is cleared after.
 */
final class SelfTest {

    static final boolean ENABLED = Boolean.getBoolean("valiertweaks.selftest");

    private static final int CX = 400, CZ = 200; // an empty corner of the test world
    private static final int HEIGHT = 10;

    private static int passed, failed;
    private static final List<String> failures = new ArrayList<>();

    private SelfTest() {}

    private static String stepName = "trees";

    static void run(ICommandSender sender, String step) {
        if (!"trees".equals(step) && !"peaceful".equals(step) && !"boilers".equals(step)) {
            sender.addChatMessage(new ChatComponentText("Steps: trees, peaceful, boilers"));
            return;
        }
        passed = failed = 0;
        failures.clear();
        stepName = step;
        try {
            if ("trees".equals(step)) new Trees().start();
            else if ("boilers".equals(step)) new Boilers().start();
            else new Peaceful().start();
        } catch (Throwable t) {
            ValierTweaks.LOG.error("[ValierTweaks SelfTest] " + step + " threw", t);
            check(false, step + " ran without an exception (" + t + ")");
            summary();
        }
    }

    /**
     * {@code valiertweaks selftest peaceful}: the flat test world's overworld stands in for a personal dimension (the
     * command itself refuses it, which is checked first). A zombie can't be spawned while it is peaceful, natural
     * spawning is denied, a cow is unaffected, and a zombie that was already there is gone 10 ticks later.
     */
    public static final class Peaceful {

        final WorldServer world = DimensionManager.getWorld(0);
        final int x = (CX << 4) + 52, z = (CZ << 4) + 8; // x .. x + 5 stay inside one chunk
        int y, ticks;
        EntityZombie earlier;
        EntityCow cow;
        final boolean wasPeaceful = PeacefulDims.isPeaceful(0);

        void start() {
            world.theChunkProviderServer.loadChunk((x >> 4), CZ);
            y = world.getHeightValue(x, z);

            // The command, as a player in the overworld (not a personal dimension) and as a non-op naming a dimension.
            final String[] said = new String[1];
            FakePlayer player = new FakePlayer(world, new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000d01"), "PdimTester")) {

                @Override
                public void addChatMessage(net.minecraft.util.IChatComponent message) {
                    said[0] = message.getUnformattedText();
                }
            };
            player.setPosition(x + 0.5, y, z + 0.5);
            check(!PeacefulDims.isPersonal(world), "the overworld isn't a personal dimension");
            new PdimCommand().processCommand(player, new String[] { "peaceful", "on" });
            check(said[0] != null && said[0].contains("only be set in a personal dimension") && !PeacefulDims.isPeaceful(0),
                "/pdim peaceful on is refused outside a personal dimension (" + said[0] + ")");
            new PdimCommand().processCommand(player, new String[] { "peaceful", "on", "0" });
            check(said[0] != null && said[0].startsWith("Only ops") && !PeacefulDims.isPeaceful(0),
                "a non-op can't name a dimension (" + said[0] + ")");

            // Who may switch it: anyone who could break blocks where they stand.
            check(PdimCommand.mayChange(player), "with nothing protecting the spot, the player may switch it");
            DenyBreaks guard = new DenyBreaks();
            MinecraftForge.EVENT_BUS.register(guard);
            boolean refused = !PdimCommand.mayChange(player);
            MinecraftForge.EVENT_BUS.unregister(guard);
            check(refused, "where a claim would stop them breaking blocks, the player may not switch it");

            // A hostile mob that is there before peaceful goes on.
            PeacefulDims.setPeaceful(0, false);
            earlier = new EntityZombie(world);
            earlier.setLocationAndAngles(x + 0.5, y, z + 0.5, 0, 0);
            check(world.spawnEntityInWorld(earlier), "a zombie spawns while peaceful is off");

            check(PeacefulDims.setPeaceful(0, true) && PeacefulDims.isPeaceful(world), "peaceful goes on (and is reported on)");
            EntityZombie later = new EntityZombie(world);
            later.setLocationAndAngles(x + 2.5, y, z + 0.5, 0, 0);
            check(!world.spawnEntityInWorld(later) && !world.loadedEntityList.contains(later),
                "while peaceful, a new zombie can't be spawned (egg, command, spawner)");
            LivingSpawnEvent.CheckSpawn ask = new LivingSpawnEvent.CheckSpawn(later, world, x + 2.5F, y, z + 0.5F);
            MinecraftForge.EVENT_BUS.post(ask);
            check(ask.getResult() == cpw.mods.fml.common.eventhandler.Event.Result.DENY, "natural spawning of a zombie is denied");
            cow = new EntityCow(world);
            cow.setLocationAndAngles(x + 4.5, y, z + 0.5, 0, 0);
            check(world.spawnEntityInWorld(cow), "a cow still spawns");
            LivingSpawnEvent.CheckSpawn askCow = new LivingSpawnEvent.CheckSpawn(cow, world, x + 4.5F, y, z + 0.5F);
            MinecraftForge.EVENT_BUS.post(askCow);
            check(askCow.getResult() != cpw.mods.fml.common.eventhandler.Event.Result.DENY, "natural spawning of a cow isn't denied");

            // Saved with the world.
            NBTTagCompound tag = new NBTTagCompound();
            PeacefulDims.Data.get().writeToNBT(tag);
            PeacefulDims.Data copy = new PeacefulDims.Data("copy");
            copy.readFromNBT(tag);
            check(copy.dims.contains(0) && PeacefulDims.Data.get().isDirty(), "the peaceful list saves and loads (and is marked to save)");

            FMLCommonHandler.instance().bus().register(this);
            log("waiting 10 ticks for the sweep");
        }

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ++ticks < 10) return;
            FMLCommonHandler.instance().bus().unregister(this);
            try {
                check(earlier.isDead, "the zombie that was already there is removed within 10 ticks");
                check(!cow.isDead, "the cow is left alone");
            } finally {
                cow.setDead();
                PeacefulDims.setPeaceful(0, wasPeaceful);
                check(PeacefulDims.isPeaceful(0) == wasPeaceful, "peaceful goes back off");
                summary();
            }
        }
    }

    /** Public: Forge's event bus calls its handlers from generated classes. */
    public static final class Trees {

        final WorldServer world = DimensionManager.getWorld(0);
        final int x0 = CX << 4, z = (CZ << 4) + 8;
        final int ax = x0 + 2, bx = x0 + 10, cx = x0 + 18, dx = x0 + 28, ex = x0 + 36;
        int y; // the first log's height
        final ChunkPos protectedLog = new ChunkPos(ex, 0, z);
        ItemStack axeA;
        int trees0, logs0, ticks;

        void start() {
            for (int c = CX - 1; c <= CX + 3; c++) world.theChunkProviderServer.loadChunk(c, CZ);
            y = world.getHeightValue(ax, z);
            protectedLog.y = y + 6; // above where the worn axe breaks

            clear();
            tree(ax);
            tree(bx);
            tree(dx);
            tree(ex);
            for (int i = 0; i < 5; i++) for (int j = 0; j < 4; j++) world.setBlock(cx + i, y + j, z, Blocks.log, 0, 3);
            check(TreeFinisher.isTree(world, ax, y, z), "Tinkers sees tree A as a tree");
            check(!TreeFinisher.isTree(world, cx + 2, y, z), "Tinkers doesn't see the log wall as a tree");

            trees0 = TreeFinisher.treesFinished;
            logs0 = TreeFinisher.logsFinished;
            MinecraftForge.EVENT_BUS.register(this);
            FMLCommonHandler.instance()
                .bus()
                .register(this);

            axeA = axe(3);
            check(axeA != null, "built a Tinkers' lumber axe with 3 uses left");
            if (axeA == null) {
                finish();
                return;
            }
            chop("A", ax, axeA, false);
            chop("B", bx, axe(-1), false);
            chop("C", cx + 2, axe(3), false);
            chop("D", dx, axe(-1), true);
            chop("E", ex, axe(3), false);
            log("chopping started; checking in 60 ticks");
        }

        /** A trunk, a branch near the top and a 5x5x3 crown of leaves that won't decay. */
        void tree(int x) {
            for (int i = 0; i < HEIGHT; i++) world.setBlock(x, y + i, z, Blocks.log, 0, 3);
            world.setBlock(x + 1, y + HEIGHT - 2, z, Blocks.log, 4, 3); // east-west branch
            int top = y + HEIGHT - 1;
            for (int i = -2; i <= 2; i++) for (int j = -1; j <= 1; j++) for (int k = -2; k <= 2; k++)
                if (world.isAirBlock(x + i, top + j, z + k)) world.setBlock(x + i, top + j, z + k, Blocks.leaves, 4, 3);
        }

        /** A fresh iron lumber axe, or one with this many uses left (-1: fresh). */
        ItemStack axe(int usesLeft) {
            ItemStack axe = ToolBuilder.instance.buildTool(
                new ItemStack(TinkerTools.broadAxeHead, 1, 2),
                new ItemStack(TinkerTools.toughRod, 1, 2),
                new ItemStack(TinkerTools.largePlate, 1, 2),
                new ItemStack(TinkerTools.toughBinding, 1, 2),
                "Test Axe");
            if (axe == null || usesLeft < 0) return axe;
            NBTTagCompound tool = axe.getTagCompound()
                .getCompoundTag("InfiTool");
            tool.setInteger("Damage", tool.getInteger("TotalDurability") - usesLeft);
            return axe;
        }

        /** What a player does: break the bottom log with the axe in hand (Minecraft's own harvest call). */
        void chop(String name, int x, ItemStack axe, boolean sneaking) {
            FakePlayer player = FakePlayerFactory
                .get(world, new GameProfile(UUID.nameUUIDFromBytes(("vtweaks-" + name).getBytes()), "[TreeTest" + name + "]"));
            if (player.playerNetServerHandler == null)
                new NetHandlerPlayServer(MinecraftServer.getServer(), new NetworkManager(false), player);
            player.theItemInWorldManager.setGameType(WorldSettings.GameType.SURVIVAL);
            player.setPosition(x + 0.5, y, z - 2.5);
            player.setSneaking(sneaking);
            player.inventory.currentItem = 0;
            player.inventory.mainInventory[0] = axe;
            player.theItemInWorldManager.tryHarvestBlock(x, y, z);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void protect(BlockEvent.BreakEvent event) {
            if (event.x == protectedLog.x && event.y == protectedLog.y && event.z == protectedLog.z) event.setCanceled(true);
        }

        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || ++ticks < 60) return;
            try {
                verify();
            } catch (Throwable t) {
                ValierTweaks.LOG.error("[ValierTweaks SelfTest] the checks threw", t);
                check(false, "checks ran without an exception (" + t + ")");
            }
            finish();
        }

        void verify() {
            int logsPerTree = HEIGHT + 1;
            check(logsLeft(ax) == 0, "A (axe with 3 uses left): no logs left standing (" + logsLeft(ax) + ")");
            check(broken(axeA), "A: the axe broke partway");
            check(drops(ax) == logsPerTree, "A: all " + logsPerTree + " logs dropped (" + drops(ax) + ")");
            check(
                TreeFinisher.treesFinished - trees0 >= 1 && TreeFinisher.logsFinished - logs0 >= 1,
                "this mod finished tree A (" + (TreeFinisher.logsFinished - logs0) + " log(s) so far in the test)");
            check(logsLeft(bx) == 0, "B (fresh axe): no logs left (" + logsLeft(bx) + ")");
            int wall = 0;
            for (int i = 0; i < 5; i++) for (int j = 0; j < 4; j++) if (isLog(cx + i, y + j)) wall++;
            check(
                wall >= 11 && !isLog(cx + 2, y),
                "C (log wall, no leaves): the clicked log went, at most Tinkers' own 3x3 with it (" + wall + " of 20 left)");
            check(logsLeft(dx) == logsPerTree - 1 && !isLog(dx, y), "D (sneaking): only the clicked log went (" + logsLeft(dx) + " left)");
            check(
                logsLeft(ex) == 1 && isLog(ex, protectedLog.y),
                "E (one protected log): only the protected log is left (" + logsLeft(ex) + " left)");
            check(
                TreeFinisher.treesFinished - trees0 == 2,
                "this mod finished exactly A and E, not B, C or D (" + (TreeFinisher.treesFinished - trees0) + ")");
        }

        int logsLeft(int x) {
            int n = 0;
            for (int i = 0; i < HEIGHT; i++) if (isLog(x, y + i)) n++;
            if (isLog(x + 1, y + HEIGHT - 2)) n++;
            return n;
        }

        boolean isLog(int x, int yy) {
            return world.getBlock(x, yy, z) == Blocks.log;
        }

        int drops(int x) {
            int n = 0;
            for (Object o : world.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(x - 3, y - 2, z - 3, x + 4, y + HEIGHT + 3, z + 4))) {
                ItemStack s = ((EntityItem) o).getEntityItem();
                if (s != null && s.getItem() == Item.getItemFromBlock(Blocks.log)) n += s.stackSize;
            }
            return n;
        }

        void finish() {
            MinecraftForge.EVENT_BUS.unregister(this);
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
            clear();
            summary();
        }

        /** Removes the test blocks and the dropped items. */
        void clear() {
            for (int x = x0 - 2; x < x0 + 45; x++) for (int yy = y; yy < y + HEIGHT + 3; yy++) for (int k = z - 3; k <= z + 3; k++) {
                Block b = world.getBlock(x, yy, k);
                if (b == Blocks.log || b == Blocks.leaves) world.setBlockToAir(x, yy, k);
            }
            for (Object o : world.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(x0 - 4, y - 3, z - 5, x0 + 48, y + HEIGHT + 6, z + 5)))
                ((EntityItem) o).setDead();
        }
    }

    /**
     * {@code valiertweaks selftest boilers}: places a small coal boiler, a high pressure coal boiler and an LV advanced
     * boiler as a player would, then burns coal in each through the boiler's own fuel step ({@code updateFuel}), 60
     * pieces each (a third of them would leave ash). With {@code noBoilerAsh} on, all 60 burn and heat the boiler and
     * the ash slot stays empty; coal blocks (always a full dust of ash otherwise) leave none either. Then with it off,
     * to show the test reaches the ash code: the same coal leaves ash, and a coal block a full dust of dark ash. It all
     * runs at once (no ticks); the boilers are removed after.
     */
    static final class Boilers {

        final WorldServer world = DimensionManager.getWorld(0);
        final int x0 = (CX << 4) + 2, z = (CZ << 4) + 14; // beside the tree test's row, in the same chunk
        final boolean setting = TweaksConfig.noBoilerAsh;
        final List<int[]> placed = new ArrayList<>();
        Method updateFuel;

        void start() {
            world.theChunkProviderServer.loadChunk(x0 >> 4, z >> 4);
            try {
                updateFuel = MTEBoiler.class.getDeclaredMethod("updateFuel", IGregTechTileEntity.class, long.class);
                updateFuel.setAccessible(true);
                IGregTechTileEntity small = place(ItemList.Machine_Bronze_Boiler.get(1), x0, "small coal boiler", MTEBoilerBronze.class);
                IGregTechTileEntity steel = place(ItemList.Machine_Steel_Boiler.get(1), x0 + 2, "high pressure coal boiler", MTEBoilerSteel.class);
                IGregTechTileEntity advanced = place(GregtechItemList.Boiler_Advanced_LV.get(1), x0 + 4, "LV advanced boiler", MTEAdvancedBoilerBase.class);
                boiler(small, "small coal boiler", true);
                boiler(steel, "high pressure coal boiler", true);
                boiler(advanced, "LV advanced boiler", false);
            } catch (Throwable t) {
                ValierTweaks.LOG.error("[ValierTweaks SelfTest] boilers threw", t);
                check(false, "the boiler checks ran without an exception (" + t + ")");
            } finally {
                TweaksConfig.noBoilerAsh = setting;
                check(TweaksConfig.noBoilerAsh == setting, "noBoilerAsh is back to its setting (" + setting + ")");
                boolean gone = true;
                for (int[] p : placed) {
                    TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
                    if (te instanceof IInventory) for (int i = 0; i < ((IInventory) te).getSizeInventory(); i++)
                        ((IInventory) te).setInventorySlotContents(i, null);
                    world.setBlockToAir(p[0], p[1], p[2]);
                    gone &= world.isAirBlock(p[0], p[1], p[2]);
                }
                for (Object o : world.getEntitiesWithinAABB(EntityItem.class, AxisAlignedBB.getBoundingBox(x0 - 3, 0, z - 3, x0 + 8, 256, z + 4)))
                    ((EntityItem) o).setDead();
                check(gone, "the test boilers are removed (" + placed.size() + ")");
                summary();
            }
        }

        /** Places the machine on the ground as a player would (GregTech's own item placing); null if that failed. */
        IGregTechTileEntity place(ItemStack stack, int x, String name, Class<?> type) {
            int y = world.getHeightValue(x, z);
            FakePlayer player = FakePlayerFactory
                .get(world, new GameProfile(UUID.nameUUIDFromBytes("vtweaks-boilers".getBytes()), "[BoilerTest]"));
            boolean ok = stack != null && stack.getItem() instanceof ItemBlock
                && ((ItemBlock) stack.getItem()).placeBlockAt(stack, player, world, x, y, z, 1, 0.5F, 1F, 0.5F, 0);
            if (ok) placed.add(new int[] { x, y, z });
            TileEntity te = world.getTileEntity(x, y, z);
            IGregTechTileEntity gt = te instanceof IGregTechTileEntity ? (IGregTechTileEntity) te : null;
            boolean right = ok && gt != null && type.isInstance(gt.getMetaTileEntity());
            check(right, "placed a " + name + " (" + (gt == null ? te : gt.getMetaTileEntity()) + ")");
            return right ? gt : null;
        }

        void boiler(IGregTechTileEntity te, String name, boolean coalBlocks) throws Exception {
            if (te == null) return;
            TweaksConfig.noBoilerAsh = true;
            Burn r = burn(te, new ItemStack(Items.coal, 64), 60);
            check(r.burned == 60 && r.heated == 60, name + ": burns 60 coal, each one heating it (" + r.burned + " burned, " + r.heated + " heated)");
            check(r.ash == null, name + ": no ash from 60 coal with noBoilerAsh on (" + r + ")");
            if (coalBlocks) {
                r = burn(te, new ItemStack(Blocks.coal_block, 4), 4);
                check(r.burned == 4 && r.heated == 4 && r.ash == null, name + ": 4 coal blocks burn and leave no ash (" + r + ")");
            }
            TweaksConfig.noBoilerAsh = false;
            r = burn(te, new ItemStack(Items.coal, 64), 60);
            check(r.burned == 60 && r.ash != null, name + ": with noBoilerAsh off, 60 coal leave ash, as GregTech has it (" + r + ")");
            if (coalBlocks) {
                r = burn(te, new ItemStack(Blocks.coal_block, 1), 1);
                ItemStack dust = GTOreDictUnificator.get(OrePrefixes.dust, Materials.AshDark, 1L);
                check(r.ash != null && dust != null && r.ash.isItemEqual(dust) && r.ash.stackSize == 1,
                    name + ": with noBoilerAsh off, a coal block leaves a dust of dark ash (" + r + ")");
            }
        }

        /** Burns {@code times} pieces of {@code fuel} through the boiler's own fuel step, from an empty ash slot. */
        Burn burn(IGregTechTileEntity te, ItemStack fuel, int times) throws Exception {
            MTEBoiler b = (MTEBoiler) te.getMetaTileEntity();
            te.setInventorySlotContents(2, fuel.copy());
            te.setInventorySlotContents(3, null);
            Burn r = new Burn();
            for (int i = 0; i < times; i++) {
                b.mTemperature = 20; // an advanced boiler only takes fuel while cool
                b.mProcessingEnergy = 0;
                updateFuel.invoke(b, te, (long) i);
                if (b.mProcessingEnergy > 0) r.heated++;
            }
            ItemStack left = te.getStackInSlot(2);
            r.burned = fuel.stackSize - (left == null ? 0 : left.stackSize);
            r.ash = te.getStackInSlot(3);
            te.setInventorySlotContents(2, null);
            te.setInventorySlotContents(3, null);
            b.mProcessingEnergy = 0;
            return r;
        }

        static final class Burn {

            int burned, heated;
            ItemStack ash;

            @Override
            public String toString() {
                return burned + " burned, ash " + (ash == null ? "none" : ash.stackSize + " x " + ash.getDisplayName());
            }
        }
    }

    /** Stands in for a claim: refuses every block break while registered. Public for Forge's event bus. */
    public static final class DenyBreaks {

        @SubscribeEvent
        public void protect(BlockEvent.BreakEvent event) {
            event.setCanceled(true);
        }
    }

    /** A mutable block position. */
    private static final class ChunkPos {

        final int x, z;
        int y;

        ChunkPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static boolean broken(ItemStack stack) {
        return stack != null && stack.hasTagCompound()
            && stack.getTagCompound()
                .getCompoundTag("InfiTool")
                .getBoolean("Broken");
    }

    private static void summary() {
        String s = "SUMMARY " + stepName + ": " + passed + " passed, " + failed + " failed" + (failures.isEmpty() ? "" : " " + failures);
        log(s);
    }

    private static void check(boolean ok, String what) {
        if (ok) passed++;
        else {
            failed++;
            failures.add(what);
        }
        log((ok ? "PASS " : "FAIL ") + what);
    }

    private static void log(String text) {
        ValierTweaks.LOG.info("[ValierTweaks SelfTest] " + text);
    }
}
