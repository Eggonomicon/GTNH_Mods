package com.valier.tweaks.pipe;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Pipe Configurator: copies one pipe face's settings and pastes them onto others, as EnderIO's conduit probe does.
 * <ul>
 * <li>Sneak-right-click a pipe's arm where it touches a block: copy that face (mode, amount, interval, priority,
 * redstone, tag, overflow, spread and all three lists; never what it holds).</li>
 * <li>Right-click another face on the same kind of pipe: paste (claims are respected; amount and interval still keep to
 * the tier's limits when used).</li>
 * <li>A creative pipe's centre copies and pastes its source settings (only onto another creative pipe's centre).</li>
 * <li>Sneak-right-click the air: forget what it holds.</li>
 * </ul>
 * Works on the server only ({@link #onItemUseFirst} returns false on the player's computer, so the click reaches it).
 */
public class ItemPipeConfigurator extends Item {

    static final String TAG = "ValierPipeSettings";

    public ItemPipeConfigurator() {
        setMaxStackSize(1);
        setUnlocalizedName(ValierTweaks.MODID + ".pipe_configurator");
        setTextureName(ValierTweaks.MODID + ":pipe_configurator");
    }

    @Override
    public boolean onItemUseFirst(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx,
        float hy, float hz) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TilePipe)) return false;
        if (world.isRemote) return false; // the server decides; returning true here would keep the click from it
        TilePipe p = (TilePipe) te;
        int arm = BlockPipe.armLookedAt(world, x, y, z, player);
        int face = arm >= 0 && arm <= 5 ? arm : arm == 6 && p.creative() ? TilePipe.SOURCE : -1;
        FaceConfig cfg = face < 0 ? null : p.face(face);
        if (cfg == null) {
            PipeClicks.tell(player, EnumChatFormatting.GRAY, "Click a pipe where it touches a block"
                + (p.creative() ? ", or a creative pipe's centre." : "."));
            return true;
        }
        use(stack, player, p, face, player.isSneaking());
        return true;
    }

    /** Copies (sneaking) or pastes one face's settings; face 6: a creative pipe's source. Also for the self-test. */
    public static void use(ItemStack stack, EntityPlayer player, TilePipe p, int face, boolean copy) {
        FaceConfig cfg = p.face(face);
        if (cfg == null) return;
        if (copy) copy(stack, player, p, face, cfg);
        else paste(stack, player, p, face, cfg);
    }

    private static void copy(ItemStack stack, EntityPlayer player, TilePipe p, int face, FaceConfig cfg) {
        NBTTagCompound t = new NBTTagCompound();
        t.setTag("Settings", cfg.settings());
        t.setString("Kind", p.kind().word);
        t.setBoolean("Source", face == TilePipe.SOURCE);
        t.setString("From", PipeTier.NAMES[p.tier()] + " " + p.kind().word + " pipe, " + (face == TilePipe.SOURCE ? "source"
            : PipeClicks.words(face) + " face"));
        if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
        stack.stackTagCompound.setTag(TAG, t);
        player.inventoryContainer.detectAndSendChanges();
        PipeClicks.tell(player, EnumChatFormatting.GREEN, "Copied: " + summary(cfg) + ". Right-click another face to paste.");
    }

    private static void paste(ItemStack stack, EntityPlayer player, TilePipe p, int face, FaceConfig cfg) {
        NBTTagCompound t = stack.stackTagCompound == null ? null : stack.stackTagCompound.getCompoundTag(TAG);
        if (t == null || !t.hasKey("Settings", 10)) {
            PipeClicks.tell(player, EnumChatFormatting.GRAY, "Nothing copied yet: sneak-right-click a pipe face to copy it.");
            return;
        }
        if (!p.kind().word.equals(t.getString("Kind"))) {
            PipeClicks.tell(player, EnumChatFormatting.RED, "Those are " + t.getString("Kind") + " pipe settings; this is a "
                + p.kind().word + " pipe.");
            return;
        }
        if (t.getBoolean("Source") != (face == TilePipe.SOURCE)) {
            PipeClicks.tell(player, EnumChatFormatting.RED, t.getBoolean("Source")
                ? "Those are a creative pipe's source settings: paste them onto a creative pipe's centre."
                : "A creative pipe's centre only takes another creative pipe's source settings.");
            return;
        }
        if (!PipeClicks.may(p, player)) return;
        cfg.applySettings(t.getCompoundTag("Settings"));
        p.faceChanged();
        PipeClicks.tell(player, EnumChatFormatting.GREEN, "Pasted: " + summary(cfg) + ".");
    }

    static String summary(FaceConfig cfg) {
        StringBuilder sb = new StringBuilder(cfg.mode.words);
        for (int i = 0; i < cfg.lists.length; i++) {
            int n = cfg.lists[i].entries();
            if (n > 0 || cfg.lists[i].kind != FaceConfig.Kind.ITEMS) sb.append(", ")
                .append(FaceConfig.LIST_NAMES[i])
                .append(' ')
                .append(cfg.lists[i].kind == FaceConfig.Kind.ITEMS ? String.valueOf(n) : cfg.lists[i].kind.words.toLowerCase());
        }
        if (!cfg.tag.isEmpty()) sb.append(", tag ")
            .append(cfg.tag);
        if (cfg.priority != 0) sb.append(", priority ")
            .append(cfg.priority);
        if (cfg.mode.pulls()) sb.append(", ")
            .append(cfg.spread.words.toLowerCase());
        return sb.toString();
    }

    /** Sneak-right-click the air: it forgets what it holds. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking() && stack.stackTagCompound != null && stack.stackTagCompound.hasKey(TAG)) {
            stack.stackTagCompound.removeTag(TAG);
            if (stack.stackTagCompound.hasNoTags()) stack.stackTagCompound = null;
            player.inventoryContainer.detectAndSendChanges();
            PipeClicks.tell(player, EnumChatFormatting.GRAY, "The Pipe Configurator holds nothing now.");
        }
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        String grey = EnumChatFormatting.GRAY.toString();
        NBTTagCompound t = stack.stackTagCompound == null ? null : stack.stackTagCompound.getCompoundTag(TAG);
        if (t != null && t.hasKey("Settings", 10)) {
            lines.add(EnumChatFormatting.GREEN + "Holds: " + t.getString("From"));
            lines.add(grey + summary(FaceConfig.read(t.getCompoundTag("Settings"))));
        } else lines.add(grey + "Holds nothing");
        lines.add(grey + "Sneak-right-click a pipe face: copy");
        lines.add(grey + "Right-click another face: paste");
        lines.add(grey + "Sneak-right-click the air: clear");
    }

    @Override
    public boolean hasEffect(ItemStack stack, int pass) {
        return stack.stackTagCompound != null && stack.stackTagCompound.hasKey(TAG);
    }
}
