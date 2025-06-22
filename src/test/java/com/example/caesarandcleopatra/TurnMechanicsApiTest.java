package com.example.caesarandcleopatra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import com.example.caesarandcleopatra.model.Player;

import jakarta.ws.rs.core.Response;

public class TurnMechanicsApiTest {
    
    private GameResource gameResource;
    
    @BeforeEach
    void setUp() {
        gameResource = new GameResource();
        
        // Complete initial influence placement to get to standard play using the actual API
        Map<String, String> caesarInfluence = new HashMap<>();
        caesarInfluence.put("AEDILE", "ONE");
        caesarInfluence.put("QUAESTOR", "TWO");
        caesarInfluence.put("PRAETOR", "THREE");
        caesarInfluence.put("CONSUL", "FOUR");
        caesarInfluence.put("CENSOR", "FIVE");
        
        Response caesarResponse = gameResource.placeInitialInfluence("CAESAR", caesarInfluence);
        assertEquals(Response.Status.OK.getStatusCode(), caesarResponse.getStatus());
        
        Map<String, String> cleopatraInfluence = new HashMap<>();
        cleopatraInfluence.put("AEDILE", "ONE");
        cleopatraInfluence.put("QUAESTOR", "TWO");
        cleopatraInfluence.put("PRAETOR", "THREE");
        cleopatraInfluence.put("CONSUL", "FOUR");
        cleopatraInfluence.put("CENSOR", "FIVE");
        
        Response cleopatraResponse = gameResource.placeInitialInfluence("CLEOPATRA", cleopatraInfluence);
        assertEquals(Response.Status.OK.getStatusCode(), cleopatraResponse.getStatus());
        
        // Should now be in standard play with Caesar's turn
        GameResource.GameState gameState = gameResource.getGame();
        assertEquals("STANDARD_PLAY", gameState.gameMode());
        assertEquals(Player.CAESAR, gameState.currentPlayer());
        assertEquals("FIRST_CARD_SELECTION", gameState.turnPhase());
    }
    
