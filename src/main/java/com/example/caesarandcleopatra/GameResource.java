package com.example.caesarandcleopatra;

import jakarta.inject.Singleton;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import com.example.caesarandcleopatra.model.BustBag.BustPiece;
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
        var patricianState = persistentGame.getPatricianState();
        for (var type : com.example.caesarandcleopatra.model.PatricianCard.Type.values()) {
            int remaining = patricianState.getRemainingCount(type);
            Map<Player, List<InfluenceEntry>> influenceMap = new HashMap<>();
            for (Player player : Player.values()) {
                var influenceList = patricianState.getPlayedInfluence(type, player);
                List<InfluenceEntry> entries = new ArrayList<>();
                for (var inf : influenceList) {
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

    public static record CardDTO(String id, String type) {}

    public static record PatricianBoardEntry(int remaining, Map<Player, List<InfluenceEntry>> influence) {}

    public static record InfluenceEntry(boolean faceUp, String type) {}
}