package dev.ryvrook.lullaby.model;

import java.util.Locale;

/** The {@code Mode} config value. */
public enum Mode {
    /** Replace the two vanilla slumber systems with Lullaby copies, or fall back to manual. */
    NATIVE,
    /** Leave vanilla alone and move the clock from Lullaby itself. */
    MANUAL,
    /** Native, with the automatic fallback to manual. The default. */
    AUTO;

    public static Mode parse(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return AUTO;
        }
    }

    /** True when this setting asks for the native system swap. */
    public boolean wantsNative() {
        return this != MANUAL;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
