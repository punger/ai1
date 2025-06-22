package com.example.caesarandcleopatra.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Random;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.example.caesarandcleopatra.model.BustBag.BustPiece;

/**
 * Represents the complete game state, including board patricians, played influence cards,
 * player hands, action and influence decks, discard pile, and bust bag.
 */
public class Game {
    public static long seed = 0;
    private static Random shuffler;

    // Game mode enumeration
    public enum GameMode {
        INITIAL_INFLUENCE_PLACEMENT,
        STANDARD_PLAY
    }

    // Turn phase enumeration for standard play
    public enum TurnPhase {
        FIRST_CARD_SELECTION,     // Choose first card (influence face-down or action)
        SECOND_CARD_SELECTION,    // Optionally choose second card (influence face-up or action)
        VOTE_OF_CONFIDENCE,       // Vote of confidence occurs
        DRAW_PHASE               // Draw to 6 cards from chosen deck
    }

    // Current game mode
    private GameMode currentMode;
    public GameMode getCurrentMode() { return currentMode; }

    // Current turn phase (only relevant during STANDARD_PLAY)
    private TurnPhase currentTurnPhase;
    public TurnPhase getCurrentTurnPhase() { return currentTurnPhase; }

    // Current player whose turn it is
    private Player currentPlayer;
    public Player getCurrentPlayer() { return currentPlayer; }

    // Turn state tracking
    private int cardsPlayedThisTurn = 0;
    private boolean actionCardPlayedThisTurn = false;
    private ActionCard pendingActionCard = null;
    private ActionContext pendingActionContext = null;
    
    // Track which players have completed initial influence placement
    private boolean caesarInitialInfluencePlaced = false;
    private boolean cleopatraInitialInfluencePlaced = false;
    
    public boolean hasPlayerPlacedInitialInfluence(Player player) {
        return player == Player.CAESAR ? caesarInitialInfluencePlaced : cleopatraInitialInfluencePlaced;
    }

    // Encapsulates patrician board and played influence state
    private PatricianState patricianState;
    public PatricianState getPatricianState() { return patricianState;}

    public static final int MAX_HAND_SIZE = 6;
    // Each player's hand of cards
    private Map<Player, List<Card>> playerHands;

    // Each player's deck of Action cards (separate from Influence cards)
    private Map<Player, List<ActionCard>> playerActionDecks;

    // Each player's deck of Influence cards
    private Map<Player, List<InfluenceCard>> playerInfluenceDecks;

    // Discard pile for cards
    private List<Card> discardPile;
    public List<Card> getDiscards() { return discardPile;}
    // Bag of Bust pieces for drawing during the game
    private BustBag bustBag;

    // Patrician card counts claimed by each player from Votes of Confidence
    private Map<Player, Map<PatricianCard.Type, Integer>> playerPatricians;

    // Generic deck generator for any card type
    private static <E, C> Map<Player, List<C>> generateDecks(E[] types, Function<E, Integer> countFn, BiFunction<String, E, C> creator) {
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
        
        // Initialize game mode and current player
        currentMode = GameMode.INITIAL_INFLUENCE_PLACEMENT;
        currentPlayer = Player.CAESAR; // Caesar goes first
        currentTurnPhase = TurnPhase.FIRST_CARD_SELECTION; // Default phase
        
        patricianState = new PatricianState(this);
        // PatricianState manages board cards and played influence
        playerHands            = new HashMap<>();
        playerActionDecks      = generateDecks(ActionCard.Type.values(), ActionCard.Type::getCount, ActionCard::new);
        playerInfluenceDecks   = generateDecks(InfluenceCard.Type.values(), InfluenceCard.Type::getCount, InfluenceCard::new);

        // Initialize each player's starting hand: 5 Influence cards (1-5) and 1 Veto
        initializeStartingHands();
        discardPile            = new LinkedList<>();

        // Initialize bust bag with one of each BustPiece
        bustBag = new BustBag(shuffler);

        // Initialize claimed patrician counts per player
        playerPatricians = new HashMap<>();
        for (Player p : Player.values()) {
            Map<PatricianCard.Type, Integer> counts = new EnumMap<>(PatricianCard.Type.class);
            for (PatricianCard.Type t : PatricianCard.Type.values()) {
                counts.put(t, 0);
            }
            playerPatricians.put(p, counts);
        }
    }

