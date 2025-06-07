package com.example.caesarandcleopatra.model;



/**
 * Represents an Action card in the game.
 * Each ActionCard.Type has a fixed count.
 */
public record ActionCard(String id, Type type) implements Card {

    public enum Type {
        /**
         * ASSASSINATION: removes a specified opponent's played Influence card
         * from the board and discards it.
         * Required context fields: patricianType1, cardIndex.
         */
        ASSASSINATION(3, (game, player, context) -> {
            PatricianCard.Type type = context.getPatricianType1();
            Player opponent = player.opponent();
            // remove via PatricianState and discard
            InfluenceCard removed = game.getPatricianState().removeInfluence(type, opponent, context.getCardIndex());
            if (removed != null) {
                game.discard(removed);
            }
        }),
        /**
         * SCOUT: reveals all of the opponent’s played Influence cards
         * for the specified patrician type by turning them face up.
         * Required context fields: patricianType1.
         */
        SCOUT(3, (game, player, context) -> {
            PatricianCard.Type type = context.getPatricianType1();
            Player opponent = player.opponent();
            game.getPatricianState().revealInfluence(type, opponent);
        }),
        /**
         * WRATH_OF_THE_GODS: discards all played Influence cards
         * for both players of the specified patrician type.
         * Required context fields: patricianType1.
         */
        WRATH_OF_THE_GODS(1, (game, player, context) -> {
            PatricianCard.Type type = context.getPatricianType1();
            for (InfluenceCard card : game.getPatricianState().clearInfluence(type))
                game.discard(card);
        }),
        /**
         * CASTLING: redistributes the current player's played Influence cards
         * between two patrician groups as specified, placing cards face down.
         * Required context fields: patricianType1, patricianType2, redistributedCardsForType1, redistributedCardsForType2.
         */
        CASTLING(1, (game, player, context) -> {
            PatricianCard.Type t1 = context.getPatricianType1();
            PatricianCard.Type t2 = context.getPatricianType2();
            game.getPatricianState().replace(player, t1, context.getRedistributedCardsForType1());
            game.getPatricianState().replace(player, t2, context.getRedistributedCardsForType2());
        }),
        /**
         * VETO: negates the opponent's last ActionCard effect, discarding both
         * the VETO and the opponent's action card, then allows the vetoing player
         * to draw a replacement card.
         * Required context fields: none.
         */
        VETO(1, (game, player, context) -> {
            // VETO negates opponent's last Action; behavior to be implemented
        });

        private final int count;
        private final ActionEffect effect;

        Type(int count, ActionEffect effect) {
            this.count = count;
            this.effect = effect;
        }

        public int getCount() {
            return count;
        }

        public void apply(Game game, Player player, ActionContext context) {
            effect.apply(game, player, context);
        }
    }

    @Override
    public int getCount() {
        return type.getCount();
    }

    /**
     * Applies this ActionCard's effect to the game for the given player.
     */
    /**
     * Applies this ActionCard's effect to the game for the given player with additional context.
     */
    public void apply(Game game, Player player, ActionContext context) {
        type.apply(game, player, context);
    }
}
