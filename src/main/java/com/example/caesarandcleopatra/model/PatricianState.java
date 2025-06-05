package com.example.caesarandcleopatra.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;

import com.example.caesarandcleopatra.model.PatricianCard.Type;

import java.util.Comparator;

/**
 * Encapsulates the board and played‐influence state for all Patrician groups.
 * Manages remaining Patrician cards and played influence cards per group and player.
 */
public class PatricianState {
    private final Game game;
    private final Map<PatricianCard.Type, Map<Player, InfluenceList>> playedInfluence = new HashMap<>();
    private final Map<PatricianCard.Type, Integer> boardState = new HashMap<>();

    private class InfluenceCardState {
        private final InfluenceCard card;
        private boolean faceUp = false;
        InfluenceCardState(InfluenceCard card, boolean isFaceUp) {
            this(card);
            faceUp = isFaceUp;
        }
        InfluenceCardState(InfluenceCard card) {
            this.card = card;
        }
        void setFaceDown() {faceUp = false;}
        void setFaceUp() {faceUp = true;}
        boolean isFaceUp() {return faceUp;}
        InfluenceCard getCard() { return card;}
        int getValue() {return card.getValue();}
    }
    private class InfluenceList implements Comparable<InfluenceList> {
        private final ArrayList<InfluenceCardState> influencers = new ArrayList<>();
        private int numPhilosophers = 0;

        void addCard(InfluenceCard card, boolean isFaceUp) {addCard(new InfluenceCardState(card, isFaceUp));}
        void addCard(InfluenceCardState card) { 
            if (card.getCard().type().equals(InfluenceCard.Type.PHILOSOPHER))
                numPhilosophers++;
            influencers.add(card);
        }
        private InfluenceCard removeCard(InfluenceCardState card) {
            influencers.remove(card);
            if (card.getCard().type().equals(InfluenceCard.Type.PHILOSOPHER))
                numPhilosophers--;
            return card.getCard();
        }
        InfluenceCard removeAtIndex(int index) {
            if (index < 0 || index >= influencers.size()) return null;
            return removeCard(influencers.get(index));
        }
        @Override
        public int compareTo(InfluenceList o) {
            if (influenceSum() == o.influenceSum()) return 0;
            if (numPhilosophers != o.numPhilosophers) 
                return o.influenceSum() - influenceSum();
            return influenceSum() - o.influenceSum();
        }
        int influenceSum() { return influencers.stream().mapToInt(pc -> pc.getCard().getValue()).sum();}
        InfluenceCard removeMax() {
            if (influencers.isEmpty() || 
                    influencers.stream().allMatch(ics -> ics.getCard().type().equals(InfluenceCard.Type.PHILOSOPHER))) 
                return null;
            InfluenceCardState card = influencers.stream().min(Comparator.comparing(InfluenceCardState::getValue)).get();
            return removeCard(card);
        }
        InfluenceCard removeMin() {
            if (influencers.isEmpty() || 
                    influencers.stream().allMatch(ics -> ics.getCard().type().equals(InfluenceCard.Type.PHILOSOPHER))) 
                return null;
            InfluenceCardState card = influencers.stream().max(Comparator.comparing(InfluenceCardState::getValue)).get();
            return removeCard(card);
        }
        List<InfluenceCardState> getList() {return influencers;}
        void clear() {
            numPhilosophers = 0;
            influencers.clear();
        }
    }

    public PatricianState(Game game) {
        this.game = game;
        for (PatricianCard.Type type : PatricianCard.Type.values()) {
            boardState.put(type, type.getCount());
            Map<Player, InfluenceList> startState = new HashMap<>();
            startState.put(Player.CAESAR, new InfluenceList());
            startState.put(Player.CLEOPATRA, new InfluenceList());
            playedInfluence.put(type, startState);
        }
    }

    /**
     * Adds a played InfluenceCard against the specified Patrician group.
     *
     * @param type    the Patrician group type
     * @param player  the player who played the card
     * @param card    the InfluenceCard played
     * @param faceUp  whether the card is face-up
     */
    public void addInfluence(PatricianCard.Type type, Player player, InfluenceCard card, boolean faceUp) {
        playedInfluence.get(type).get(player).addCard(card, faceUp);
    }

