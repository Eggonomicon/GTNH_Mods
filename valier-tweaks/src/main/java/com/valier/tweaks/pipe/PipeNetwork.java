package com.valier.tweaks.pipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * Pipes of one kind joined together (found by a flood fill in {@link PipeNetworks}), and their faces touching blocks,
 * sorted by what they do. Rebuilt whenever one of its pipes changes; never ticks itself.
 */
public final class PipeNetwork {

    final PipeKind kind;
    final List<Long> pipes = new ArrayList<>();
    final List<Face> all = new ArrayList<>();
    final List<Face> inserts = new ArrayList<>(), requests = new ArrayList<>(), provides = new ArrayList<>(),
        extracts = new ArrayList<>(), overflow = new ArrayList<>();
    /** More pipes than {@code pipeNetworkMax}: it does nothing at all (and WAILA, the menu and the command say so). */
    boolean tooBig;

    PipeNetwork(PipeKind kind) {
        this.kind = kind;
    }

    public int pipeCount() {
        return pipes.size();
    }

    public int faceCount() {
        return all.size();
    }

    public boolean isTooBig() {
        return tooBig;
    }

    /** Higher priority first; equal priorities always in the same order (by place), for the "in order" spread. */
    static final Comparator<Face> BY_PRIORITY = (a, b) -> {
        int c = Integer.compare(b.cfg.priority, a.cfg.priority);
        if (c != 0) return c;
        if (a.pipe.xCoord != b.pipe.xCoord) return Integer.compare(a.pipe.xCoord, b.pipe.xCoord);
        if (a.pipe.zCoord != b.pipe.zCoord) return Integer.compare(a.pipe.zCoord, b.pipe.zCoord);
        if (a.pipe.yCoord != b.pipe.yCoord) return Integer.compare(a.pipe.yCoord, b.pipe.yCoord);
        return Integer.compare(a.side, b.side);
    };

    void sort() {
        for (Face f : all) {
            switch (f.cfg.mode) {
                case INSERT:
                    inserts.add(f);
                    break;
                case PUSH_PULL:
                    inserts.add(f);
                    extracts.add(f);
                    break;
                case REQUEST:
                    requests.add(f);
                    break;
                case EXTRACT:
                    extracts.add(f);
                    break;
                case PROVIDE:
                    provides.add(f);
                    if (f.cfg.acceptOverflow) overflow.add(f);
                    break;
                default:
            }
        }
        inserts.sort(BY_PRIORITY);
        requests.sort(BY_PRIORITY);
        provides.sort(BY_PRIORITY);
        overflow.sort(BY_PRIORITY);
    }

    /** One face of a pipe touching a block, as the network knows it. */
    public static final class Face {

        final PipeNetwork net;
        final TilePipe pipe;
        final int side;
        /** The side of the block it touches, as that block sees it (whose slots and tanks apply). */
        final int access;
        final int tx, ty, tz;
        final FaceConfig cfg;
        final int tier;
        final UUID owner;
        /** For the timer queue: when it works next, how many times in a row it found nothing to do. */
        long due;
        int idle;
        boolean dead;
        /** In the timer queue (not waiting for a chunk, not being run). */
        boolean queued;
        /** The wireless scope its tag lives in (its owner's team), when it is a Wireless face with a tag. */
        Object scope;
        /** A creative pipe's centre: the endless inventory or tank it draws from (else null: the block it touches). */
        final CreativeSource source;

        Face(PipeNetwork net, TilePipe pipe, int side) {
            this.net = net;
            this.pipe = pipe;
            this.side = side;
            ForgeDirection d = ForgeDirection.getOrientation(side);
            this.access = d.getOpposite()
                .ordinal();
            this.tx = pipe.xCoord + d.offsetX;
            this.ty = pipe.yCoord + d.offsetY;
            this.tz = pipe.zCoord + d.offsetZ;
            this.cfg = pipe.face(side);
            this.tier = pipe.tier();
            this.owner = pipe.owner();
            this.source = side == TilePipe.SOURCE ? new CreativeSource(cfg, pipe.kind(), tier) : null;
        }

        boolean wireless() {
            return PipeTier.wireless(tier) && !cfg.tag.isEmpty();
        }

        /** Faces trade only with faces of the same tag. */
        boolean sameTag(Face o) {
            return cfg.tag.equals(o.cfg.tag);
        }

        /** Whether its redstone setting lets it work now. */
        boolean powered() {
            if (cfg.redstone == FaceConfig.ALWAYS) return true;
            boolean on = pipe.getWorldObj()
                .isBlockIndirectlyGettingPowered(pipe.xCoord, pipe.yCoord, pipe.zCoord);
            return cfg.redstone == FaceConfig.WITH_SIGNAL ? on : !on;
        }

        String where() {
            return pipe.xCoord + " " + pipe.yCoord + " " + pipe.zCoord + " "
                + (side == TilePipe.SOURCE ? "source" : ForgeDirection.getOrientation(side).toString());
        }
    }
}
