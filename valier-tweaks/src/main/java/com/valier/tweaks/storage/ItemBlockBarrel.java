package com.valier.tweaks.storage;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

/**
 * A barrel as an item (metadata: its tier). Picked up with a wrench or broken, it holds what the barrel held and the
 * channel it was on ({@value BarrelClicks#ITEM_TAG}); placed, it holds that again and joins that channel again.
 */
public class ItemBlockBarrel extends ItemBlock {

    public ItemBlockBarrel(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return BarrelTier.clamp(damage);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + BarrelTier.clamp(stack.getItemDamage());
    }

    /** One per stack when it holds something (so contents never merge). */
    @Override
    public int getItemStackLimit(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound()
            .hasKey(BarrelClicks.ITEM_TAG) ? 1 : 64;
    }

    static NBTTagCompound tag(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound()
            .getCompoundTag(BarrelClicks.ITEM_TAG) : null;
    }

    @Override
    public boolean placeBlockAt(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side, float hx, float hy,
        float hz, int meta) {
        if (!super.placeBlockAt(stack, player, world, x, y, z, side, hx, hy, hz, meta)) return false;
        TileEntity te = world.getTileEntity(x, y, z);
        if (world.isRemote || !(te instanceof TileBarrel)) return true;
        TileBarrel t = (TileBarrel) te;
        t.tier = BarrelTier.clamp(stack.getItemDamage());
        t.owner = player == null ? null : player.getUniqueID();
        NBTTagCompound tag = tag(stack);
        t.store();
        if (tag != null && world.captureBlockSnapshots) {
            // Forge can still undo this placement (another mod cancelling it gives the item back as it was): what the
            // item holds goes in at the end of this tick, once the barrel still stands.
            BarrelSync.placed(t, (NBTTagCompound) tag.copy(), player);
            t.markDirty();
            return true;
        }
        fill(t, tag, player);
        return true;
    }

    /** Puts what a barrel's item held into the placed barrel, and rejoins its channel (in its placer's team). */
    static void fill(TileBarrel t, NBTTagCompound tag, EntityPlayer player) {
        String channel = "", scope = "";
        if (tag != null) {
            t.own.read(tag.getCompoundTag("Store"));
            channel = tag.getString("Channel");
            scope = tag.getString("Scope");
        }
        t.store();
        if (!channel.isEmpty()) {
            BarrelChannels d = BarrelChannels.get();
            // Its placer's team (an item carried out of another team's place doesn't open their channel).
            String in = t.owner != null || scope.isEmpty() ? t.scopeOf(null) : scope;
            String why = d == null ? "the channels aren't loaded" : d.join(t, in, channel);
            if (why != null && player != null) BarrelClicks.tell(player, EnumChatFormatting.RED, "Not linked to \"" + channel + "\": " + why);
        }
        t.store()
            .changed();
        t.markDirty();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int t = BarrelTier.clamp(stack.getItemDamage());
        list.add(EnumChatFormatting.GRAY + "Holds " + String.format("%,d", BarrelTier.stacks(t)) + " stacks of one item.");
        NBTTagCompound tag = tag(stack);
        if (tag != null) {
            BarrelStore s = new BarrelStore();
            s.read(tag.getCompoundTag("Store"));
            if (!s.isEmpty()) list.add(EnumChatFormatting.YELLOW + String.format("%,d", s.count()) + " × " + s.item()
                .getDisplayName());
            if (s.locked) list.add(EnumChatFormatting.AQUA + "Locked to its item");
            if (!tag.getString("Channel")
                .isEmpty()) list.add(EnumChatFormatting.LIGHT_PURPLE + "Rejoins channel \"" + tag.getString("Channel") + "\" when placed");
        }
        list.add(EnumChatFormatting.DARK_GRAY + "Right-click: put in. Left-click: take a stack.");
        list.add(EnumChatFormatting.DARK_GRAY + "Sneak + right-click, empty hand: its menu.");
    }
}
