package com.example.caesarandcleopatra;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

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
}