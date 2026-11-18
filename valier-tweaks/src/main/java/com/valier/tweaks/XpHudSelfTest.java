package com.valier.tweaks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import cpw.mods.fml.common.Loader;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.impl.ModuleRegistrar;

/**
 * The WAILA part of {@code valiertweaks selftest xpcrafter}, used only when WAILA is loaded. It asks every WAILA data
 * provider registered for the tank, as WAILA's server does for a player looking at it, and checks the
 * {@value XpCrafterWaila#TAG} compound. With the Valier Client mod loaded on the test server too (its providers are
 * registered on both sides), it also runs that mod's name and line providers on the result, as WAILA does on a
 * player's computer, and checks what a player would read.
 */
final class XpHudSelfTest {

    static final String CLIENT = "com.valier.client.";
    static final String OPTION = "valierclient.xpcrafter";
    static final List<String> HEAD = Collections.singletonList("§fUltra Low Voltage Fluid Tank");
    static final List<String> BODY = Collections.singletonList("Facing: North");

    final WorldServer world;
    final EntityPlayerMP player;
    final boolean client = Loader.isModLoaded("valierclient");

    XpHudSelfTest(WorldServer world, EntityPlayerMP player) {
        this.world = world;
        this.player = player;
        if (!client) ValierTweaks.LOG.info("[ValierTweaks SelfTest] Valier Client isn't loaded: what players read isn't checked");
    }

    /** Just chosen, before any liquid XP: what it makes and the cost go along with WAILA's data. */
    void chosen(int[] p, ItemStack tiny) {
        NBTTagCompound t = info(p);
        SelfTest.check(registered(p) && t != null && t.getInteger("Format") == 1 && makes(t, tiny)
            && t.getInteger("Cost") == 8000 && "NO_RECIPE".equals(t.getString("Status")) && !t.hasKey("Tank"),
            "WAILA: the data WAILA's server sends for it says what it makes and the cost, as soon as it is chosen ("
                + t + ")");
    }

    /** Working, with 4,000 mB taken and three made: the data, and what Valier Client shows. */
    void working(int[] p, ItemStack tiny) {
        NBTTagCompound data = data(p);
        NBTTagCompound t = data.getCompoundTag(XpCrafterWaila.TAG);
        SelfTest.check(makes(t, tiny) && "WORKING".equals(t.getString("Status")) && t.getInteger("Bank") == 4000
            && t.getLong("Made") == 3 && !t.hasKey("Tank"), "WAILA: working, holding 4,000 mB, 3 made (" + t + ")");
        if (!client) return;
        List<String> head = run(true, p, data, HEAD, true), body = run(false, p, data, BODY, true);
        List<String> want = Arrays.asList("Makes: " + tiny.getDisplayName(), "8,000 mB of liquid XP each", "Working",
            "Holding 4,000 mB towards the next", "Made: 3", "Facing: North");
        SelfTest.check(head != null && head.equals(Collections.singletonList("§fValier XP Crafter")) && body != null
            && plain(body).equals(want) && body.get(2)
                .startsWith("§a"),
            "Valier Client: WAILA names it Valier XP Crafter and says what it makes, the cost, Working (green), the XP it "
                + "holds and how many it made, before the tank's own lines (" + head + " / " + body + ")");
        List<String> off = run(true, p, data, HEAD, false);
        SelfTest.check(HEAD.equals(off), "Valier Client: with its WAILA option off, the name stays the tank's (" + off + ")");
    }

    /** Full dust just switched on. */
    void full(int[] p, ItemStack dust) {
        NBTTagCompound data = data(p);
        NBTTagCompound t = data.getCompoundTag(XpCrafterWaila.TAG);
        List<String> body = client ? run(false, p, data, BODY, true) : null;
        SelfTest.check(makes(t, dust) && t.getInteger("Cost") == 72000 && t.getBoolean("Whole") && t.getBoolean("Full")
            && (!client || body != null && plain(body).subList(0, 2)
                .equals(Arrays.asList("Makes: " + dust.getDisplayName(), "72,000 mB of liquid XP each (full dust)"))),
            "WAILA: with full dust on at once it says gallium dust, 72,000 mB each (full dust) (" + t + " / " + body + ")");
    }

    /** Its store full, nothing touching it, 8,000 mB in its tank. */
    void noOutput(int[] p, Fluid xp) {
        NBTTagCompound data = data(p);
        NBTTagCompound t = data.getCompoundTag(XpCrafterWaila.TAG);
        List<String> body = client ? run(false, p, data, BODY, true) : null;
        String xpName = new FluidStack(xp, 1).getLocalizedName();
        SelfTest.check("NO_OUTPUT".equals(t.getString("Status")) && t.getInteger("Tank") == 8000
            && xp.getName()
                .equals(t.getString("TankFluid"))
            && t.getInteger("Stored") == 576
            && (!client || body != null && body.contains("§cWaiting: its store is full, and nothing touching it takes items")
                && plain(body).contains("Tank: 8,000 mB of " + xpName) && plain(body).contains("In its store: 576")),
            "WAILA: with its store full and nothing touching it, it says why (red), what is in its tank and its store (" + t
                + " / " + body + ")");
    }