    /**
     * Initializes the starting hand for each player: 5 Influence cards (1-5) and 1 Veto card.
     * This method is called during game initialization and when transitioning to standard play.
     */
    private void initializeStartingHands() {
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
    }

    public Card draw(Player p, boolean isInfluence) {
        if (playerHands.get(p).size() >= MAX_HAND_SIZE) return null;
        Card drawn;
        if (isInfluence) {
            drawn = playerInfluenceDecks.get(p).removeLast();
        } else {
            drawn = playerActionDecks.get(p).removeLast();
        }
        playerHands.get(p).add(drawn);
        return drawn;
    }
    // Methods to handle played influence cards
    /**
     * Plays an Influence card for a player against a specified Patrician group.
     * Behavior depends on the current game mode.
     *
     * @param patricianType the Patrician group type targeted
     * @param player the player playing the card
     * @param influenceCard the InfluenceCard being played
     * @param faceUp whether the card is placed face up
     */
    public void playInfluenceCard(PatricianCard.Type patricianType, Player player, InfluenceCard influenceCard, boolean faceUp) {
        // Remove the card from the player's hand
        List<Card> hand = playerHands.get(player);
        boolean removed = hand.removeIf(card ->
            card instanceof InfluenceCard ic &&
            ic.id().equals(influenceCard.id()) &&
            ic.type().equals(influenceCard.type()));
        
        if (!removed) {
            throw new IllegalArgumentException("Card not found in player's hand: " + influenceCard.id());
        }
        
        if (currentMode == GameMode.INITIAL_INFLUENCE_PLACEMENT) {
            // During initial placement, cards are always face down
            patricianState.addInfluence(patricianType, player, influenceCard, false);
        } else {
            // During standard play, honor the faceUp parameter
            patricianState.addInfluence(patricianType, player, influenceCard, faceUp);
        }
    }

