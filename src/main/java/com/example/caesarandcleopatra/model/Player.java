package com.example.caesarandcleopatra.model;

public enum Player {
    CAESAR,
    CLEOPATRA;

    /**
     * Returns the opposing player.
     *
     * @return the opponent of this player
     */
    public Player opponent() {
        return this == CAESAR ? CLEOPATRA : CAESAR;
    }
}