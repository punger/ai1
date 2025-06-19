package com.example.caesarandcleopatra;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

public class GameResourceTest {

    @Test
    public void testGetGame() throws Exception {
        GameResource gameResource = new GameResource();
        var gameState = gameResource.getGame();

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(gameState);

        System.out.println("GameState JSON:\n" + json);
    }

    @Test
    public void testPlaceInitialInfluence() throws Exception {
        GameResource gameResource = new GameResource();
        
        // Prepare test data - placing influence cards on different patrician groups
        Map<String, String> influenceCards = new HashMap<>();
        // Map patrician types to influence card types
        influenceCards.put("PRAETOR", "ONE");
        influenceCards.put("AEDILE", "TWO");
        influenceCards.put("CONSUL", "THREE");
        
        // Call the method with CAESAR as the player
        Response response = gameResource.placeInitialInfluence("CAESAR", influenceCards);
        
        // Extract the response entity
        Object entity = response.getEntity();
        
        // Pretty print the response
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        System.out.println("\n=== placeInitialInfluence Response ===");
        System.out.println("Status Code: " + response.getStatus());
        System.out.println("Response Entity: " + entity);
        
        // If you want to see the game state after placing influence
        System.out.println("\n=== Game State After Placing Initial Influence ===");
        var gameState = gameResource.getGame();
        String gameStateJson = mapper.writeValueAsString(gameState);
        System.out.println(gameStateJson);
    }

    @Test
    public void testOpponentCardsHiddenDuringInitialInfluence() throws Exception {
        GameResource gameResource = new GameResource();
        
        // First, Caesar places initial influence
        Map<String, String> caesarInfluence = new HashMap<>();
        caesarInfluence.put("PRAETOR", "ONE");
        caesarInfluence.put("AEDILE", "TWO");
        caesarInfluence.put("CONSUL", "THREE");
        
        Response caesarResponse = gameResource.placeInitialInfluence("CAESAR", caesarInfluence);
        System.out.println("Caesar placement status: " + caesarResponse.getStatus());
        
        // Now it should be Cleopatra's turn - check game state from Cleopatra's perspective
        var gameState = gameResource.getGame();
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        System.out.println("\n=== Game State During Cleopatra's Initial Influence Turn ===");
        System.out.println("Current Player: " + gameState.currentPlayer());
        System.out.println("Game Mode: " + gameState.gameMode());
        System.out.println("Waiting for Initial Influence: " + gameState.waitingForInitialInfluence());
        
        // Check that Caesar's cards are hidden in the patrician board
        var patricianBoard = gameState.patricianBoard();
        
        // Check PRAETOR group where Caesar placed a card
        var praetorEntry = patricianBoard.get("PRAETOR");
        var caesarInfluenceOnPraetor = praetorEntry.influence().get(com.example.caesarandcleopatra.model.Player.CAESAR);
        
        System.out.println("\n=== Caesar's Influence on PRAETOR (should be hidden) ===");
        for (var influence : caesarInfluenceOnPraetor) {
            System.out.println("Face Up: " + influence.faceUp() + ", Type: " + influence.type());
            // Verify that the card type is "hidden" and not face up
            assert influence.type().equals("hidden") : "Caesar's card should be hidden, but was: " + influence.type();
            assert !influence.faceUp() : "Caesar's card should not be face up during initial placement";
        }
        
        // Place Cleopatra's initial influence
        Map<String, String> cleopatraInfluence = new HashMap<>();
        cleopatraInfluence.put("CENSOR", "FOUR");
        cleopatraInfluence.put("QUAESTOR", "FIVE");
        
        Response cleopatraResponse = gameResource.placeInitialInfluence("CLEOPATRA", cleopatraInfluence);
        System.out.println("Cleopatra placement status: " + cleopatraResponse.getStatus());
        
        // Now both players should have completed initial influence - check final game state
        var finalGameState = gameResource.getGame();
        System.out.println("\n=== Final Game State After Both Players Complete Initial Influence ===");
        System.out.println("Current Player: " + finalGameState.currentPlayer());
        System.out.println("Game Mode: " + finalGameState.gameMode());
        System.out.println("Waiting for Initial Influence: " + finalGameState.waitingForInitialInfluence());
        
        // In standard play mode, cards should be visible with their actual IDs
        var finalPatricianBoard = finalGameState.patricianBoard();
        var finalPraetorEntry = finalPatricianBoard.get("PRAETOR");
        var finalCaesarInfluence = finalPraetorEntry.influence().get(com.example.caesarandcleopatra.model.Player.CAESAR);
        
        System.out.println("\n=== Caesar's Influence on PRAETOR (should be visible now) ===");
        for (var influence : finalCaesarInfluence) {
            System.out.println("Face Up: " + influence.faceUp() + ", Type: " + influence.type());
            // During standard play, the actual card ID should be visible
            assert !influence.type().equals("hidden") : "Caesar's card should be visible in standard play, but was hidden";
        }
        
        String finalGameStateJson = mapper.writeValueAsString(finalGameState);
        System.out.println("\nFinal Game State JSON:\n" + finalGameStateJson);
    }
   
}