    public void setHand(Player p, Card...cards) {
        playerHands.put(p, Arrays.asList(cards));
    }
    public void setHand(Player p, List<Card> cards) {
        playerHands.put(p, cards);
    }
    /**
     * Finds and returns an InfluenceCard from the player's hand by its ID.
     *
     * @param player the player whose hand to search
     * @param cardId the ID of the card to find
     * @return the InfluenceCard if found, null otherwise
     */
    public InfluenceCard findInfluenceCardInHand(Player player, String cardId) {
        List<Card> hand = playerHands.get(player);
        return hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .filter(ic -> ic.id().equals(cardId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Completes initial influence placement for the current player and transitions
     * to the next player or to standard play mode.
     */
    public void completeInitialInfluencePlacement() {
        if (currentMode != GameMode.INITIAL_INFLUENCE_PLACEMENT) {
            throw new IllegalStateException("Not in initial influence placement mode");
        }
        
        // Mark current player as having completed initial placement
        if (currentPlayer == Player.CAESAR) {
            caesarInitialInfluencePlaced = true;
        } else {
            cleopatraInitialInfluencePlaced = true;
        }
        
        // Check if both players have placed their initial influence
        if (caesarInitialInfluencePlaced && cleopatraInitialInfluencePlaced) {
            // Both players done - transition to standard play
            currentMode = GameMode.STANDARD_PLAY;
            currentPlayer = Player.CAESAR; // Caesar starts standard play
            currentTurnPhase = TurnPhase.FIRST_CARD_SELECTION;
            
            // Reset turn state
            cardsPlayedThisTurn = 0;
            actionCardPlayedThisTurn = false;
            pendingActionCard = null;
            pendingActionContext = null;
            
            // Reset both players' hands to the starting hand for standard play
            initializeStartingHands();
        } else {
            // Switch to the other player for their initial placement
            currentPlayer = currentPlayer.opponent();
        }
    }

    /**
     * Checks if the current player needs to place initial influence.
     */
    public boolean isWaitingForInitialInfluence() {
        return currentMode == GameMode.INITIAL_INFLUENCE_PLACEMENT &&
               !hasPlayerPlacedInitialInfluence(currentPlayer);
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
     * Returns the current hands for each player.
     *
     * @return unmodifiable map of Player to their hand cards
     */
    public Map<Player, List<Card>> getPlayerHands() {
        return Collections.unmodifiableMap(playerHands);
    }

    /**
     * Returns the count of remaining cards in the player's influence deck.
     *
     * @param player the player
     * @return size of influence deck
     */
    public int getInfluenceDeckCount(Player player) {
        return playerInfluenceDecks.get(player).size();
    }

    /**
     * Returns the count of remaining cards in the player's action deck.
     *
     * @param player the player
     * @return size of action deck
     */
    public int getActionDeckCount(Player player) {
        return playerActionDecks.get(player).size();
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

    public void handleVeto(Player p, ActionCard action, boolean drewFromInfluence) {
        // Discard the player's played action card
        playerHands.get(p).remove(action);
        // Remove the VETO card from the opponent's hand
        List<Card> opponentHand = playerHands.get(p.opponent());
        Optional<ActionCard> opponentAction = opponentHand.stream()
            .filter(c -> c instanceof ActionCard)
            .map( c -> (ActionCard) c)
            .filter(ac -> ac.type().equals(ActionCard.Type.VETO))
            .findFirst();
        opponentAction.ifPresent(c -> {
            opponentHand.remove(c);
            discard(c);
        });
        // Draw a replacement card for the vetoing player
        draw(p, drewFromInfluence);
    }

    // ========== TURN MECHANICS ==========

    /**
     * Plays a card during the current turn phase.
     * For influence cards, the face-up behavior depends on the phase.
     * For action cards, they are executed immediately.
     *
     * @param cardId the ID of the card to play
     * @param patricianType the patrician type to target (for influence cards)
     * @param actionContext additional context for action cards (optional)
     * @return true if the card was successfully played
     */
    public boolean playCardInTurn(String cardId, PatricianCard.Type patricianType, ActionContext actionContext) {
        if (currentMode != GameMode.STANDARD_PLAY) {
            throw new IllegalStateException("Not in standard play mode");
        }
        
        if (currentTurnPhase == TurnPhase.VOTE_OF_CONFIDENCE || currentTurnPhase == TurnPhase.DRAW_PHASE) {
            throw new IllegalStateException("Cannot play cards during " + currentTurnPhase + " phase");
        }
        
        // Find the card in the current player's hand
        List<Card> hand = playerHands.get(currentPlayer);
        Card cardToPlay = hand.stream()
            .filter(card -> card.id().equals(cardId))
            .findFirst()
            .orElse(null);
            
        if (cardToPlay == null) {
            return false; // Card not found in hand
        }
        
        // Check if we can play another card this turn
        if (cardsPlayedThisTurn >= 2) {
            return false; // Already played maximum cards this turn
        }
        
        // Handle influence cards
        if (cardToPlay instanceof InfluenceCard influenceCard) {
            return playInfluenceCardInTurn(influenceCard, patricianType);
        }
        
        // Handle action cards
        if (cardToPlay instanceof ActionCard actionCard) {
            return playActionCardInTurn(actionCard, actionContext);
        }
        
        return false;
    }
    
    /**
     * Plays an influence card during the turn.
     * First phase: face down
     * Second phase: face up
     */
    private boolean playInfluenceCardInTurn(InfluenceCard influenceCard, PatricianCard.Type patricianType) {
        boolean faceUp = (currentTurnPhase == TurnPhase.SECOND_CARD_SELECTION);
        
        // Check if we can add influence to this patrician type
        if (!patricianState.addInfluence(patricianType, currentPlayer, influenceCard, faceUp)) {
            return false; // Cannot add more influence cards to this patrician
        }
        
        // Remove card from hand
        playerHands.get(currentPlayer).removeIf(card ->
            card instanceof InfluenceCard ic && ic.id().equals(influenceCard.id()));
        
        cardsPlayedThisTurn++;
        
        // Advance to next phase if this was the first card
        if (currentTurnPhase == TurnPhase.FIRST_CARD_SELECTION) {
            currentTurnPhase = TurnPhase.SECOND_CARD_SELECTION;
        }
        
        return true;
    }
    
    /**
     * Plays an action card during the turn.
     */
    private boolean playActionCardInTurn(ActionCard actionCard, ActionContext actionContext) {
        // Check if we already played an action card this turn
        if (actionCardPlayedThisTurn) {
            return false; // Can only play one action card per turn
        }
        
        // Store the action card and context for potential veto
        pendingActionCard = actionCard;
        pendingActionContext = actionContext;
        
        // Remove card from hand
        playerHands.get(currentPlayer).removeIf(card ->
            card instanceof ActionCard ac && ac.id().equals(actionCard.id()));
        
        cardsPlayedThisTurn++;
        actionCardPlayedThisTurn = true;
        
        // Execute the action immediately (can be vetoed by opponent)
        if (actionContext != null) {
            actionCard.apply(this, currentPlayer, actionContext);
        }
        
        // Discard the action card
        discard(actionCard);
        
        // Advance to next phase if this was the first card
        if (currentTurnPhase == TurnPhase.FIRST_CARD_SELECTION) {
            currentTurnPhase = TurnPhase.SECOND_CARD_SELECTION;
        }
        
        return true;
    }
    
    /**
     * Skips the second card selection phase and moves to vote of confidence.
     */
    public void skipSecondCardSelection() {
        if (currentMode != GameMode.STANDARD_PLAY) {
            throw new IllegalStateException("Not in standard play mode");
        }
        
        if (currentTurnPhase != TurnPhase.SECOND_CARD_SELECTION) {
            throw new IllegalStateException("Can only skip during second card selection phase");
        }
        
        currentTurnPhase = TurnPhase.VOTE_OF_CONFIDENCE;
    }
    
    /**
     * Advances to the vote of confidence phase.
     * This is called automatically after playing cards or when skipping second card.
     */
    public void proceedToVoteOfConfidence() {
        if (currentMode != GameMode.STANDARD_PLAY) {
            throw new IllegalStateException("Not in standard play mode");
        }
        
        currentTurnPhase = TurnPhase.VOTE_OF_CONFIDENCE;
    }
    
    /**
     * Executes a vote of confidence for the specified patrician type.
     *
     * @param patricianType the patrician type to vote on
     * @return the winner of the vote, or null if tie
     */
    public Player executeVoteOfConfidence(PatricianCard.Type patricianType) {
        if (currentMode != GameMode.STANDARD_PLAY) {
            throw new IllegalStateException("Not in standard play mode");
        }
        
        if (currentTurnPhase != TurnPhase.VOTE_OF_CONFIDENCE) {
            throw new IllegalStateException("Not in vote of confidence phase");
        }
        
        Player winner = resolveVoteOfConfidence(patricianType);
        
        // Advance to draw phase
        currentTurnPhase = TurnPhase.DRAW_PHASE;
        
        return winner;
    }
    
    /**
     * Draws cards to fill hand to 6 cards from the specified deck type.
     *
     * @param fromInfluenceDeck true to draw from influence deck, false for action deck
     */
    public void drawToHandLimit(boolean fromInfluenceDeck) {
        if (currentMode != GameMode.STANDARD_PLAY) {
            throw new IllegalStateException("Not in standard play mode");
        }
        
        if (currentTurnPhase != TurnPhase.DRAW_PHASE) {
            throw new IllegalStateException("Not in draw phase");
        }
        
        // Draw cards until hand is full or deck is empty
        List<Card> hand = playerHands.get(currentPlayer);
        while (hand.size() < MAX_HAND_SIZE) {
            Card drawn = draw(currentPlayer, fromInfluenceDeck);
            if (drawn == null) {
                // Deck is empty, cannot draw more
                break;
            }
        }
        
        // End the turn
        endTurn();
    }
    
    /**
     * Ends the current player's turn and switches to the opponent.
     */
    private void endTurn() {
        // Reset turn state
        cardsPlayedThisTurn = 0;
        actionCardPlayedThisTurn = false;
        pendingActionCard = null;
        pendingActionContext = null;
        
        // Switch to opponent
        currentPlayer = currentPlayer.opponent();
        currentTurnPhase = TurnPhase.FIRST_CARD_SELECTION;
    }
    
    /**
     * Checks if the current player can play more cards this turn.
     */
    public boolean canPlayMoreCards() {
        return currentMode == GameMode.STANDARD_PLAY &&
               (currentTurnPhase == TurnPhase.FIRST_CARD_SELECTION ||
                currentTurnPhase == TurnPhase.SECOND_CARD_SELECTION) &&
               cardsPlayedThisTurn < 2;
    }
    
    /**
     * Gets the number of cards played by the current player this turn.
     */
    public int getCardsPlayedThisTurn() {
        return cardsPlayedThisTurn;
    }
    
    /**
     * Checks if an action card was played this turn.
     */
    public boolean wasActionCardPlayedThisTurn() {
        return actionCardPlayedThisTurn;
    }
    
    /**
     * Gets the pending action card (for veto purposes).
     */
    public ActionCard getPendingActionCard() {
        return pendingActionCard;
    }
    
    /**
     * Gets the pending action context (for veto purposes).
     */
    public ActionContext getPendingActionContext() {
        return pendingActionContext;
    }
}
