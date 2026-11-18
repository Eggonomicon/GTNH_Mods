package com.valier.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.SpecialChars;

/**
 * WAILA for Valier XP Crafters. A crafter is a plain GregTech tank to the client; the server's Valier Tweaks adds a
 * {@value #TAG} compound to the block data WAILA fetches for a player looking at one. When it is there, the hover name
 * becomes "Valier XP Crafter", lines saying what it makes, the liquid XP each takes and what it is doing go first in
 * the body, and the mod name at the bottom says Valier Tweaks instead of GregTech. Without it (any other block, or a
 * server without Valier Tweaks) nothing changes.
 * <p>
 * WAILA option "Valier XP crafters" (in WAILA's settings, under Valier) turns it off.
 */
public final class XpCrafterHud implements IWailaDataProvider {

    /** The compound the server adds (Valier Tweaks' {@code XpCrafterWaila.TAG}). */
    static final String TAG = "ValierXpCrafter";
    static final String OPTION = "valierclient.xpcrafter";
    /** GregTech's machine tile entity: every GregTech tank is one. */
    static final String GT_TILE = "gregtech.api.metatileentity.BaseMetaTileEntity";

    /** Called by WAILA (IMC {@code register}). Nothing to do without GregTech: XP crafters are GregTech tanks. */
    public static void callbackRegister(IWailaRegistrar registrar) {
        Class<?> tiles;
        try {
            tiles = Class.forName(GT_TILE);
        } catch (ClassNotFoundException e) {
            return;
        }
        registrar.addConfig("Valier", OPTION, "option.valierclient.xpcrafter", true);
        XpCrafterHud hud = new XpCrafterHud();
        registrar.registerHeadProvider(hud, tiles);
        registrar.registerBodyProvider(hud, tiles);
        registrar.registerTailProvider(hud, tiles);
    }

    /** What the server says about the crafter being looked at, or null (not one, or switched off). */
    static NBTTagCompound info(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        if (accessor == null || (config != null && !config.getConfig(OPTION, true))) return null;
        NBTTagCompound nbt = accessor.getNBTData();
        return nbt != null && nbt.hasKey(TAG, 10) ? nbt.getCompoundTag(TAG) : null;
    }

    /** The name line becomes "Valier XP Crafter", in the colour WAILA gave the tank's name. */
    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        if (tip == null || info(accessor, config) == null) return tip;
        String name = tr("valierclient.xpcrafter.name");
        if (tip.isEmpty()) tip.add(EnumChatFormatting.WHITE + name);
        else tip.set(0, leadingFormat(tip.get(0)) + name);
        return tip;
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        NBTTagCompound t = tip == null ? null : info(accessor, config);
        if (t != null) tip.addAll(0, lines(t));
        return tip;
    }

    /** The body lines for what the server sent. */
    static List<String> lines(NBTTagCompound t) {
        String grey = EnumChatFormatting.GRAY.toString();
        List<String> out = new ArrayList<>();
        if (t.hasKey("Makes", 10)) {
            out.add(grey + tr("valierclient.xpcrafter.makes", EnumChatFormatting.WHITE + makesName(t) + grey));
            out.add(grey + tr(t.getBoolean("Whole") ? "valierclient.xpcrafter.costWhole" : "valierclient.xpcrafter.cost",
                number(t.getInteger("Cost"))));
        } else {
            out.add(grey + tr("valierclient.xpcrafter.nothing"));
            out.add(grey + tr("valierclient.xpcrafter.choose"));
        }
        if (t.getBoolean("Full") && !t.getBoolean("Whole")) out.add(grey + tr("valierclient.xpcrafter.fullOn"));
        String status = t.getString("Status");
        if (!status.isEmpty() && !"NO_RECIPE".equals(status)) out.add(colour(status) + statusText(status, t));
        if (t.getInteger("Tank") > 0) out.add(grey + tr("valierclient.xpcrafter.tank", number(t.getInteger("Tank")),
            fluidName(t.getString("TankFluid"))));
        if (t.getInteger("Bank") > 0) out.add(grey + tr("valierclient.xpcrafter.bank", number(t.getInteger("Bank"))));
        if (t.getLong("Made") > 0) out.add(grey + tr("valierclient.xpcrafter.made", number(t.getLong("Made"))));
        if (t.getInteger("Stored") > 0) out.add(grey + tr("valierclient.xpcrafter.stored", number(t.getInteger("Stored"))));
        return out;
    }

    /** "2 × Gallium Dust" in the player's language; the server's English name if the item isn't known here. */
    static String makesName(NBTTagCompound t) {
        try {
            ItemStack s = ItemStack.loadItemStackFromNBT(t.getCompoundTag("Makes"));
            if (s != null && s.getItem() != null) return (s.stackSize > 1 ? s.stackSize + " × " : "") + s.getDisplayName();
        } catch (RuntimeException e) {
            // an item this client draws differently: the server's name will do
        }
        return t.getString("MakesName");
    }

    static String statusText(String status, NBTTagCompound t) {
        String key = "valierclient.xpcrafter.status." + status;
        if (StatCollector.canTranslate(key)) return StatCollector.translateToLocal(key);
        String words = t.getString("StatusText"); // a status newer than this mod: the server's English words
        return words.isEmpty() ? status : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    static EnumChatFormatting colour(String status) {
        switch (status) {
            case "WORKING":
                return EnumChatFormatting.GREEN;
            case "NEEDS_XP":
            case "FULL":
                return EnumChatFormatting.YELLOW;
            case "NO_OUTPUT":
            case "WRONG_FLUID":
            case "OFF":
                return EnumChatFormatting.RED;
            default:
                return EnumChatFormatting.GRAY;
        }
    }

    static String fluidName(String name) {
        Fluid f = name == null || name.isEmpty() ? null : FluidRegistry.getFluid(name);
        return f == null ? name : new FluidStack(f, 1).getLocalizedName();
    }

    /** The formatting codes a line starts with (WAILA's colour for names). */
    static String leadingFormat(String s) {
        int i = 0;
        while (i + 1 < s.length() && s.charAt(i) == '§') i += 2;
        return s.substring(0, i);
    }

    static String number(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    static String tr(String key, Object... args) {
        return args.length == 0 ? StatCollector.translateToLocal(key) : StatCollector.translateToLocalFormatted(key, args);
    }

    // ---- not used: the server sends the data, the stack is WAILA's own -----------------------------------------------

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, int x, int y,
        int z) {
        return tag;
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    /** The mod name line WAILA adds at the bottom (blue, italic: "GregTech") says Valier Tweaks, if it is shown. */
    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        if (tip == null || info(accessor, config) == null) return tip;
        String mod = SpecialChars.BLUE + SpecialChars.ITALIC;
        for (int i = tip.size() - 1; i >= 0; i--) {
            if (tip.get(i) != null && tip.get(i)
                .startsWith(mod)) {
                tip.set(i, mod + tr("valierclient.xpcrafter.mod"));
                break;
            }
        }
        return tip;
    }
}