    /**
     * Returns an unmodifiable view of played influence cards for a player on a group.
     *
     * @param type   the Patrician group type
/**
     * Removes and returns a played influence card from a group for a player.
     *
     * @param type    the Patrician group type
     * @param player  the player whose played card to remove
     * @param index   the index in that player’s played list
     * @return the removed InfluenceCard, or null if out of bounds
     */
    public InfluenceCard removeInfluence(PatricianCard.Type type, Player player, int index) {
        return playedInfluence.get(type).get(player).removeAtIndex(index);
    }

    /**
     * Reveals all played influence cards for the given player on a Patrician group.
     *
     * @param type   the Patrician group type
     * @param player the player whose played cards to reveal
     */
    public void revealInfluence(PatricianCard.Type type, Player player) {
        for (InfluenceCardState state : playedInfluence.get(type).get(player).getList()) {
            state.setFaceUp();
        }
    }
/**
 * Resolves a Vote of Confidence (VOC) for the specified Patrician group.
 * All face-down influence cards for both players on this group are revealed.
 * Influence totals are compared, with Philosopher cards causing inversion if counts differ.
 * In a tie (equal effective totals), no board cards are removed or discarded, and influence remains for future VOCs.
 * On a clear win:
 *   - The board count for this group is decremented.
 *   - Regardless of the winner or loser, the player with the higher total discards his highest value 
 *     influence card and the player with a lower sum discards his lowest.
 *   - A player may win the VOC but have no numbered influence cards.  In that case nothing is discarded for that player
 *   - Remaining influence cards stay face up on the board for cumulative resolution in future VOCs.
 *
 * @param type the Patrician group type to resolve
 * @return the winning Player, or null if the VOC is a tie
 */
    public Player resolveVoteOfConfidence(PatricianCard.Type type) {
        // reveal face-down cards
        revealInfluence(type, Player.CAESAR);
        revealInfluence(type, Player.CLEOPATRA);
        // get lists
        InfluenceList caesarList = playedInfluence.get(type).get(Player.CAESAR);
        InfluenceList cleopatraList = playedInfluence.get(type).get(Player.CLEOPATRA);
        // compare total influence considering Philosopher counts
        int cmp = caesarList.compareTo(cleopatraList);
        if (cmp == 0) {
            return null;
        }
        // decrement remaining board cards for this type
        boardState.put(type, boardState.get(type) - 1);

        Player winner = cmp > 0 ? Player.CAESAR : Player.CLEOPATRA;

        if (caesarList.influenceSum() > cleopatraList.influenceSum()) {
            game.discard(caesarList.removeMax());
            game.discard(cleopatraList.removeMin());
        } else {
            game.discard(caesarList.removeMin());
            game.discard(cleopatraList.removeMax());
        }
        return winner;
    }
    /**
     * @param player the player whose cards to retrieve
     * @return list of PlayedInfluenceCard
     */
    public List<InfluenceCardState> getPlayedInfluence(PatricianCard.Type type, Player player) {
        return Collections.unmodifiableList(playedInfluence.get(type).get(player).getList());
    }

    public List<InfluenceCard> clearInfluence(PatricianCard.Type patrician) {
        LinkedList<InfluenceCardState> removed = new LinkedList<>();
        InfluenceList playersInfluence = playedInfluence.get(patrician).get(Player.CAESAR);
        removed.addAll(playersInfluence.influencers);
        playersInfluence.clear();
        playersInfluence =  playedInfluence.get(patrician).get(Player.CLEOPATRA);
        removed.addAll(playersInfluence.influencers);
        playersInfluence.clear();
        return removed.stream().map(InfluenceCardState::getCard).toList();
    }

    public void replace(Player player, Type patrician, List<InfluenceCard> replacementCards) {
        InfluenceList playersInfluence = playedInfluence.get(patrician).get(player);
        playersInfluence.clear();
        replacementCards.forEach(c -> playersInfluence.addCard(c, false));
    }
}