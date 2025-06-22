package com.example.caesarandcleopatra;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.example.caesarandcleopatra.model.BustBag.BustPiece;
import com.example.caesarandcleopatra.model.PatricianState.InfluenceCardState;

import java.util.*;
import java.util.stream.Collectors;

import com.example.caesarandcleopatra.model.*;

import jakarta.enterprise.context.ApplicationScoped;

@Path("/game")
@ApplicationScoped
public class GameResource {

    private Game persistentGame;

    public GameResource() {
        this.persistentGame = new Game();
    }

    // GET /api/game - Retrieves the full game state (initialization or save/retrieve)
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public GameState getGame() {
        if (persistentGame == null) {
            persistentGame = new Game();
        }
        Player current = persistentGame.getCurrentPlayer();
        // Map each card to DTO with id and simple type identifier
        List<CardDTO> currentHand = persistentGame.getPlayerHands().get(current).stream()
            .map(c -> {
                if (c instanceof InfluenceCard ic) return new CardDTO(ic.id(), "influence");
                if (c instanceof ActionCard ac)   return new CardDTO(ac.id(), "action");
                return new CardDTO(c.id(), "unknown");
            })
            .collect(Collectors.toList());
        int opponentHandCount = persistentGame.getPlayerHands().get(current.opponent()).size();
        Map<Player, DeckCount> deckCounts = Map.of(
            current, new DeckCount(persistentGame.getInfluenceDeckCount(current), persistentGame.getActionDeckCount(current)),
            current.opponent(), new DeckCount(persistentGame.getInfluenceDeckCount(current.opponent()), persistentGame.getActionDeckCount(current.opponent()))
        );
        List<String> bustBagContents = persistentGame.getBustBag().stream().map(BustPiece::name).collect(Collectors.toList());
        // Build per-group board entries with remaining counts and played influence
        Map<String, PatricianBoardEntry> patricianBoard = new HashMap<>();
        PatricianState patricianState = persistentGame.getPatricianState();
        Map<PatricianCard.Type, Integer> boardState = patricianState.getBoardState();
        boolean isInitialInfluencePlacement = persistentGame.getCurrentMode() == Game.GameMode.INITIAL_INFLUENCE_PLACEMENT;
        
        for (var type : PatricianCard.Type.values()) {
            int remaining = boardState.getOrDefault(type, 0);
            Map<Player, List<InfluenceEntry>> influenceMap = new HashMap<>();
            for (Player player : Player.values()) {
                List<InfluenceCardState> influenceList = patricianState.getPlayedInfluence(type, player);
                List<InfluenceEntry> entries = new ArrayList<>();
                
                // During initial influence placement, hide opponent's cards completely
                if (isInitialInfluencePlacement && player != current) {
                    // Show only the count of opponent's cards, but hide their details
                    for (int i = 0; i < influenceList.size(); i++) {
                        entries.add(new InfluenceEntry(false, "hidden"));
                    }
                } else {
                    // Show current player's cards or all cards during standard play
                    for (PatricianState.InfluenceCardState inf : influenceList) {
                        entries.add(new InfluenceEntry(inf.isFaceUp(), inf.getCard().id()));
                    }
                }
                influenceMap.put(player, entries);
            }
            patricianBoard.put(type.toString(), new PatricianBoardEntry(remaining, influenceMap));
        }
        // Convert player patrician counts to map of string keys
        Map<Player, Map<String,Integer>> playerPatricianCards = new HashMap<>();
        for (Map.Entry<Player, ?> entry : persistentGame.getPlayerPatricianCounts().entrySet()) {
            Player player = entry.getKey();
            Map<?, Integer> countsMap = (Map<?, Integer>) entry.getValue();
            Map<String, Integer> m2 = new HashMap<>();
            countsMap.forEach((cardType, count) -> m2.put(cardType.toString(), count));
            playerPatricianCards.put(player, m2);
        }
        return new GameState(current, 1, currentHand, opponentHandCount,
                             playerPatricianCards, bustBagContents,
                             deckCounts, patricianBoard,
                             persistentGame.getCurrentMode().toString(),
                             persistentGame.isWaitingForInitialInfluence(),
                             persistentGame.getCurrentMode() == Game.GameMode.STANDARD_PLAY ?
                                 persistentGame.getCurrentTurnPhase().toString() : null,
                             persistentGame.getCardsPlayedThisTurn(),
                             persistentGame.canPlayMoreCards());
    }

