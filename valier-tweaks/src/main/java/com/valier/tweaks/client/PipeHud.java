package com.valier.tweaks.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.valier.tweaks.pipe.FaceConfig;
import com.valier.tweaks.pipe.FaceMode;
import com.valier.tweaks.pipe.PipeWaila;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * WAILA lines for Valier pipes, from what the server sends ({@code pipe.PipeInfo}):
 * <ul>
 * <li>looking at an arm where it touches a block: that face's mode, tag, priority, spread, list sizes, what it moved and
 * voided per minute, the last thing it moved, and what it holds back;</li>
 * <li>looking at the centre: the network (pipes, working and sleeping faces), what is flowing through it (the things
 * its working faces moved most over the last minute), what it voided, and a creative pipe's source.</li>
 * </ul>
 * WAILA option "Valier pipes" turns it off. WAILA runs this on both sides, so it uses no client-only classes.
 */
public final class PipeHud implements IWailaDataProvider {

    static final String OPTION = "valiertweaks.pipes";
    static final String[] SIDES = { "Below", "Above", "North", "South", "West", "East", "Source" };

    public static void callbackRegister(IWailaRegistrar registrar) {
        registrar.addConfig("Valier", OPTION, "option.valiertweaks.pipes", true);
        registrar.registerBodyProvider(new PipeHud(), com.valier.tweaks.pipe.TilePipe.class);
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        if (tip == null || accessor == null || (config != null && !config.getConfig(OPTION, true))) return tip;
        NBTTagCompound nbt = accessor.getNBTData();
        if (nbt == null || !nbt.hasKey(PipeWaila.TAG, 10)) return tip;
        MovingObjectPosition mop = accessor.getPosition();
        int part = mop == null ? -1 : mop.subHit;
        tip.addAll(lines(nbt.getCompoundTag(PipeWaila.TAG), part));
        return tip;
    }

    /** The lines for what the server sent; part: the arm looked at (0-5), 6 the centre, -1 not known. */
    public static List<String> lines(NBTTagCompound t, int part) {
        List<String> out = new ArrayList<>();
        boolean fluid = t.getBoolean("Fluid");
        NBTTagCompound face = part >= 0 && part <= 5 ? faceOn(t, part) : null;
        if (face != null) {
            face(out, face, fluid, "");
            NBTTagCompound n = t.getCompoundTag("Net");
            if (t.hasKey("Net", 10)) out.add(EnumChatFormatting.DARK_GRAY + "Network: " + n.getInteger("Pipes") + " pipes");
            return out;
        }
        if (t.hasKey("Net", 10)) {
            NBTTagCompound n = t.getCompoundTag("Net");
            String grey = EnumChatFormatting.GRAY.toString();
            if (n.getBoolean("TooBig")) out.add(EnumChatFormatting.RED + "Network too big: it does nothing");
            else out.add(grey + "Network: " + n.getInteger("Pipes") + " pipes, " + n.getInteger("Working") + " working"
                + (n.getInteger("Sleeping") > 0 ? " (" + n.getInteger("Sleeping") + " waiting)" : ""));
            NBTTagList flow = n.getTagList("Flow", 10);
            if (flow.tagCount() == 0) out.add(grey + "Flowing: nothing in the last minute");
            else {
                out.add(grey + "Flowing (last minute):");
                for (int i = 0; i < flow.tagCount(); i++) {
                    NBTTagCompound c = flow.getCompoundTagAt(i);
                    out.add(grey + "  " + EnumChatFormatting.WHITE + name(c) + grey + "  " + perMinute(c.getLong("PerMin"), c.hasKey("Fluid")));
                }
            }
            if (n.getLong("Voided") > 0) out.add(EnumChatFormatting.LIGHT_PURPLE + "Voided: " + perMinute(n.getLong("Voided"), fluid));
        }
        NBTTagCompound source = faceOn(t, 6);
        if (source != null) face(out, source, fluid, "Source: ");
        return out;
    }

