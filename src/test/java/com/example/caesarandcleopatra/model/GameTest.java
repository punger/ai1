package com.example.caesarandcleopatra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.caesarandcleopatra.model.PatricianState.InfluenceCardState;

import static com.example.caesarandcleopatra.model.InfluenceCard.Type.*;
import static com.example.caesarandcleopatra.model.PatricianCard.Type.*;
import static com.example.caesarandcleopatra.model.Player.*;

public class GameTest {
    InfluenceCard cardOf(InfluenceCard.Type t) {return new InfluenceCard(t.name(), t);}
    void validateInfluence(List<? extends Card> a, InfluenceCard.Type ... cards) {
        List<InfluenceCard.Type> bType = Arrays.asList(cards);
        List<InfluenceCard> b = bType.stream().map(this::cardOf).toList();
        assertEquals(a.size(), b.size());
        assertTrue(a.stream().allMatch(e -> Collections.frequency(a, e) == Collections.frequency(b, e)));
    }

    List<InfluenceCard> cardsOf(List<InfluenceCardState> played) {
        return played.stream().map(InfluenceCardState::getCard).toList();
    }
    @Test
    public void testVoteOfConfidence_CaesarWins() {
        Game game = new Game();
        PatricianState state = game.getPatricianState();
        // Caesar: 3+2, Cleopatra: 1
        state.replaceInfluence(CAESAR, PRAETOR, List.of(cardOf(THREE) , cardOf(TWO)));
        state.replaceInfluence(CLEOPATRA, PRAETOR, List.of(cardOf(ONE)));
        Player winner = state.resolveVoteOfConfidence(PRAETOR);
        System.out.println("VOC CaesarWins: " + winner);
        assert winner == CAESAR;
        validateInfluence(game.getDiscards(), ONE, THREE);
        validateInfluence(cardsOf(state.getPlayedInfluence(PRAETOR, CAESAR)), TWO);
        validateInfluence(cardsOf(state.getPlayedInfluence(PRAETOR, CLEOPATRA)));
    }
    
    @Test
    public void testVoteOfConfidence_CleopatraWins() {
        Game game = new Game();
        PatricianState state = game.getPatricianState();
        // Caesar: 1, Cleopatra: 4+2
        state.replaceInfluence(CAESAR, AEDILE, List.of(cardOf(ONE)));
        state.replaceInfluence(CLEOPATRA, AEDILE, List.of(cardOf(FOUR), cardOf(TWO)));
        Player winner = state.resolveVoteOfConfidence(AEDILE);
        System.out.println("VOC CleopatraWins: " + winner);
        assert winner == CLEOPATRA;
        System.out.println(game.getDiscards());
        validateInfluence(game.getDiscards(), FOUR, ONE);
        validateInfluence(cardsOf(state.getPlayedInfluence(AEDILE, CAESAR)));
        validateInfluence(cardsOf(state.getPlayedInfluence(AEDILE, CLEOPATRA)), TWO);
    }

    @Test
    public void testVoteOfConfidence_Tie() {
        Game game = new Game();
        PatricianState state = game.getPatricianState();
        // Caesar: 2+2, Cleopatra: 4
        state.replaceInfluence(CAESAR, CONSUL, 
            java.util.List.of(cardOf(TWO),cardOf(TWO)));
        state.replaceInfluence(CLEOPATRA, CONSUL, 
            java.util.List.of(cardOf(FOUR)));
        Player winner = state.resolveVoteOfConfidence(CONSUL);
        System.out.println("VOC Tie: " + winner);
        assert winner == null;
        System.out.println(game.getDiscards());
        validateInfluence(game.getDiscards());
        validateInfluence(cardsOf(state.getPlayedInfluence(CONSUL, CAESAR)), TWO, TWO);
        validateInfluence(cardsOf(state.getPlayedInfluence(CONSUL, CLEOPATRA)), FOUR);
    }

    @Test
    public void testVoteOfConfidence_PhilosopherInversion() {
        Game game = new Game();
        PatricianState state = game.getPatricianState();
        // Caesar: 2+PHILOSOPHER, Cleopatra: 3
        state.replaceInfluence(CAESAR, CENSOR, List.of(cardOf(TWO), cardOf(PHILOSOPHER)));
        state.replaceInfluence(CLEOPATRA, CENSOR, java.util.List.of(cardOf(THREE)));
        Player winner = state.resolveVoteOfConfidence(CENSOR);
        System.out.println("VOC PhilosopherInversion: " + winner);
        // According to the rules, if philosopher counts differ, the player with more philosophers loses
        assert winner == CAESAR;
        System.out.println(game.getDiscards());
        validateInfluence(game.getDiscards(), TWO, THREE, PHILOSOPHER);
        validateInfluence(cardsOf(state.getPlayedInfluence(CONSUL, CAESAR)));
        validateInfluence(cardsOf(state.getPlayedInfluence(CONSUL, CLEOPATRA)));
    }

}
