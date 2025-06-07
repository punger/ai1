package com.example.caesarandcleopatra.model;

public record InfluenceCard(String id, Type type) implements Card {

    public enum Type {
        ONE(1, 5),
        TWO(2, 5),
        THREE(3, 5),
        FOUR(4, 5),
        FIVE(5, 5),
        PHILOSOPHER(0, 2);

        private final int value;
        private final int count;

        Type(int value, int count) {
            this.value = value;
            this.count = count;
        }

        public int getValue() {
            return value;
        }

        public int getCount() {
            return count;
        }
    }

    @Override
    public int getCount() {
        return type.getCount();
    }
    public int getValue() {return type.value;}
}