package com.example.caesarandcleopatra.model;

public sealed interface Card permits PatricianCard, InfluenceCard, ActionCard {
    String id();
    int getCount();

    /**
     * Returns all available PatricianCard types.
     */
    static PatricianCard.Type[] patricianTypes() {
        return PatricianCard.Type.values();
    }

    /**
     * Returns all available InfluenceCard types.
     */
    static InfluenceCard.Type[] influenceTypes() {
        return InfluenceCard.Type.values();
    }

    /**
     * Returns all available ActionCard types.
     */
    static ActionCard.Type[] actionTypes() {
        return ActionCard.Type.values();
    }
}