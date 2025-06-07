package com.example.caesarandcleopatra;

import jakarta.inject.Singleton;
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

import com.example.caesarandcleopatra.model.*;;

@Path("/game")
@Singleton
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
        Player current = Player.CAESAR; // TODO: determine actual current player
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
        for (var type : PatricianCard.Type.values()) {
            int remaining = boardState.getOrDefault(type, 0);
            Map<Player, List<InfluenceEntry>> influenceMap = new HashMap<>();
            for (Player player : Player.values()) {
                List<InfluenceCardState> influenceList = patricianState.getPlayedInfluence(type, player);
                List<InfluenceEntry> entries = new ArrayList<>();
                for (PatricianState.InfluenceCardState inf : influenceList) {
                    entries.add(new InfluenceEntry(inf.isFaceUp(), inf.getCard().id()));
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
                             deckCounts, patricianBoard);
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
        for (Map.Entry<String, String> entry : influenceCards.entrySet()) {
            String patricianGroupId = entry.getKey();
            String cardId = entry.getValue();
            PatricianCard.Type patricianType = PatricianCard.Type.valueOf(patricianGroupId);
            InfluenceCard cardToPlay = new InfluenceCard(cardId, InfluenceCard.Type.valueOf(cardId));
            persistentGame.playInfluenceCard(patricianType, current, cardToPlay, false);
        }
        return Response.ok("{\"status\":\"Initial influence placed\"}").build();
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

// POST /game/action/playCard - Play a card (Influence/Action)
    @POST
    @Path("/action/playAction")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> playCard(Map<String, Object> playRequest) {
        // Example: extract parameters from playRequest as needed
        // String playerId = (String) playRequest.get("playerId");
        // String cardId = (String) playRequest.get("cardId");
        // String target = (String) playRequest.get("target");
        // Implement game logic here, e.g. persistentGame.playCard(...);

        // For now, just return a stub response
        Map<String, Object> details = new HashMap<>();
        details.put("received", playRequest);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "card played");
        response.put("details", details);
        return response;
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
        Map<String, PatricianBoardEntry> patricianBoard) {}

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
}