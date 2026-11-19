package com.valier.tweaks.pipe;

/**
 * What a pipe face touching a block does. Nothing moves until some face pulls (Pull, Push/Pull) or asks (Request): those
 * are the only ones that work on a timer, and so the only ones that cost anything. Ordinals are saved and sent to
 * players (three bits each): new modes go at the end.
 */
public enum FaceMode {

    /** Not connected (the pipe ignores that block). */
    OFF("Off", 0x707070, "Leaves this block alone."),
    /** Passive: Request faces on the network may take matching items from this block (the default). */
    PROVIDE("Provide", 0x55CC55, "Request faces on this network may take what they ask for from this block."),
    /** Passive: pulling faces deliver into this block. */
    INSERT("Push", 0x4D8DFF, "Things pulled from other blocks are pushed into this block."),
    /** Every interval, pulls up to its amount out of this block and sends it to pushing faces. */
    EXTRACT("Pull", 0xFF9933, "Pulls things out of this block and sends them to Push faces (then Request faces)."),
    /** Keeps its filter's amounts stocked in this block, taking what's missing from Provide faces. */
    REQUEST("Request", 0xB266FF, "Keeps what its filter lists stocked in this block, from Provide faces."),
    /** Both: pulls from this block on its timer, and takes what other faces push (never its own items back). */
    PUSH_PULL("Push/Pull", 0x33CCBB, "Pulls from this block, and also takes what other faces push (a buffer).");

    public final String words;
    /** Its colour on the pipe and in the menu. */
    public final int colour;
    public final String explain;

    FaceMode(String words, int colour, String explain) {
        this.words = words;
        this.colour = colour;
        this.explain = explain;
    }

    /** Works on a timer (the only modes that cost anything). */
    public boolean active() {
        return this == EXTRACT || this == REQUEST || this == PUSH_PULL;
    }

    public boolean pulls() {
        return this == EXTRACT || this == PUSH_PULL;
    }

    /** Takes what pulling faces send. */
    public boolean takesPushes() {
        return this == INSERT || this == PUSH_PULL;
    }

    /** The order the menu cycles through. */
    public static final FaceMode[] CYCLE = { PROVIDE, INSERT, EXTRACT, PUSH_PULL, REQUEST, OFF };

    public FaceMode next(int by) {
        int i = 0;
        for (int k = 0; k < CYCLE.length; k++) if (CYCLE[k] == this) i = k;
        return CYCLE[Math.floorMod(i + by, CYCLE.length)];
    }

    public static FaceMode of(int i) {
        FaceMode[] all = values();
        return i >= 0 && i < all.length ? all[i] : PROVIDE;
    }
}
