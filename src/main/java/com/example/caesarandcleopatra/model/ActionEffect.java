package com.example.caesarandcleopatra.model;

@FunctionalInterface
public interface ActionEffect {
    /**
     * Applies an action effect on the game for a given player with context.
     *
     * @param game    the game instance
     * @param player  the player performing the action
     * @param context additional parameters required for the action
     */
    void apply(Game game, Player player, ActionContext context);
}