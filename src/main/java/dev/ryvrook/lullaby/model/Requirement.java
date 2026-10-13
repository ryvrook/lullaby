package dev.ryvrook.lullaby.model;

import java.util.Locale;

/** How many sleepers a world needs. */
public enum Requirement {
    /** A share of the eligible players, rounded up. */
    PERCENT,
    /** A fixed number of players. */
    COUNT,
    /** No hard threshold. Time runs faster the larger the sleeping share. */
    DYNAMIC;

    /** Parses a config value ignoring case, or returns {@code fallback} when it is empty or unknown. */
    public static Requirement parse(String value, Requirement fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
