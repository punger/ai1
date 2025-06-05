package com.example.caesarandcleopatra.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

/**
 * Encapsulates board and played‐influence state for a single Patrician group.
 * Manages remaining Patrician cards, played influence cards, and VOC resolution.
 */
public class PatricianGroupState {
    private final Game game;
    private final List<PatricianCard> boardCards = new ArrayList<>();
    private static class Patrician
    private final Map<Player, List<Game.PlayedInfluenceCard>> playedInfluence = new HashMap<>();

    public PatricianGroupState(PatricianCard.Type type, Game game) {
        this.game = game;
        // initialize board cards by count
        for (int i = 0; i < type.getCount(); i++) {
            String id = type.name().toLowerCase();
            String display = type.name().charAt(0) + type.name().substring(1).toLowerCase();
            boardCards.add(new PatricianCard(id, type, display));
        }
        for (Player p : Player.values()) {
            playedInfluence.put(p, new ArrayList<>());
        }
    }

    public PatricianCard.Type getType() {
        return type;
    }

    public void playInfluence(Player player, Game.PlayedInfluenceCard pic) {
        playedInfluence.get(player).add(pic);
    }

    public List<Game.PlayedInfluenceCard> getPlayedInfluence(Player player) {
        return Collections.unmodifiableList(playedInfluence.get(player));
    }

    public int remainingCount() {
        return boardCards.size();
    }

    public PatricianCard claimOne() {
        if (boardCards.isEmpty()) return null;
        return boardCards.remove(0);
    }

    /**
     * Resolves a Vote of Confidence (VOC) for the specified Patrician group.
     * All face-down Influence cards for that group are revealed.
     * Sums the values for each player.
     * Discard policy: the player with the higher sum discards their highest-value Influence card;
     * the player with the lower sum discards their lowest-value Influence card.
     * Philosophers invert the winner for claiming but do not affect sum-based discards.
     * If tied sums: no cards are discarded or claimed.
     *
     * @param patricianType the Patrician group for the VOC
     * @return the winning Player, or null if tie
     */
    public Player resolveVoteOfConfidence(PatricianCard.Type patricianType, Game game) {
        int caesarPhilosopherCount = 0, cleopatraPhilosopherCount = 0;
        int caesarSum = 0, cleoSum = 0;
        for (var entry : groupMap.entrySet()) {
            Player p = entry.getKey();
            for (var pic : entry.getValue()) {
                if (pic.getInfluenceCard().type() == InfluenceCard.Type.PHILOSOPHER) {
                    pic.setFaceUp(true);
                    if (p == Player.CAESAR) caesarPhilosopherCount++;
                    else cleopatraPhilosopherCount++;
                    continue;
                }
                pic.setFaceUp(true);
                int val = pic.getInfluenceCard().type().getValue();
                if (p == Player.CAESAR) caesarSum += val;
                else cleoSum += val;
            }
        }
        if (caesarSum == cleoSum) {
            return null;
        }
        boolean invert = caesarPhilosopherCount != cleopatraPhilosopherCount;
        // Determine sum-based winner/loser for discard policy
        Player sumWinner = caesarSum > cleoSum ? Player.CAESAR : Player.CLEOPATRA;
        Player sumLoser = sumWinner == Player.CAESAR ? Player.CLEOPATRA : Player.CAESAR;
        // Effective winner may be inverted by Philosopher
        Player effectiveWinner = sumWinner;
        if (invert) {
            effectiveWinner = sumLoser;
        }
        Player effectiveLoser = effectiveWinner == Player.CAESAR ? Player.CLEOPATRA : Player.CAESAR;

        // Discard highest-value card from the sum-based winner
        var sumWinnerList = groupMap.getOrDefault(sumWinner, new ArrayList<>());
        var high = sumWinnerList.stream()
            .max(Comparator.comparingInt(pic -> pic.getInfluenceCard().type().getValue()))
            .orElse(null);
        if (high != null) {
            sumWinnerList.remove(high);
            discard(high.getInfluenceCard());
        }

        // Assign claimed Patrician card to effective winner
        PatricianCard toClaim = patricianState.claimCard(patricianType);
        if (toClaim != null) {
            playerPatricians.get(effectiveWinner).add(toClaim);
        }

        // Discard lowest-value card from the sum-based loser
        var sumLoserList = groupMap.getOrDefault(sumLoser, new ArrayList<>());
        var low = sumLoserList.stream()
            .min(Comparator.comparingInt(pic -> pic.getInfluenceCard().type().getValue()))
            .orElse(null);
        if (low != null) {
            sumLoserList.remove(low);
            discard(low.getInfluenceCard());
        }
        return winner;
    }

}