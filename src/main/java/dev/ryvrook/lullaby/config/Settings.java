package dev.ryvrook.lullaby.config;

import dev.ryvrook.lullaby.model.Mode;
import dev.ryvrook.lullaby.model.Requirement;
import dev.ryvrook.lullaby.model.Rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An immutable copy of {@link LullabyConfig} with every value clamped to something usable. World threads
 * read the current instance through a volatile field, so a reload never shows them a half applied config.
 */
public record Settings(
        Mode mode,
        Rule defaultRule,
        int minimumSleepers,
        boolean excludeCreative,
        int afkSeconds,
        int skipSeconds,
        int cooldownSeconds,
        boolean progressMessages,
        boolean showToasts,
        boolean showTitle,
        boolean clearStorms,
        List<String> stormWeathers,
        boolean allowDynamic,
        int dynamicMaxSpeed,
        boolean dynamicIgnored,
        Map<String, Rule> worlds) {

    /** A manual fast forward never takes longer than this, so a typo cannot stall a night for an hour. */
    public static final int MAX_SKIP_SECONDS = 60;

    public static Settings from(LullabyConfig c) {
        boolean allowDynamic = c.allowDynamic();
        boolean[] ignored = {false};
        Rule base = new Rule(true,
                gate(Requirement.parse(c.requirement(), Requirement.PERCENT), allowDynamic, ignored),
                clamp(c.percent(), 0, 100),
                Math.max(1, c.count()));
        Map<String, Rule> worlds = new HashMap<>();
        for (Map.Entry<String, WorldRule> e : c.worlds().entrySet()) {
            WorldRule w = e.getValue();
            if (e.getKey() == null || w == null) {
                continue;
            }
            worlds.put(e.getKey().toLowerCase(Locale.ROOT), new Rule(
                    w.enabled() == null ? base.enabled() : w.enabled(),
                    gate(Requirement.parse(w.requirement(), base.requirement()), allowDynamic, ignored),
                    w.percent() == null ? base.percent() : clamp(w.percent(), 0, 100),
                    w.count() == null ? base.count() : Math.max(1, w.count())));
        }
        List<String> storms = new ArrayList<>();
        for (String s : c.stormWeathers()) {
            if (s != null && !s.isBlank()) {
                storms.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return new Settings(
                Mode.parse(c.mode()),
                base,
                Math.max(1, c.minimumSleepers()),
                c.excludeCreative(),
                Math.max(0, c.afkSeconds()),
                clamp(c.skipSeconds(), 0, MAX_SKIP_SECONDS),
                Math.max(0, c.cooldownSeconds()),
                c.progressMessages(),
                c.showToasts(),
                c.showTitle(),
                c.clearStorms(),
                List.copyOf(storms),
                allowDynamic,
                clamp(c.dynamicMaxSpeed(), 1, 200),
                ignored[0],
                Map.copyOf(worlds));
    }

    /** The rule for a world by name, ignoring case, or the default rule. */
    public Rule ruleFor(String worldName) {
        if (worldName == null) {
            return defaultRule;
        }
        Rule rule = worlds.get(worldName.toLowerCase(Locale.ROOT));
        return rule == null ? defaultRule : rule;
    }

    /**
     * Dynamic nights are experimental and off until {@code AllowDynamic} is true. A dynamic rule without that
     * switch becomes percent, and {@code dynamicIgnored} records that so the plugin can warn once.
     */
    private static Requirement gate(Requirement wanted, boolean allowDynamic, boolean[] ignored) {
        if (wanted == Requirement.DYNAMIC && !allowDynamic) {
            ignored[0] = true;
            return Requirement.PERCENT;
        }
        return wanted;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