    void forgotten(int[] p) {
        SelfTest.check(XpCrafters.hudAt(world.provider.dimensionId, p[0], p[1], p[2]) == null,
            "WAILA: once it is forgotten, WAILA is told nothing more about it");
    }

    /** A plain tank: nothing added, nothing changed. */
    void plainTank(int[] q) {
        NBTTagCompound data = data(q);
        List<String> head = client ? run(true, q, data, HEAD, true) : HEAD,
            body = client ? run(false, q, data, BODY, true) : BODY;
        SelfTest.check(!data.hasKey(XpCrafterWaila.TAG) && HEAD.equals(head) && BODY.equals(body),
            "WAILA: a plain ULV tank gets nothing extra, and Valier Client changes nothing (" + head + " / " + body + ")");
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    static boolean makes(NBTTagCompound t, ItemStack what) {
        if (t == null || !t.hasKey("Makes", 10)) return false;
        ItemStack s = ItemStack.loadItemStackFromNBT(t.getCompoundTag("Makes"));
        return s != null && s.isItemEqual(what) && s.stackSize == what.stackSize;
    }

    NBTTagCompound info(int[] p) {
        NBTTagCompound data = data(p);
        return data.hasKey(XpCrafterWaila.TAG, 10) ? data.getCompoundTag(XpCrafterWaila.TAG) : null;
    }

    boolean registered(int[] p) {
        TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
        for (List<IWailaDataProvider> l : ModuleRegistrar.instance()
            .getNBTProviders(te)
            .values()) for (IWailaDataProvider pr : l) if (pr instanceof XpCrafterWaila) return true;
        return false;
    }

    /** The block data WAILA's server would send a player looking at p: every provider for its tile entity. */
    NBTTagCompound data(int[] p) {
        TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
        NBTTagCompound tag = new NBTTagCompound();
        if (te == null) return tag;
        for (List<IWailaDataProvider> l : ModuleRegistrar.instance()
            .getNBTProviders(te)
            .values()) for (IWailaDataProvider pr : l) tag = pr.getNBTData(player, te, tag, world, p[0], p[1], p[2]);
        return tag;
    }

    /** What Valier Client's name (head) or line (body) providers make of WAILA's lines; null if it registered none. */
    List<String> run(boolean head, int[] p, NBTTagCompound data, List<String> start, boolean option) {
        TileEntity te = world.getTileEntity(p[0], p[1], p[2]);
        Map<Integer, List<IWailaDataProvider>> all = head ? ModuleRegistrar.instance()
            .getHeadProviders(te)
            : ModuleRegistrar.instance()
                .getBodyProviders(te);
        IWailaDataAccessor accessor = accessor(te, data);
        IWailaConfigHandler config = config(option);
        List<String> tip = new ArrayList<>(start);
        int found = 0;
        for (List<IWailaDataProvider> l : all.values()) {
            for (IWailaDataProvider pr : l) {
                if (!pr.getClass()
                    .getName()
                    .startsWith(CLIENT)) continue;
                found++;
                tip = head ? pr.getWailaHead(null, tip, accessor, config) : pr.getWailaBody(null, tip, accessor, config);
            }
        }
        return found == 0 ? null : tip;
    }

    static List<String> plain(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String s : lines) out.add(s.replaceAll("§.", ""));
        return out;
    }

    IWailaDataAccessor accessor(TileEntity te, NBTTagCompound data) {
        return new IWailaDataAccessor() {

            @Override
            public World getWorld() {
                return world;
            }

            @Override
            public EntityPlayer getPlayer() {
                return player;
            }

            @Override
            public Block getBlock() {
                return te == null ? null : te.getBlockType();
            }

            @Override
            public int getBlockID() {
                return 0;
            }

            @Override
            public String getBlockQualifiedName() {
                return "";
            }

            @Override
            public int getMetadata() {
                return 0;
            }

            @Override
            public TileEntity getTileEntity() {
                return te;
            }

            @Override
            public MovingObjectPosition getPosition() {
                return null;
            }

            @Override
            public Vec3 getRenderingPosition() {
                return null;
            }

            @Override
            public NBTTagCompound getNBTData() {
                return data;
            }

            @Override
            public int getNBTInteger(NBTTagCompound tag, String key) {
                return tag.getInteger(key);
            }

            @Override
            public double getPartialFrame() {
                return 0;
            }

            @Override
            public ForgeDirection getSide() {
                return ForgeDirection.UNKNOWN;
            }

            @Override
            public ItemStack getStack() {
                return null;
            }
        };
    }

    static IWailaConfigHandler config(boolean option) {
        return new IWailaConfigHandler() {

            @Override
            public Set<String> getModuleNames() {
                return Collections.emptySet();
            }

            @Override
            public HashMap<String, String> getConfigKeys(String module) {
                return new HashMap<>();
            }

            @Override
            public boolean getConfig(String key, boolean def) {
                return OPTION.equals(key) ? option : def;
            }

            @Override
            public boolean getConfig(String key) {
                return getConfig(key, true);
            }
        };
    }
}
