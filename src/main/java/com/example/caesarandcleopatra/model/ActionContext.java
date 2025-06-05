package com.example.caesarandcleopatra.model;

import java.util.List;
import com.example.caesarandcleopatra.model.PatricianCard.Type;

/**
 * Carries context information required for ActionCard effects,
 * such as selected patrician types, card index, and redistributed cards lists.
 */
public class ActionContext {
    private Type patricianType1;
    private Type patricianType2;
    private int cardIndex;
    private List<InfluenceCard> redistributedCardsForType1;
    private List<InfluenceCard> redistributedCardsForType2;

    public ActionContext() {
    }

    public Type getPatricianType1() {
        return patricianType1;
    }

    public void setPatricianType1(Type patricianType1) {
        this.patricianType1 = patricianType1;
    }

    public Type getPatricianType2() {
        return patricianType2;
    }

    public void setPatricianType2(Type patricianType2) {
        this.patricianType2 = patricianType2;
    }

    public int getCardIndex() {
        return cardIndex;
    }

    public void setCardIndex(int cardIndex) {
        this.cardIndex = cardIndex;
    }

    public List<InfluenceCard> getRedistributedCardsForType1() {
        return redistributedCardsForType1;
    }

    public void setRedistributedCardsForType1(List<InfluenceCard> redistributedCardsForType1) {
        this.redistributedCardsForType1 = redistributedCardsForType1;
    }

    public List<InfluenceCard> getRedistributedCardsForType2() {
        return redistributedCardsForType2;
    }

    public void setRedistributedCardsForType2(List<InfluenceCard> redistributedCardsForType2) {
        this.redistributedCardsForType2 = redistributedCardsForType2;
    }
}