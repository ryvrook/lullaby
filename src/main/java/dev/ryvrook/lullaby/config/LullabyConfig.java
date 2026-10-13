package dev.ryvrook.lullaby.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server owner options, written to {@code Lullaby.json} inside the mod data folder. Read it through
 * {@link Settings}, which is an immutable, cleaned up copy that world threads can share.
 */
public final class LullabyConfig {

    public static final BuilderCodec<LullabyConfig> CODEC = BuilderCodec.builder(LullabyConfig.class, LullabyConfig::new)
            .append(new KeyedCodec<>("Mode", Codec.STRING),
                    (c, v, extra) -> c.mode = v,
                    (c, extra) -> c.mode)
            .add()
            .append(new KeyedCodec<>("Requirement", Codec.STRING),
                    (c, v, extra) -> c.requirement = v,
                    (c, extra) -> c.requirement)
            .add()
            .append(new KeyedCodec<>("Percent", Codec.INTEGER),
                    (c, v, extra) -> c.percent = v,
                    (c, extra) -> c.percent)
            .add()
            .append(new KeyedCodec<>("Count", Codec.INTEGER),
                    (c, v, extra) -> c.count = v,
                    (c, extra) -> c.count)
            .add()
            .append(new KeyedCodec<>("MinimumSleepers", Codec.INTEGER),
                    (c, v, extra) -> c.minimumSleepers = v,
                    (c, extra) -> c.minimumSleepers)
            .add()
            .append(new KeyedCodec<>("ExcludeCreative", Codec.BOOLEAN),
                    (c, v, extra) -> c.excludeCreative = v,
                    (c, extra) -> c.excludeCreative)
            .add()
            .append(new KeyedCodec<>("AfkSeconds", Codec.INTEGER),
                    (c, v, extra) -> c.afkSeconds = v,
                    (c, extra) -> c.afkSeconds)
            .add()
            .append(new KeyedCodec<>("SkipSeconds", Codec.INTEGER),
                    (c, v, extra) -> c.skipSeconds = v,
                    (c, extra) -> c.skipSeconds)
            .add()
            .append(new KeyedCodec<>("CooldownSeconds", Codec.INTEGER),
                    (c, v, extra) -> c.cooldownSeconds = v,
                    (c, extra) -> c.cooldownSeconds)
            .add()
            .append(new KeyedCodec<>("ProgressMessages", Codec.BOOLEAN),
                    (c, v, extra) -> c.progressMessages = v,
                    (c, extra) -> c.progressMessages)
            .add()
            .append(new KeyedCodec<>("ShowToasts", Codec.BOOLEAN),
                    (c, v, extra) -> c.showToasts = v,
                    (c, extra) -> c.showToasts)
            .add()
            .append(new KeyedCodec<>("ShowTitle", Codec.BOOLEAN),
                    (c, v, extra) -> c.showTitle = v,
                    (c, extra) -> c.showTitle)
            .add()
            .append(new KeyedCodec<>("ClearStorms", Codec.BOOLEAN),
                    (c, v, extra) -> c.clearStorms = v,
                    (c, extra) -> c.clearStorms)
            .add()
            .append(new KeyedCodec<>("StormWeathers", Codec.STRING_ARRAY),
                    (c, v, extra) -> c.stormWeathers = v,
                    (c, extra) -> c.stormWeathers)
            .add()
            .append(new KeyedCodec<>("AllowDynamic", Codec.BOOLEAN),
                    (c, v, extra) -> c.allowDynamic = v,
                    (c, extra) -> c.allowDynamic)
            .add()
            .append(new KeyedCodec<>("DynamicMaxSpeed", Codec.INTEGER),
                    (c, v, extra) -> c.dynamicMaxSpeed = v,
                    (c, extra) -> c.dynamicMaxSpeed)
            .add()
            .append(new KeyedCodec<>("Worlds", new MapCodec<>(WorldRule.CODEC, LinkedHashMap::new)),
                    (c, v, extra) -> c.worlds = v,
                    (c, extra) -> c.worlds)
            .add()
            .build();

    private String mode = "auto";
    private String requirement = "percent";
    private int percent = 50;
    private int count = 1;
    private int minimumSleepers = 1;
    private boolean excludeCreative = true;
    private int afkSeconds = 300;
    private int skipSeconds = 4;
    private int cooldownSeconds = 60;
    private boolean progressMessages = true;
    private boolean showToasts = true;
    private boolean showTitle = false;
    private boolean clearStorms = true;
    private String[] stormWeathers = {"Storm", "Rain", "Snow_Heavy"};
    private boolean allowDynamic = false;
    private int dynamicMaxSpeed = 20;
    private Map<String, WorldRule> worlds = new LinkedHashMap<>();

    public String mode() {
        return mode;
    }

    public String requirement() {
        return requirement;
    }

    public int percent() {
        return percent;
    }

    public int count() {
        return count;
    }

    public int minimumSleepers() {
        return minimumSleepers;
    }

    public boolean excludeCreative() {
        return excludeCreative;
    }

    public int afkSeconds() {
        return afkSeconds;
    }

    public int skipSeconds() {
        return skipSeconds;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public boolean progressMessages() {
        return progressMessages;
    }

    public boolean showToasts() {
        return showToasts;
    }

    public boolean showTitle() {
        return showTitle;
    }

    public boolean clearStorms() {
        return clearStorms;
    }

    public String[] stormWeathers() {
        return stormWeathers == null ? new String[0] : stormWeathers.clone();
    }

    public boolean allowDynamic() {
        return allowDynamic;
    }

    public int dynamicMaxSpeed() {
        return dynamicMaxSpeed;
    }

    public Map<String, WorldRule> worlds() {
        return worlds == null ? Map.of() : worlds;
    }

    // ---- changes made by /lullaby admin ------------------------------------

    public void setPercent(int percent) {
        this.requirement = "percent";
        this.percent = percent;
    }

    public void setCount(int count) {
        this.requirement = "count";
        this.count = count;
    }

    public void setDynamic() {
        this.requirement = "dynamic";
    }

    /** The rule for a world, matched ignoring case, created when it does not exist yet. */
    public WorldRule worldRule(String name) {
        if (worlds == null) {
            worlds = new LinkedHashMap<>();
        }
        for (Map.Entry<String, WorldRule> e : worlds.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)) && e.getValue() != null) {
                return e.getValue();
            }
        }
        WorldRule rule = new WorldRule();
        // The map read from Lullaby.json is read only, so a new entry goes into a copy.
        Map<String, WorldRule> copy = new LinkedHashMap<>(worlds);
        copy.put(name, rule);
        worlds = copy;
        return rule;
    }
}