    private static NBTTagCompound faceOn(NBTTagCompound t, int side) {
        NBTTagList faces = t.getTagList("Faces", 10);
        for (int i = 0; i < faces.tagCount(); i++) if (faces.getCompoundTagAt(i)
            .getByte("Side") == side) return faces.getCompoundTagAt(i);
        return null;
    }

    private static void face(List<String> out, NBTTagCompound c, boolean fluid, String prefix) {
        String grey = EnumChatFormatting.GRAY.toString();
        FaceMode mode = FaceMode.of(c.getByte("Mode"));
        StringBuilder head = new StringBuilder(grey + prefix + EnumChatFormatting.WHITE + mode.words);
        if (c.hasKey("Tag")) head.append(grey)
            .append(", tag ")
            .append(EnumChatFormatting.WHITE)
            .append(c.getString("Tag"));
        if (c.getInteger("Priority") != 0) head.append(grey)
            .append(", priority ")
            .append(c.getInteger("Priority"));
        out.add(head.toString());
        if (mode == FaceMode.OFF) return;
        if (mode.pulls()) {
            FaceConfig.Spread[] spreads = FaceConfig.Spread.values();
            out.add(grey + "Spread: " + spreads[Math.max(0, Math.min(spreads.length - 1, c.getByte("Spread")))].words);
        }
        int[] lists = c.getIntArray("Lists");
        if (lists.length == 3) {
            StringBuilder l = new StringBuilder(grey + "Lists:");
            for (int i = 0; i < 3; i++) l.append(i == 0 ? " " : ", ")
                .append(FaceConfig.LIST_NAMES[i])
                .append(' ')
                .append(lists[i] < 0 ? "existing" : String.valueOf(lists[i]));
            out.add(l.toString());
        }
        long moved = c.getLong("Moved"), voided = c.getLong("Voided");
        out.add(grey + "Moved: " + perMinute(moved, fluid) + (voided > 0
            ? EnumChatFormatting.LIGHT_PURPLE + ", voided " + perMinute(voided, fluid) : ""));
        if (c.hasKey("Last", 10)) {
            NBTTagCompound last = c.getCompoundTag("Last");
            long ago = last.getLong("Ago");
            out.add(grey + (last.getBoolean("Void") ? "Last voided: " : "Last: ") + EnumChatFormatting.WHITE
                + amount(last.getInteger("Amount"), last.hasKey("Fluid")) + " " + name(last) + grey
                + (ago <= 1 ? ", just now" : ", " + ago + " s ago"));
        }
        if (c.getInteger("HeldItems") > 0) out.add(EnumChatFormatting.YELLOW + "Holding back " + c.getInteger("HeldItems") + " items");
        if (c.getInteger("HeldFluid") > 0) out.add(EnumChatFormatting.YELLOW + "Holding back " + number(c.getInteger("HeldFluid")) + " mB");
    }

    /** An item's or a fluid's name, as this game names it. */
    static String name(NBTTagCompound c) {
        if (c.hasKey("Item", 10)) {
            ItemStack s = ItemStack.loadItemStackFromNBT(c.getCompoundTag("Item"));
            if (s != null) try {
                return s.getDisplayName();
            } catch (Throwable e) {
                return "?";
            }
        }
        if (c.hasKey("Fluid")) {
            Fluid f = FluidRegistry.getFluid(c.getString("Fluid"));
            return f == null ? c.getString("Fluid") : f.getLocalizedName(new FluidStack(f, 1000));
        }
        return "?";
    }

    static String amount(long n, boolean fluid) {
        return fluid ? number(n) + " mB" : number(n) + " ×";
    }

    static String perMinute(long n, boolean fluid) {
        return number(n) + (fluid ? " mB" : "") + "/min";
    }

    static String number(long n) {
        return String.format("%,d", n);
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return tip;
    }

    @Override
    public NBTTagCompound getNBTData(net.minecraft.entity.player.EntityPlayerMP player, net.minecraft.tileentity.TileEntity te,
        NBTTagCompound tag, net.minecraft.world.World world, int x, int y, int z) {
        return tag;
    }
}
