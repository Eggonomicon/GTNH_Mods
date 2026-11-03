package com.valier.loot;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.valier.loot.selftest.SelfTest;

/**
 * {@code /lootchest <info|reset|remove|add|stats> [x y z [dim]]} for ops. Without coordinates it acts on the chest
 * the player is looking at.
 */
public class LootCommand extends CommandBase {

    private static final String USAGE = "/lootchest <info|reset|remove|add|stats|reload> [x y z [dim]]";

    @Override
    public String getCommandName() {
        return "lootchest";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1
            ? getListOfStringsMatchingLastWord(args, "info", "reset", "remove", "add", "stats", "reload")
            : null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(USAGE);
        if ("selftest".equals(args[0]) && SelfTest.ENABLED) {
            SelfTest.run(sender, args);
            return;
        }
        if ("import".equals(args[0])) {
            // Reads files on the server, so only from the server console.
            if (!(sender instanceof MinecraftServer)) {
                reply(sender, "/lootchest import only works from the server console.");
                return;
            }
            if (args.length < 3) {
                reply(sender, "/lootchest import <candidates file> <backup world folder> [apply]");
                return;
            }
            BackupImport.run(sender, args[1], args[2], args.length > 3 && "apply".equals(args[3]));
            return;
        }
        if ("reload".equals(args[0])) {
            LootConfig.reload();
            reply(
                sender,
                "Reloaded config/valierloot.cfg: random loot " + LootConfig.randomMode.name()
                    .toLowerCase()
                    + ", "
                    + LootConfig.poolRules.size()
                    + " pool rule(s). New rolls use it; copies players already have are kept.");
            return;
        }
        if ("stats".equals(args[0])) {
            World world = args.length >= 2 ? world(sender, parseInt(sender, args[1])) : sender.getEntityWorld();
            int[] counts = LootData.get(world)
                .loadedCounts();
            reply(
                sender,
                "Loot chests in the loaded chunks of dimension " + world.provider.dimensionId
                    + ": "
                    + counts[1]
                    + " locked, "
                    + counts[0]
                    + " waiting to be locked. (Each chunk keeps its own; unloaded chunks aren't counted.)");
            return;
        }
        World world;
        int x, y, z;
        if (args.length >= 4) {
            x = parseInt(sender, args[1]);
            y = parseInt(sender, args[2]);
            z = parseInt(sender, args[3]);
            world = args.length >= 5 ? world(sender, parseInt(sender, args[4])) : sender.getEntityWorld();
        } else if (sender instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) sender;
            MovingObjectPosition hit = lookedAt(player);
            if (hit == null) {
                reply(sender, "Look at a chest (within 6 blocks) or give its coordinates.");
                return;
            }
            x = hit.blockX;
            y = hit.blockY;
            z = hit.blockZ;
            world = player.worldObj;
        } else {
            throw new WrongUsageException(USAGE);
        }
        LootData data = LootData.get(world);
        long pos = LootData.pack(x, y, z);
        String where = x + " " + y + " " + z;
        boolean chest = LootEvents.isVanillaChest(world.getTileEntity(x, y, z));
        switch (args[0]) {
            case "info": {
                if (!chest) reply(sender, where + " is not a vanilla chest.");
                else if (data.isPending(pos)) reply(sender, where + ": loot chest, loot not locked away yet.");
                else if (data.getOpened(pos) != null) {
                    LootData.LootChest c = data.getOpened(pos);
                    reply(
                        sender,
                        where + ": loot chest. "
                            + c.templateStacks()
                            + " stacks of loot; "
                            + c.playerCount()
                            + " player(s) have their own copy.");
                } else if (data.isIgnored(pos)) reply(sender, where + ": a player's chest (never loot).");
                else reply(sender, where + ": ordinary chest.");
                if (chest && data.isLoot(pos) && LootConfig.randomMode != LootConfig.RandomMode.OFF) {
                    LootConfig.PoolRule rule = RandomLoot.ruleFor(world, x, z);
                    reply(
                        sender,
                        "Random loot (" + LootConfig.randomMode.name()
                            .toLowerCase() + "): " + (rule == null ? "no pool rule applies here" : rule.describe()));
                }
                return;
            }
            case "reset": {
                if (!data.isLoot(pos)) {
                    reply(sender, where + " is not a loot chest.");
                    return;
                }
                // Both halves of a double chest; anything players stored in their copies goes too.
                for (long half : halves(world, data, x, y, z)) {
                    int hx = LootData.unpackX(half), hz = LootData.unpackZ(half);
                    LootEvents.closeWindows(world, hx, y, hz);
                    LootEvents.lock(world, data, hx, y, hz)
                        .resetCopies();
                }
                reply(sender, "Reset " + where + ": every player gets the full loot again.");
                return;
            }
            case "remove": {
                if (!data.isLoot(pos)) {
                    reply(sender, where + " is not a loot chest.");
                    return;
                }
                for (long half : halves(world, data, x, y, z)) {
                    int hx = LootData.unpackX(half), hz = LootData.unpackZ(half);
                    LootEvents.closeWindows(world, hx, y, hz);
                    LootEvents.lock(world, data, hx, y, hz);
                    data.restore(hx, y, hz);
                }
                reply(sender, where + " is an ordinary chest again, holding the original loot.");
                return;
            }
            case "add": {
                if (!chest) {
                    reply(sender, where + " is not a vanilla chest.");
                    return;
                }
                if (data.isLoot(pos)) {
                    reply(sender, where + " is already a loot chest.");
                    return;
                }
                data.forget(pos);
                data.addPending(pos);
                LootEvents.lock(world, data, x, y, z);
                reply(sender, where + " is now a loot chest: each player gets their own copy of what was in it.");
                return;
            }
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    /** The loot chest at x, y, z and, if it is a double chest, its other half. */
    private static List<Long> halves(World world, LootData data, int x, int y, int z) {
        List<Long> halves = new ArrayList<>();
        halves.add(LootData.pack(x, y, z));
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            long n = LootData.pack(x + d[0], y, z + d[1]);
            if (data.isLoot(n) && world.getBlock(x + d[0], y, z + d[1]) == world.getBlock(x, y, z)) halves.add(n);
        }
        return halves;
    }

    private static World world(ICommandSender sender, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) throw new WrongUsageException("Dimension " + dim + " is not loaded.");
        return world;
    }

    private static MovingObjectPosition lookedAt(EntityPlayerMP player) {
        // On the server a player's posY is at their feet.
        double eyeY = player.posY + (player.isSneaking() ? 1.54D : 1.62D);
        Vec3 eye = Vec3.createVectorHelper(player.posX, eyeY, player.posZ);
        Vec3 look = player.getLook(1.0F);
        Vec3 end = eye.addVector(look.xCoord * 6.0D, look.yCoord * 6.0D, look.zCoord * 6.0D);
        MovingObjectPosition hit = player.worldObj.rayTraceBlocks(eye, end);
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? hit : null;
    }

    private static void reply(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
}
