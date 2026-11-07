package gtnhserver.mmfreepower.mixin;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.recursive_pineapple.matter_manipulator.common.building.AbstractBuildable;
import com.recursive_pineapple.matter_manipulator.common.building.MMInventory;

import gtnhserver.mmfreepower.MMFortune;
import gtnhserver.mmfreepower.MkIVConfig;

/**
 * Upstream, a Matter Manipulator deletes every ore it removes: in {@code AbstractBuildable.removeBlock}, a block that
 * GregTech calls an ore ({@code GTUtility.isOre}: every naturally generated GT, GT++ and BartWorks ore, big or small)
 * or that has an ore dictionary name starting with "ore" (vanilla and other mods' ores) is set to air with its drops
 * thrown away. With {@code mining/dropOres} on, both checks answer "not an ore", so ores take the manipulator's normal
 * path: the block's own drops (what a pickaxe without Fortune gets) go to the player's uplink, ME system or inventory
 * like any other removed block.
 * <p>
 * Drops are also worked out with the Fortune level of the manipulator in hand ({@link MMFortune#miningLevel}) instead
 * of none.
 */
@Mixin(value = AbstractBuildable.class, remap = false)
public abstract class MixinAbstractBuildable {

    private static final String REMOVE_BLOCK = "removeBlock(Lnet/minecraft/world/World;IIILcom/recursive_pineapple/matter_manipulator/common/building/ImmutableBlockSpec;Z)V";

    @WrapOperation(
        method = REMOVE_BLOCK,
        at = @At(value = "INVOKE", target = "Lgregtech/api/util/GTUtility;isOre(Lnet/minecraft/block/Block;I)Z"),
        remap = false)
    private boolean mmfreepower$gtOreDrops(Block block, int meta, Operation<Boolean> original) {
        return !MkIVConfig.dropOres && original.call(block, meta);
    }

    @WrapOperation(
        method = REMOVE_BLOCK,
        at = @At(value = "INVOKE", target = "Ljava/lang/String;startsWith(Ljava/lang/String;)Z"),
        remap = false)
    private boolean mmfreepower$oreDictDrops(String oreName, String prefix, Operation<Boolean> original) {
        return !MkIVConfig.dropOres && original.call(oreName, prefix);
    }

    /**
     * The manipulator asks for drops with Fortune 0 and no harvesting player. Ask with the Fortune on the manipulator
     * in hand, with the player set as the harvester the way a pickaxe's harvestBlock does: GregTech ores only apply
     * Fortune for a real player in {@code Block.harvesters}.
     */
    @WrapOperation(
        method = REMOVE_BLOCK,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;getDrops(Lnet/minecraft/world/World;IIIII)Ljava/util/ArrayList;"),
        remap = false)
    private ArrayList<ItemStack> mmfreepower$minedDrops(Block block, World world, int x, int y, int z, int meta,
        int fortune, Operation<ArrayList<ItemStack>> original) {
        EntityPlayer player = ((MMInventory) (Object) this).player;
        // The manipulator's block spec carries an item-style metadata (a natural GregTech ore's 8032 becomes 32, which
        // GregTech treats as player-placed and gives no Fortune). The block is still in the world here, so use its
        // real metadata, as a pickaxe would.
        int realMeta = world.getBlock(x, y, z) == block ? world.getBlockMetadata(x, y, z) : meta;
        Object previous = MMFortune.beginHarvest(block, player);
        try {
            return original.call(block, world, x, y, z, realMeta, Math.max(fortune, MMFortune.miningLevel(player)));
        } finally {
            MMFortune.endHarvest(block, previous);
        }
    }

    /** The same Fortune level for the harvest event that other mods use to change drops. */
    @ModifyArg(
        method = REMOVE_BLOCK,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/event/ForgeEventFactory;fireBlockHarvesting(Ljava/util/ArrayList;Lnet/minecraft/world/World;Lnet/minecraft/block/Block;IIIIIFZLnet/minecraft/entity/player/EntityPlayer;)F"),
        index = 7,
        remap = false)
    private int mmfreepower$fortuneEvent(int fortune) {
        return Math.max(fortune, MMFortune.miningLevel(((MMInventory) (Object) this).player));
    }
}
