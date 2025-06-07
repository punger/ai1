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

   
}