    // POST /api/game/action/placeInitialInfluence - Submits initial face-down influence card placements
    @POST
    @Path("/action/placeInitialInfluence")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response placeInitialInfluence(
        @QueryParam("playerId") String playerId,
        Map<String, String> influenceCards)
    {
        Player current = resolvePlayer(playerId);
        
        // Validate that it's this player's turn during initial placement
        if (persistentGame.getCurrentMode() != Game.GameMode.INITIAL_INFLUENCE_PLACEMENT) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"Not in initial influence placement mode\"}")
                .build();
        }
        
        if (persistentGame.getCurrentPlayer() != current) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"Not your turn\"}")
                .build();
        }
        
        if (persistentGame.hasPlayerPlacedInitialInfluence(current)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"Player has already placed initial influence\"}")
                .build();
        }

        persistentGame.setHand(current, InfluenceCard.getByValue(1, 2, 3, 4, 5));
        // Place the influence cards
        for (Map.Entry<String, String> entry : influenceCards.entrySet()) {
            String patricianGroupId = entry.getKey();
            String cardId = entry.getValue();
            PatricianCard.Type patricianType = PatricianCard.Type.valueOf(patricianGroupId);
            
            InfluenceCard cardToPlay = new InfluenceCard(cardId, InfluenceCard.Type.valueOf(cardId));
            persistentGame.playInfluenceCard(patricianType, current, cardToPlay, false);
        }
        
        // Complete this player's initial influence placement
        persistentGame.completeInitialInfluencePlacement();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Initial influence placed");
        response.put("currentMode", persistentGame.getCurrentMode().toString());
        response.put("currentPlayer", persistentGame.getCurrentPlayer().toString());
        response.put("waitingForInitialInfluence", persistentGame.isWaitingForInitialInfluence());
        
        return Response.ok(response).build();
    }

    // GET /game/draw - Draw a card from a specified deck for a player
    @GET
    @Path("/draw")
    @Produces(MediaType.APPLICATION_JSON)
    public Response drawCard(
            @QueryParam("playerId") String playerId,
            @QueryParam("deckType") String deckType) {

        if (playerId == null || deckType == null) {
            throw new IllegalArgumentException("player or deck type was missing");
        }

        Player player = resolvePlayer(playerId);

        boolean isInfluence = "influence".equalsIgnoreCase(deckType);
        Card drawnCard = persistentGame.draw(player, isInfluence);
        CardDTO cardDTO = CardDTO.from(drawnCard);

        if (cardDTO == null) {
            throw new IllegalArgumentException("No card drawn because hand is at limit");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "Okay");
        response.put("card", cardDTO);

        return Response.ok(response).build();
    }

    // POST /game/action/proceedToVoteOfConfidence - Move directly to vote of confidence phase
    @POST
    @Path("/action/proceedToVoteOfConfidence")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proceedToVoteOfConfidence(Map<String, Object> request) {
        try {
            String playerId = (String) request.get("playerId");
            Player player = resolvePlayer(playerId);
            
            // Validate it's the player's turn
            if (persistentGame.getCurrentPlayer() != player) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Not your turn\"}")
                    .build();
            }
            
            persistentGame.skipSecondCardSelection();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Proceeded to vote of confidence");
            response.put("turnPhase", persistentGame.getCurrentTurnPhase().toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    // POST /game/action/voteOfConfidence - Execute vote of confidence
    @POST
    @Path("/action/voteOfConfidence")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response executeVoteOfConfidence(Map<String, Object> request) {
        try {
            String playerId = (String) request.get("playerId");
            String patricianTypeStr = (String) request.get("patricianType");
            
            Player player = resolvePlayer(playerId);
            PatricianCard.Type patricianType = PatricianCard.Type.valueOf(patricianTypeStr.toUpperCase());
            
            // Validate it's the player's turn
            if (persistentGame.getCurrentPlayer() != player) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Not your turn\"}")
                    .build();
            }
            
            Player winner = persistentGame.executeVoteOfConfidence(patricianType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Vote of confidence executed");
            response.put("winner", winner != null ? winner.toString() : null);
            response.put("turnPhase", persistentGame.getCurrentTurnPhase().toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    // POST /game/action/drawCards - Draw cards to hand limit
    @POST
    @Path("/action/drawCards")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response drawCards(Map<String, Object> request) {
        try {
            String playerId = (String) request.get("playerId");
            Boolean fromInfluenceDeck = (Boolean) request.get("fromInfluenceDeck");
            
            Player player = resolvePlayer(playerId);
            
            // Validate it's the player's turn
            if (persistentGame.getCurrentPlayer() != player) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Not your turn\"}")
                    .build();
            }
            
            if (fromInfluenceDeck == null) {
                fromInfluenceDeck = true; // Default to influence deck
            }
            
            persistentGame.drawToHandLimit(fromInfluenceDeck);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "Cards drawn, turn ended");
            response.put("newCurrentPlayer", persistentGame.getCurrentPlayer().toString());
            response.put("turnPhase", persistentGame.getCurrentTurnPhase().toString());
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
    }

    // POST /api/game/reset - Reset the game to initial state
    @POST
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetGame() {
        persistentGame = new Game();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "Game reset");
        response.put("currentMode", persistentGame.getCurrentMode().toString());
        response.put("currentPlayer", persistentGame.getCurrentPlayer().toString());
        return Response.ok(response).build();
    }

    // Utility methods
    private Player resolvePlayer(String player) { return Player.valueOf(player.toUpperCase());}


    // Other API methods unchanged...

    // Record DTOs for GET /api/game response
    public static record GameState(
        Player currentPlayer,
        int turnNumber,
        List<CardDTO> currentPlayerHand,
        int opponentHandCount,
        Map<Player, Map<String,Integer>> playerPatricianCards,
        List<String> bustBagContents,
        Map<Player, DeckCount> deckCounts,
        Map<String, PatricianBoardEntry> patricianBoard,
        String gameMode,
        boolean waitingForInitialInfluence,
        String turnPhase,
        int cardsPlayedThisTurn,
        boolean canPlayMoreCards) {}

    public static record DeckCount(int influenceDeckCount, int actionDeckCount) {}

    public static record CardDTO(String id, String type) {
        public static CardDTO from(Card card) {
            if (card == null) return null;
            if (card instanceof InfluenceCard ic) {
                return new CardDTO(ic.id(), "influence");
            } else if (card instanceof ActionCard ac) {
                return new CardDTO(ac.id(), "action");
            }
            throw new IllegalArgumentException("Unrecognized card: "+card.toString());
        }
    }

    public static record PatricianBoardEntry(int remaining, Map<Player, List<InfluenceEntry>> influence) {}

    public static record InfluenceEntry(boolean faceUp, String type) {}
    
    public static record CardAssignment(String influenceCardId, String patricianType) {}
    
    // POST /game/action/playInfluenceCard - Play influence cards with face up/down positioning
    @POST
    @Path("/action/playInfluenceCard")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response playInfluenceCard(Map<String, Object> request) {
        try {
            String playerId = (String) request.get("playerId");
            Map<String, Object> faceDownAssignmentMap = (Map<String, Object>) request.get("faceDownAssignment");
            Map<String, Object> faceUpAssignmentMap = (Map<String, Object>) request.get("faceUpAssignment");
            
            Player player = resolvePlayer(playerId);
            
            // Validate it's the player's turn during standard play
            if (persistentGame.getCurrentMode() != Game.GameMode.STANDARD_PLAY) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Can only play influence cards during standard play\"}")
                    .build();
            }
            
            if (persistentGame.getCurrentPlayer() != player) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Not your turn\"}")
                    .build();
            }
            
            // Parse face down assignment (required)
            if (faceDownAssignmentMap == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Face down card assignment is required\"}")
                    .build();
            }
            
            String faceDownCardId = (String) faceDownAssignmentMap.get("influenceCardId");
            String faceDownPatricianType = (String) faceDownAssignmentMap.get("patricianType");
            
            PatricianCard.Type faceDownPatrician = PatricianCard.Type.valueOf(faceDownPatricianType.toUpperCase());
            
            // Play the face down card using turn mechanics
            boolean success = persistentGame.playCardInTurn(faceDownCardId, faceDownPatrician, null);
            if (!success) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Cannot play face down card: " + faceDownCardId + "\"}")
                    .build();
            }
            
            // Parse face up assignment (optional)
            if (faceUpAssignmentMap != null) {
                String faceUpCardId = (String) faceUpAssignmentMap.get("influenceCardId");
                String faceUpPatricianType = (String) faceUpAssignmentMap.get("patricianType");
                
                PatricianCard.Type faceUpPatrician = PatricianCard.Type.valueOf(faceUpPatricianType.toUpperCase());
                
                // Play the face up card using turn mechanics
                boolean faceUpSuccess = persistentGame.playCardInTurn(faceUpCardId, faceUpPatrician, null);
                if (!faceUpSuccess) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"Cannot play face up card: " + faceUpCardId + "\"}")
                        .build();
                }
            }
            
            // Return the updated game state
            return Response.ok(getGame()).build();
            
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
    }
    
    // POST /game/action/playActionCard - Play action card with custom effect
    @POST
    @Path("/action/playActionCard")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response playActionCard(Map<String, Object> request) {
        try {
            String playerId = (String) request.get("playerId");
            String actionCardName = (String) request.get("actionCardName");
            Map<String, Object> actionEffectData = (Map<String, Object>) request.get("actionEffect");
            
            Player player = resolvePlayer(playerId);
            
            // Validate it's the player's turn during standard play
            if (persistentGame.getCurrentMode() != Game.GameMode.STANDARD_PLAY) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Can only play action cards during standard play\"}")
                    .build();
            }
            
            if (persistentGame.getCurrentPlayer() != player) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Not your turn\"}")
                    .build();
            }
            
            // Create ActionEffect from the provided data
            ActionEffect customEffect = (game, effectPlayer, context) -> {
                // The UI provides the specific logic for the action card effect
                // This is a placeholder - the actual effect logic should be provided by the UI
                if (actionEffectData != null) {
                    // Apply custom effect logic here based on actionEffectData
                    // For now, we'll just log that a custom action was applied
                    System.out.println("Custom action effect applied for " + actionCardName);
                }
            };
            
            // Apply the custom effect directly to the game
            customEffect.apply(persistentGame, player, null);
            
            // Return the updated game state
            return Response.ok(getGame()).build();
            
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
        }
    }
}