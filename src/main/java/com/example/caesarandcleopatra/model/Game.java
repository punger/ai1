package com.example.caesarandcleopatra.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Random;

import static com.example.caesarandcleopatra.model.BustBag.BustPiece;

/**
 * Represents the complete game state, including board patricians, played influence cards,
 * player hands, action and influence decks, discard pile, and bust bag.
 */
public class Game {
    public static long seed = 0;
    private static Random shuffler;

    // Encapsulates patrician board and played influence state
    private PatricianState patricianState;
    PatricianState getPatricianState() { return patricianState;}

    public static final int MAX_HAND_SIZE = 6;
    // Each player's hand of cards
    private Map<Player, List<Card>> playerHands;

    // Each player's deck of Action cards (separate from Influence cards)
    private Map<Player, List<ActionCard>> playerActionDecks;

    // Each player's deck of Influence cards
    private Map<Player, List<InfluenceCard>> playerInfluenceDecks;

    // Discard pile for cards
    private List<Card> discardPile;

    // Bag of Bust pieces for drawing during the game
    private BustBag bustBag;

    // Patrician card counts claimed by each player from Votes of Confidence
    private Map<Player, Map<PatricianCard.Type, Integer>> playerPatricians;

    // Generic deck generator for any card type
    private static <E, C> Map<Player, List<C>> generateDecks(E[] types, java.util.function.Function<E, Integer> countFn, java.util.function.BiFunction<String, E, C> creator) {
        Map<Player, List<C>> map = new HashMap<>();
        for (Player p : Player.values()) {
            List<C> deck = new LinkedList<>();
            for (E t : types) {
                int count = countFn.apply(t);
                String id = ((Enum<?>) t).name().toLowerCase();
                for (int i = 0; i < count; i++) {
                    deck.add(creator.apply(id, t));
                }
            }
            Collections.shuffle(deck, shuffler);
            map.put(p, deck);
        }
        return map;
    }

    public Game() {
        this(seed);
    }
    /**
     * Constructs a new Game, initializing all components:
     * - Bored patricians
     * - Played influence card mappings
     * - Player hands and action/influence decks
     * - Discard pile
     * - Bust bag
     */
    public Game(long seed) {
        Game.seed = seed;
        shuffler = new Random(seed);
        patricianState = new PatricianState(this);
        // PatricianState manages board cards and played influence
        playerHands            = new HashMap<>();
        playerActionDecks      = generateDecks(ActionCard.Type.values(), ActionCard.Type::getCount, ActionCard::new);
        playerInfluenceDecks   = generateDecks(InfluenceCard.Type.values(), InfluenceCard.Type::getCount, InfluenceCard::new);

        // Initialize each player's starting hand: 5 Influence cards (1-5) and 1 Veto
        for (Player p : Player.values()) {
            List<Card> hand = new ArrayList<>();
            // Influence cards 1-5
            for (InfluenceCard.Type t : InfluenceCard.Type.values()) {
                if (t.getValue() > 0) {
                    String id = t.name().toLowerCase();
                    hand.add(new InfluenceCard(id, t));
                }
            }
            // Veto card
            hand.add(new ActionCard(ActionCard.Type.VETO.name(), ActionCard.Type.VETO));
            playerHands.put(p, hand);
        }
        discardPile            = new ArrayList<>();

        // Initialize bust bag with one of each BustPiece
        bustBag = new BustBag(shuffler);

        // Initialize claimed patrician counts per player
        playerPatricians = new HashMap<>();
        for (Player p : Player.values()) {
            Map<PatricianCard.Type, Integer> counts = new java.util.EnumMap<>(PatricianCard.Type.class);
            for (PatricianCard.Type t : PatricianCard.Type.values()) {
                counts.put(t, 0);
            }
            playerPatricians.put(p, counts);
        }
    }

    public boolean draw(Player p, boolean isInfluence) {
        if (playerHands.get(p).size() >= MAX_HAND_SIZE) return false;
        if (isInfluence) {
            playerHands.get(p).add(playerInfluenceDecks.get(p).removeLast());
        } else {
            playerHands.get(p).add(playerActionDecks.get(p).removeLast());
        }
        return true;
    }
    // Methods to handle played influence cards
    /**
     * Plays an Influence card for a player against a specified Patrician group.
     *
     * @param patricianType the Patrician group type targeted
     * @param player the player playing the card
     * @param influenceCard the InfluenceCard being played
     * @param faceUp whether the card is placed face up
     */
    public void playInfluenceCard(PatricianCard.Type patricianType, Player player, InfluenceCard influenceCard, boolean faceUp) {
        patricianState.addInfluence(patricianType, player, influenceCard, faceUp);
    }

    // Methods for discard pile management
    /**
     * Discards a Card by adding it to the discard pile. Ignores null cards.
     *
     * @param card the Card to discard; null is ignored
     */
    public void discard(Card card) {
        if (card == null) {
            return;
        }
        discardPile.add(card);
    }

    /**
     * Draws a random BustPiece from the bag and removes it.
     *
     * @return the drawn BustPiece
     */
    public BustPiece drawBust() {
        return bustBag.draw();
    }

    /**
     * Returns the current contents of the bust bag.
     *
     * @return list of remaining BustPieces in the bag
     */
    public List<BustPiece> getBustBag() {
        return bustBag.getContents();
    }

    /**
     * Returns the Patrician card counts claimed by each player during Votes of Confidence.
     *
     * @return map of Player to map of Patrician type to count
     */
    public Map<Player, Map<PatricianCard.Type, Integer>> getPlayerPatricianCounts() {
        return playerPatricians;
    }

    /**
     * Resolves a Vote of Confidence (VOC) for the specified Patrician group,
     * and increments the winner's claimed patrician count.
     *
     * @param type the Patrician group type
     * @return the winning Player, or null if tie
     */
    public Player resolveVoteOfConfidence(PatricianCard.Type type) {
        Player winner = patricianState.resolveVoteOfConfidence(type);
        if (winner != null) {
            Map<PatricianCard.Type, Integer> counts = playerPatricians.get(winner);
            counts.put(type, counts.getOrDefault(type, 0) + 1);
        }
        return winner;
    }
}
