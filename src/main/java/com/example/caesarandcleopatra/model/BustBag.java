package com.example.caesarandcleopatra.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.example.caesarandcleopatra.model.PatricianCard.Type;

/**
 * Encapsulates the collection of BustPiece tokens in the game.
 * Provides methods to draw a random piece, return a piece, and view contents.
 */
public class BustBag {
    private final Set<BustPiece> completed = new HashSet<>();

    /**
     * The types of bust pieces in the game.
     */
    public static enum BustPiece {
        PRAETOR(Type.PRAETOR),
        AEDILE(Type.AEDILE),
        CONSUL(Type.CONSUL),
        CENSOR(Type.CENSOR),
        QUAESTOR(Type.QUAESTOR),
        BLACK(null),
        GREY(null);

        private final Type patricianType;

        private BustPiece(Type patricianType) {
            this.patricianType = patricianType;
        }

        /**
         * Returns the PatricianCard.Type corresponding to this colored bust,
         * or null for BLACK/GREY pieces.
         */
        public Type getPatricianType() {
            return patricianType;
        }
        public static BustPiece findByType(PatricianCard.Type t) {
            return Arrays.asList( BustPiece.values()).stream()
                .filter(b -> b.patricianType.equals(t))
                .findAny()
                .orElse(null);
        }
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
        this.contents = new ArrayList<>();
        reset();
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

    public void complete(PatricianCard.Type t) {
        completed.add(BustPiece.findByType(t));
    }
    public void reset() {
        contents.clear();
        Arrays.asList(BustPiece.values()).stream()
            .filter(bp -> !completed.contains(bp))
            .forEach(bp -> contents.add(bp));
    }
}