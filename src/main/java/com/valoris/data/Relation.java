package com.valoris.data;

/** Relation between two kingdoms. No entry = neutral. */
public enum Relation {
    ALLIANCE("alliance"),
    WAR("war");

    private final String label;

    Relation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
