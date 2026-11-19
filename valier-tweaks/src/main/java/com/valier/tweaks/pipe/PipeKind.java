package com.valier.tweaks.pipe;

/** Item pipes and fluid pipes: the same blocks, network and faces, moving different things. */
public enum PipeKind {

    ITEM("item"),
    FLUID("fluid");

    public final String word;

    PipeKind(String word) {
        this.word = word;
    }
}
