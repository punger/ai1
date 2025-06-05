package com.example.caesarandcleopatra.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Encapsulates the collection of BustPiece tokens in the game.
 * Provides methods to draw a random piece, return a piece, and view contents.
 */
public class BustBag {

    /**
     * The types of bust pieces in the game.
     */
    public static enum BustPiece {
        PRAETOR, AEDILE, CONSUL, CENSOR, QUAESTOR, BLACK, GREY
    }

    private final List<BustPiece> contents;
    private final Random shuffler;

    /**
     * Constructs a new BustBag initialized with one of each BustPiece.
     * Uses the provided Random instance for reproducible draws.
     *
     * @param shuffler the Random instance to use for drawing
     */
    public BustBag(Random shuffler) {
        this.shuffler = shuffler;
        this.contents = new ArrayList<>(Arrays.asList(BustPiece.values()));
    }

    /**
     * Draws a random BustPiece from the bag and removes it.
     *
     * @return the drawn BustPiece
     */
    public BustPiece draw() {
        int idx = shuffler.nextInt(contents.size());
        return contents.remove(idx);
    }

    /**
     * Returns a BustPiece back into the bag.
     *
     * @param piece the BustPiece to return
     */
    public void returnPiece(BustPiece piece) {
        contents.add(piece);
    }

    /**
     * Returns an unmodifiable view of the current contents of the bag.
     *
     * @return list of remaining BustPieces in the bag
     */
    public List<BustPiece> getContents() {
        return Collections.unmodifiableList(contents);
    }
}