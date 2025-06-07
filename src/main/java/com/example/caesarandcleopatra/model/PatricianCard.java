package com.example.caesarandcleopatra.model;

public record PatricianCard(String id, Type type, String name) implements Card {

    public enum Type {
        PRAETOR(5, "blue"),
        AEDILE(5, "green"),
        CONSUL(3, "white"),
        CENSOR(3, "pink"),
        QUAESTOR(5, "yellow");

        private final int count;
        private final String color;

        Type(int count, String color) {
            this.count = count;
            this.color = color;
        }

        public int getCount() {
            return count;
        }

        public String getColor() {
            return color;
        }
    }

    @Override
    public int getCount() {
        return type.getCount();
    }
}