    @Test
    void testPlayInfluenceCardFaceDown() {
        GameResource.GameState gameState = gameResource.getGame();
        assertFalse(gameState.currentPlayerHand().isEmpty());
        
        // Get first influence card from Caesar's hand
        String cardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
        
        assertNotNull(cardId);
        
        // Play influence card face down only
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        
        Map<String, Object> faceDownAssignment = new HashMap<>();
        faceDownAssignment.put("influenceCardId", cardId);
        faceDownAssignment.put("patricianType", "AEDILE");
        playRequest.put("faceDownAssignment", faceDownAssignment);
        
        Response response = gameResource.playInfluenceCard(playRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Check updated game state - response should contain the full GameState
        GameResource.GameState updatedState = (GameResource.GameState) response.getEntity();
        assertNotNull(updatedState);
        assertEquals(Player.CAESAR, updatedState.currentPlayer());
        assertEquals("SECOND_CARD_SELECTION", updatedState.turnPhase());
        assertEquals(1, updatedState.cardsPlayedThisTurn());
        assertTrue(updatedState.canPlayMoreCards());
    }
    
    @Test
    void testPlayTwoInfluenceCards() {
        GameResource.GameState gameState = gameResource.getGame();
        
        // Get two influence cards from Caesar's hand
        String firstCardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .skip(0)
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
            
        String secondCardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .skip(1)
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
        
        assertNotNull(firstCardId);
        assertNotNull(secondCardId);
        
        // Play both cards in one request - first face down, second face up
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        
        Map<String, Object> faceDownAssignment = new HashMap<>();
        faceDownAssignment.put("influenceCardId", firstCardId);
        faceDownAssignment.put("patricianType", "AEDILE");
        playRequest.put("faceDownAssignment", faceDownAssignment);
        
        Map<String, Object> faceUpAssignment = new HashMap<>();
        faceUpAssignment.put("influenceCardId", secondCardId);
        faceUpAssignment.put("patricianType", "PRAETOR");
        playRequest.put("faceUpAssignment", faceUpAssignment);
        
        Response response = gameResource.playInfluenceCard(playRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Check that both cards were played - should advance to vote phase
        GameResource.GameState updatedState = (GameResource.GameState) response.getEntity();
        assertNotNull(updatedState);
        assertEquals(Player.CAESAR, updatedState.currentPlayer());
        assertEquals("SECOND_CARD_SELECTION", updatedState.turnPhase());
        assertEquals(2, updatedState.cardsPlayedThisTurn());
        assertFalse(updatedState.canPlayMoreCards()); // Can't play more after 2 cards
    }
    
    @Test
    void testProceedToVoteOfConfidence() {
        // Play first card using the API
        GameResource.GameState gameState = gameResource.getGame();
        String cardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
        
        assertNotNull(cardId);
        
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        
        Map<String, Object> faceDownAssignment = new HashMap<>();
        faceDownAssignment.put("influenceCardId", cardId);
        faceDownAssignment.put("patricianType", "AEDILE");
        playRequest.put("faceDownAssignment", faceDownAssignment);
        
        Response playResponse = gameResource.playInfluenceCard(playRequest);
        assertEquals(Response.Status.OK.getStatusCode(), playResponse.getStatus());
        
        // Proceed directly to vote of confidence
        Map<String, Object> skipRequest = new HashMap<>();
        skipRequest.put("playerId", "CAESAR");
        
        Response response = gameResource.proceedToVoteOfConfidence(skipRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Check game state after proceeding to vote
        gameState = gameResource.getGame();
        assertEquals("VOTE_OF_CONFIDENCE", gameState.turnPhase());
        assertEquals(Player.CAESAR, gameState.currentPlayer());
    }
    
    @Test
    void testVoteOfConfidence() {
        // Play a card and skip to vote phase
        GameResource.GameState gameState = gameResource.getGame();
        String cardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
        
        assertNotNull(cardId);
        
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        
        Map<String, Object> faceDownAssignment = new HashMap<>();
        faceDownAssignment.put("influenceCardId", cardId);
        faceDownAssignment.put("patricianType", "AEDILE");
        playRequest.put("faceDownAssignment", faceDownAssignment);
        
        Response playResponse = gameResource.playInfluenceCard(playRequest);
        assertEquals(Response.Status.OK.getStatusCode(), playResponse.getStatus());
        
        Map<String, Object> skipRequest = new HashMap<>();
        skipRequest.put("playerId", "CAESAR");
        Response skipResponse = gameResource.proceedToVoteOfConfidence(skipRequest);
        assertEquals(Response.Status.OK.getStatusCode(), skipResponse.getStatus());
        
        // Execute vote of confidence
        Map<String, Object> voteRequest = new HashMap<>();
        voteRequest.put("playerId", "CAESAR");
        voteRequest.put("patricianType", "AEDILE");
        
        Response response = gameResource.executeVoteOfConfidence(voteRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Check game state after vote
        gameState = gameResource.getGame();
        assertEquals("DRAW_PHASE", gameState.turnPhase());
        assertEquals(Player.CAESAR, gameState.currentPlayer());
    }
    
    @Test
    void testDrawPhaseAndTurnEnd() {
        // Get to draw phase by playing card, skipping, and voting
        GameResource.GameState gameState = gameResource.getGame();
        String cardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
        
        assertNotNull(cardId);
        
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        
        Map<String, Object> faceDownAssignment = new HashMap<>();
        faceDownAssignment.put("influenceCardId", cardId);
        faceDownAssignment.put("patricianType", "AEDILE");
        playRequest.put("faceDownAssignment", faceDownAssignment);
        
        Response playResponse = gameResource.playInfluenceCard(playRequest);
        assertEquals(Response.Status.OK.getStatusCode(), playResponse.getStatus());
        
        Map<String, Object> skipRequest = new HashMap<>();
        skipRequest.put("playerId", "CAESAR");
        Response skipResponse = gameResource.proceedToVoteOfConfidence(skipRequest);
        assertEquals(Response.Status.OK.getStatusCode(), skipResponse.getStatus());
        
        Map<String, Object> voteRequest = new HashMap<>();
        voteRequest.put("playerId", "CAESAR");
        voteRequest.put("patricianType", "AEDILE");
        Response voteResponse = gameResource.executeVoteOfConfidence(voteRequest);
        assertEquals(Response.Status.OK.getStatusCode(), voteResponse.getStatus());
        
        // Draw cards to end turn
        Map<String, Object> drawRequest = new HashMap<>();
        drawRequest.put("playerId", "CAESAR");
        drawRequest.put("fromInfluenceDeck", true);
        
        Response response = gameResource.drawCards(drawRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Check that it's now Cleopatra's turn
        gameState = gameResource.getGame();
        assertEquals(Player.CLEOPATRA, gameState.currentPlayer());
        assertEquals("FIRST_CARD_SELECTION", gameState.turnPhase());
        assertEquals(0, gameState.cardsPlayedThisTurn());
    }
    
    @Test
    void testPlayActionCard() {
        GameResource.GameState gameState = gameResource.getGame();
        
        // Verify Caesar has an action card in hand (should have VETO from setup)
        boolean hasActionCard = gameState.currentPlayerHand().stream()
            .anyMatch(card -> "action".equals(card.type()));
        assertTrue(hasActionCard, "Caesar should have an action card in hand");
        
        // Play action card using the API - note: current implementation is a placeholder
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        playRequest.put("actionCardName", "VETO");
        
        // Create simple action effect for testing
        Map<String, Object> actionEffect = new HashMap<>();
        actionEffect.put("type", "VETO");
        playRequest.put("actionEffect", actionEffect);
        
        Response response = gameResource.playActionCard(playRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Check that response contains updated game state
        GameResource.GameState updatedState = (GameResource.GameState) response.getEntity();
        assertNotNull(updatedState);
        assertEquals(Player.CAESAR, updatedState.currentPlayer());
        // Note: Current implementation doesn't actually modify game state meaningfully
    }
    
    @Test
    void testWrongPlayerTurn() {
        // Try to play card as Cleopatra when it's Caesar's turn
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CLEOPATRA");
        
        Map<String, Object> faceDownAssignment = new HashMap<>();
        faceDownAssignment.put("influenceCardId", "one");
        faceDownAssignment.put("patricianType", "AEDILE");
        playRequest.put("faceDownAssignment", faceDownAssignment);
        
        Response response = gameResource.playInfluenceCard(playRequest);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }
    
    @Test
    void testCardLimitBehavior() {
        GameResource.GameState gameState = gameResource.getGame();
        
        // Play first card separately
        String firstCardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .skip(0)
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
        
        assertNotNull(firstCardId);
        
        Map<String, Object> firstPlay = new HashMap<>();
        firstPlay.put("playerId", "CAESAR");
        
        Map<String, Object> faceDownAssignment = new HashMap<>();
        faceDownAssignment.put("influenceCardId", firstCardId);
        faceDownAssignment.put("patricianType", "AEDILE");
        firstPlay.put("faceDownAssignment", faceDownAssignment);
        
        Response firstResponse = gameResource.playInfluenceCard(firstPlay);
        assertEquals(Response.Status.OK.getStatusCode(), firstResponse.getStatus());
        
        // Check that we're now in second card selection phase
        gameState = gameResource.getGame();
        assertEquals("SECOND_CARD_SELECTION", gameState.turnPhase());
        assertEquals(1, gameState.cardsPlayedThisTurn());
        assertTrue(gameState.canPlayMoreCards());
        
        // Play second card separately
        String secondCardId = gameState.currentPlayerHand().stream()
            .filter(card -> "influence".equals(card.type()))
            .findFirst()
            .map(GameResource.CardDTO::id)
            .orElse(null);
        
        if (secondCardId != null) {
            Map<String, Object> secondPlay = new HashMap<>();
            secondPlay.put("playerId", "CAESAR");
            
            Map<String, Object> secondFaceDownAssignment = new HashMap<>();
            secondFaceDownAssignment.put("influenceCardId", secondCardId);
            secondFaceDownAssignment.put("patricianType", "PRAETOR");
            secondPlay.put("faceDownAssignment", secondFaceDownAssignment);
            
            Response secondResponse = gameResource.playInfluenceCard(secondPlay);
            assertEquals(Response.Status.OK.getStatusCode(), secondResponse.getStatus());
            
            // After playing 2 cards, should not be able to play more
            gameState = gameResource.getGame();
            assertEquals(2, gameState.cardsPlayedThisTurn());
            assertFalse(gameState.canPlayMoreCards());
        }
    }
    
    @Test
    void testActionCardWithCustomEffect() {
        // Test the action card API with custom effects
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        playRequest.put("actionCardName", "SCOUT");
        
        // Create custom action effect with parameters
        Map<String, Object> actionEffect = new HashMap<>();
        actionEffect.put("type", "SCOUT");
        actionEffect.put("targetPatrician", "AEDILE");
        playRequest.put("actionEffect", actionEffect);
        
        Response response = gameResource.playActionCard(playRequest);
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        
        // Verify response contains updated game state
        GameResource.GameState updatedState = (GameResource.GameState) response.getEntity();
        assertNotNull(updatedState);
        assertEquals(Player.CAESAR, updatedState.currentPlayer());
    }
    
    @Test
    void testInfluenceCardMissingRequiredAssignment() {
        // Test that missing face down assignment fails
        Map<String, Object> playRequest = new HashMap<>();
        playRequest.put("playerId", "CAESAR");
        // Missing faceDownAssignment
        
        Response response = gameResource.playInfluenceCard(playRequest);
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }
}