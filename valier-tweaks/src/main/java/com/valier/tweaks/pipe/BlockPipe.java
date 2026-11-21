package com.valier.tweaks.pipe;

import java.util.List;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.valier.tweaks.ValierTweaks;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Item pipes and fluid pipes ({@link PipeKind}), metadata = tier ({@link PipeTier}). Thin connected pipes: a core and
 * an arm to each joined side, with a coloured plate where an arm touches a block (the face's mode). Right-click an arm
 * touching a block for its menu; sneak-right-click it with a wrench to shut or open that side. A creative pipe's centre
 * opens its source menu.
 */
public class BlockPipe extends BlockContainer {

    /** Set on players' computers (the pipe renderer's id). */
    public static int renderId = -1;
    public static final float CORE_MIN = 5 / 16F, CORE_MAX = 11 / 16F;
    /** The plate where an arm touches a block: this wide either side of the middle, one pixel thick. */
    public static final float PLATE_MIN = 3 / 16F, PLATE_MAX = 13 / 16F, PLATE = 1 / 16F;

    public final PipeKind kind;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;
    @SideOnly(Side.CLIENT)
    public IIcon plate;

    public BlockPipe(PipeKind kind) {
        super(Material.iron);
        this.kind = kind;
        setHardness(1.0F);
        setResistance(10.0F);
        setStepSound(soundTypeMetal);
        setHarvestLevel("pickaxe", 0);
        setBlockName(ValierTweaks.MODID + "." + kind.word + "_pipe");
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TilePipe();
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return renderId;
    }

    @Override
    public int damageDropped(int meta) {
        return PipeTier.clamp(meta);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int t : PipeTier.ORDER) list.add(new ItemStack(item, 1, t));
    }

    /** Creative pipes can't be broken in survival (only creative players, or an op's wrench). */
    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == PipeTier.CREATIVE ? -1.0F : super.getBlockHardness(world, x, y, z);
    }

    @Override
    public float getExplosionResistance(net.minecraft.entity.Entity e, World world, int x, int y, int z, double ex, double ey,
        double ez) {
        return world.getBlockMetadata(x, y, z) == PipeTier.CREATIVE ? 6_000_000.0F
            : super.getExplosionResistance(e, world, x, y, z, ex, ey, ez);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        icons = new IIcon[PipeTier.COUNT];
        for (int t = 0; t < PipeTier.COUNT; t++) icons[t] = reg.registerIcon(ValierTweaks.MODID + ":" + kind.word + "_pipe_" + t);
        plate = reg.registerIcon(ValierTweaks.MODID + ":pipe_plate");
        blockIcon = icons[0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[PipeTier.clamp(meta)];
    }

    // ---- placing, neighbours, breaking ------------------------------------------------------------------------------

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (world.isRemote || !(te instanceof TilePipe)) return;
        TilePipe p = (TilePipe) te;
        if (placer instanceof EntityPlayer) {
            p.owner = ((EntityPlayer) placer).getGameProfile()
                .getId();
            p.ownerName = placer.getCommandSenderName();
        }
        p.updateConnections();
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity n = world.getTileEntity(x + d.offsetX, y + d.offsetY, z + d.offsetZ);
            if (n instanceof TilePipe) ((TilePipe) n).updateConnections();
        }
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TilePipe) ((TilePipe) te).updateConnections();
    }

    /** A block next to it changed (placed, broken): its joins are worked out again. */
    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TilePipe) ((TilePipe) te).updateConnections();
    }

    /** What's inside a neighbour changed (inventories say so when they change): its faces there wake up. */
    @Override
    public void onNeighborChange(IBlockAccess access, int x, int y, int z, int tx, int ty, int tz) {
        if (!(access instanceof World) || ((World) access).isRemote) return;
        World world = (World) access;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TilePipe)) return;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (x + d.offsetX == tx && y + d.offsetY == ty && z + d.offsetZ == tz) {
                PipeNetworks.wake(world, x, y, z, d.ordinal());
                return;
            }
        }
    }

    /** What its faces held back drops. */
    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!world.isRemote && te instanceof TilePipe) {
            for (FaceConfig f : ((TilePipe) te).faces) {
                if (f == null) continue;
                for (ItemStack s : f.heldItems) com.valier.tweaks.LootSpawners.dropAt(world, x, y, z, s);
                f.heldItems.clear();
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
        if (!world.isRemote) PipeNetworks.dirty(world, x, y, z);
    }

    // ---- clicks -----------------------------------------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hx, float hy,
        float hz) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TilePipe)) return false;
        if (world.isRemote) return true; // the server decides (which arm was hit, from where the player looks)
        TilePipe p = (TilePipe) te;
        int arm = armLookedAt(world, x, y, z, player);
        if (arm == 6 && p.creative()) return PipeClicks.activated(p, TilePipe.SOURCE, player);
        if (arm < 0 || arm > 5) arm = side; // the core: the side of it that was clicked
        return PipeClicks.activated(p, arm, player);
    }

    // ---- shape ----------------------------------------------------------------------------------------------------

    /** The boxes of a pipe with these joins: 0-5 the arms (with their plates), 6 the core. Null for no arm. */
    public static AxisAlignedBB[] boxes(int conn) {
        AxisAlignedBB[] b = new AxisAlignedBB[7];
        for (int s = 0; s < 6; s++) {
            boolean pipe = (conn >> s & 1) != 0, block = (conn >> (6 + s) & 1) != 0;
            if (!pipe && !block) continue;
            float lo = block ? PLATE_MIN : CORE_MIN, hi = block ? PLATE_MAX : CORE_MAX;
            switch (s) {
                case 0:
                    b[s] = AxisAlignedBB.getBoundingBox(lo, 0, lo, hi, CORE_MIN, hi);
                    break;
                case 1:
                    b[s] = AxisAlignedBB.getBoundingBox(lo, CORE_MAX, lo, hi, 1, hi);
                    break;
                case 2:
                    b[s] = AxisAlignedBB.getBoundingBox(lo, lo, 0, hi, hi, CORE_MIN);
                    break;
                case 3:
                    b[s] = AxisAlignedBB.getBoundingBox(lo, lo, CORE_MAX, hi, hi, 1);
                    break;
                case 4:
                    b[s] = AxisAlignedBB.getBoundingBox(0, lo, lo, CORE_MIN, hi, hi);
                    break;
                default:
                    b[s] = AxisAlignedBB.getBoundingBox(CORE_MAX, lo, lo, 1, hi, hi);
            }
        }
        b[6] = AxisAlignedBB.getBoundingBox(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX);
        return b;
    }

    static int conn(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        return te instanceof TilePipe ? ((TilePipe) te).conn : 0;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list, Entity entity) {
        for (AxisAlignedBB b : boxes(conn(world, x, y, z))) {
            if (b == null) continue;
            AxisAlignedBB w = b.getOffsetBoundingBox(x, y, z);
            if (mask.intersectsWith(w)) list.add(w);
        }
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        float[] u = { CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX };
        for (AxisAlignedBB b : boxes(conn(world, x, y, z))) {
            if (b == null) continue;
            u[0] = (float) Math.min(u[0], b.minX);
            u[1] = (float) Math.min(u[1], b.minY);
            u[2] = (float) Math.min(u[2], b.minZ);
            u[3] = (float) Math.max(u[3], b.maxX);
            u[4] = (float) Math.max(u[4], b.maxY);
            u[5] = (float) Math.max(u[5], b.maxZ);
        }
        setBlockBounds(u[0], u[1], u[2], u[3], u[4], u[5]);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(0, CORE_MIN, CORE_MIN, 1, CORE_MAX, CORE_MAX);
    }

    /** Ray traces against the core and each arm, so the outline (and a click) is the part looked at. */
    @Override
    public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 start, Vec3 end) {
        int hit = -1;
        MovingObjectPosition best = null;
        double bestDist = Double.MAX_VALUE;
        AxisAlignedBB[] boxes = boxes(conn(world, x, y, z));
        for (int i = 0; i < boxes.length; i++) {
            if (boxes[i] == null) continue;
            MovingObjectPosition m = boxes[i].getOffsetBoundingBox(x, y, z)
                .calculateIntercept(start, end);
            if (m == null) continue;
            double d = m.hitVec.squareDistanceTo(start);
            if (d < bestDist) {
                bestDist = d;
                best = m;
                hit = i;
            }
        }
        if (best == null) return null;
        if (world.isRemote) { // the outline drawn is the part looked at
            AxisAlignedBB b = boxes[hit];
            setBlockBounds((float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, (float) b.maxY, (float) b.maxZ);
        }
        MovingObjectPosition out = new MovingObjectPosition(x, y, z, best.sideHit, best.hitVec);
        out.subHit = hit;
        return out;
    }

    /** Which part a player looks at (0-5 an arm, 6 the core, -1 none), traced again on the server from their eyes. */
    public static int armLookedAt(World world, int x, int y, int z, EntityPlayer player) {
        double reach = player instanceof EntityPlayerMP ? ((EntityPlayerMP) player).theItemInWorldManager.getBlockReachDistance()
            : 5.0;
        Vec3 eye = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight() - (player.isSneaking() ? 0.08 : 0),
            player.posZ);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        AxisAlignedBB[] boxes = boxes(conn(world, x, y, z));
        int hit = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < boxes.length; i++) {
            if (boxes[i] == null) continue;
            MovingObjectPosition m = boxes[i].getOffsetBoundingBox(x, y, z)
                .calculateIntercept(eye, end);
            if (m == null) continue;
            double d = m.hitVec.squareDistanceTo(eye);
            if (d < bestDist) {
                bestDist = d;
                hit = i;
            }
        }
        return hit;
    }

    /** Owner helper for the self-test and placing by code. */
    public static void setOwner(TilePipe p, UUID id, String name) {
        p.owner = id;
        p.ownerName = name == null ? "" : name;
    }
}
