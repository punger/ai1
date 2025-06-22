package com.example.caesarandcleopatra.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TurnMechanicsTest {
    
    private Game game;
    
    @BeforeEach
    void setUp() {
        game = new Game(42); // Fixed seed for reproducible tests
        
        // Complete initial influence placement to get to standard play
        game.completeInitialInfluencePlacement(); // Caesar places initial influence
        game.completeInitialInfluencePlacement(); // Cleopatra places initial influence
        
        // Should now be in standard play with Caesar's turn
        assertEquals(Game.GameMode.STANDARD_PLAY, game.getCurrentMode());
        assertEquals(Player.CAESAR, game.getCurrentPlayer());
        assertEquals(Game.TurnPhase.FIRST_CARD_SELECTION, game.getCurrentTurnPhase());
    }
    
    @Test
    void testFirstCardSelection_InfluenceCard() {
        // Get Caesar's starting hand
        var hand = game.getPlayerHands().get(Player.CAESAR);
        assertFalse(hand.isEmpty());
        
        // Find an influence card
        InfluenceCard influenceCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .findFirst()
            .orElse(null);
        
        assertNotNull(influenceCard);
        
        // Play influence card face down (first phase)
        boolean success = game.playCardInTurn(influenceCard.id(), PatricianCard.Type.AEDILE, null);
        assertTrue(success);
        
        // Should now be in second card selection phase
        assertEquals(Game.TurnPhase.SECOND_CARD_SELECTION, game.getCurrentTurnPhase());
        assertEquals(1, game.getCardsPlayedThisTurn());
        
        // Verify card was played face down
        var playedCards = game.getPatricianState().getPlayedInfluence(PatricianCard.Type.AEDILE, Player.CAESAR);
        assertEquals(1, playedCards.size());
        assertFalse(playedCards.get(0).isFaceUp()); // Should be face down in first phase
    }
    
    @Test
    void testSecondCardSelection_InfluenceCard() {
        // Play first card
        var hand = game.getPlayerHands().get(Player.CAESAR);
        InfluenceCard firstCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .findFirst()
            .orElse(null);
        
        game.playCardInTurn(firstCard.id(), PatricianCard.Type.AEDILE, null);
        
        // Now play second card (should be face up)
        hand = game.getPlayerHands().get(Player.CAESAR); // Refresh hand
        InfluenceCard secondCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .findFirst()
            .orElse(null);
        
        assertNotNull(secondCard);
        
        boolean success = game.playCardInTurn(secondCard.id(), PatricianCard.Type.AEDILE, null);
        assertTrue(success);
        
        assertEquals(2, game.getCardsPlayedThisTurn());
        
        // Verify second card was played face up
        var playedCards = game.getPatricianState().getPlayedInfluence(PatricianCard.Type.AEDILE, Player.CAESAR);
        assertEquals(2, playedCards.size());
        assertTrue(playedCards.get(1).isFaceUp()); // Second card should be face up
    }
    
    @Test
    void testActionCardPlay() {
        // Get Caesar's starting hand
        var hand = game.getPlayerHands().get(Player.CAESAR);
        
        // Find the veto action card (should be in starting hand)
        ActionCard actionCard = hand.stream()
            .filter(card -> card instanceof ActionCard)
            .map(card -> (ActionCard) card)
            .findFirst()
            .orElse(null);
        
        assertNotNull(actionCard);
        assertEquals(ActionCard.Type.VETO, actionCard.type());
        
        // Play action card
        boolean success = game.playCardInTurn(actionCard.id(), null, new ActionContext());
        assertTrue(success);
        
        // Should advance to second card selection phase
        assertEquals(Game.TurnPhase.SECOND_CARD_SELECTION, game.getCurrentTurnPhase());
        assertEquals(1, game.getCardsPlayedThisTurn());
        assertTrue(game.wasActionCardPlayedThisTurn());
    }
    
    @Test
    void testSkipSecondCardSelection() {
        // Play first card
        var hand = game.getPlayerHands().get(Player.CAESAR);
        InfluenceCard firstCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .findFirst()
            .orElse(null);
        
        game.playCardInTurn(firstCard.id(), PatricianCard.Type.AEDILE, null);
        assertEquals(Game.TurnPhase.SECOND_CARD_SELECTION, game.getCurrentTurnPhase());
        
        // Skip second card selection
        game.skipSecondCardSelection();
        assertEquals(Game.TurnPhase.VOTE_OF_CONFIDENCE, game.getCurrentTurnPhase());
    }
    
    @Test
    void testVoteOfConfidencePhase() {
        // Play a card and proceed to vote phase
        var hand = game.getPlayerHands().get(Player.CAESAR);
        InfluenceCard firstCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .findFirst()
            .orElse(null);
        
        game.playCardInTurn(firstCard.id(), PatricianCard.Type.AEDILE, null);
        game.skipSecondCardSelection();
        
        assertEquals(Game.TurnPhase.VOTE_OF_CONFIDENCE, game.getCurrentTurnPhase());
        
        // Execute vote of confidence
        Player winner = game.executeVoteOfConfidence(PatricianCard.Type.AEDILE);
        // Winner could be null if it's a tie, but phase should advance
        assertEquals(Game.TurnPhase.DRAW_PHASE, game.getCurrentTurnPhase());
    }
    
    @Test
    void testDrawPhase() {
        // Get to draw phase
        var hand = game.getPlayerHands().get(Player.CAESAR);
        InfluenceCard firstCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .findFirst()
            .orElse(null);
        
        game.playCardInTurn(firstCard.id(), PatricianCard.Type.AEDILE, null);
        game.skipSecondCardSelection();
        game.executeVoteOfConfidence(PatricianCard.Type.AEDILE);
        
        assertEquals(Game.TurnPhase.DRAW_PHASE, game.getCurrentTurnPhase());
        
        int handSizeBeforeDraw = game.getPlayerHands().get(Player.CAESAR).size();
        
        // Draw to hand limit from influence deck
        game.drawToHandLimit(true);
        
        // Should now be Cleopatra's turn
        assertEquals(Player.CLEOPATRA, game.getCurrentPlayer());
        assertEquals(Game.TurnPhase.FIRST_CARD_SELECTION, game.getCurrentTurnPhase());
        assertEquals(0, game.getCardsPlayedThisTurn()); // Reset for new turn
        
        // Hand should be filled up to 6 cards
        int finalHandSize = game.getPlayerHands().get(Player.CAESAR).size();
        assertTrue(finalHandSize <= Game.MAX_HAND_SIZE);
        assertTrue(finalHandSize >= handSizeBeforeDraw); // Should have drawn at least same or more
    }
    
    @Test
    void testCannotPlayMoreThanTwoCards() {
        // Play two cards
        var hand = game.getPlayerHands().get(Player.CAESAR);
        
        InfluenceCard firstCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .skip(0)
            .findFirst()
            .orElse(null);
        
        InfluenceCard secondCard = hand.stream()
            .filter(card -> card instanceof InfluenceCard)
            .map(card -> (InfluenceCard) card)
            .skip(1)
            .findFirst()
            .orElse(null);
        
        game.playCardInTurn(firstCard.id(), PatricianCard.Type.AEDILE, null);
        game.playCardInTurn(secondCard.id(), PatricianCard.Type.AEDILE, null);
        
        assertEquals(2, game.getCardsPlayedThisTurn());
        assertFalse(game.canPlayMoreCards());
        
        // Try to play a third card - should fail
        hand = game.getPlayerHands().get(Player.CAESAR); // Refresh hand
        if (!hand.isEmpty()) {
            Card thirdCard = hand.get(0);
            boolean success = game.playCardInTurn(thirdCard.id(), PatricianCard.Type.AEDILE, null);
            assertFalse(success); // Should not be able to play third card
        }
    }
}