package dev.ryvrook.lullaby.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * One entry of {@code Worlds} in {@code Lullaby.json}. Every field is optional. A missing field keeps the
 * value of the server wide default, and missing fields are not written back.
 */
public final class WorldRule {

    public static final BuilderCodec<WorldRule> CODEC = BuilderCodec.builder(WorldRule.class, WorldRule::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (r, v, extra) -> r.enabled = v,
                    (r, extra) -> r.enabled)
            .add()
            .append(new KeyedCodec<>("Requirement", Codec.STRING),
                    (r, v, extra) -> r.requirement = v,
                    (r, extra) -> r.requirement)
            .add()
            .append(new KeyedCodec<>("Percent", Codec.INTEGER),
                    (r, v, extra) -> r.percent = v,
                    (r, extra) -> r.percent)
            .add()
            .append(new KeyedCodec<>("Count", Codec.INTEGER),
                    (r, v, extra) -> r.count = v,
                    (r, extra) -> r.count)
            .add()
            .build();

    private Boolean enabled;
    private String requirement;
    private Integer percent;
    private Integer count;

    public Boolean enabled() {
        return enabled;
    }

    public String requirement() {
        return requirement;
    }

    public Integer percent() {
        return percent;
    }

    public Integer count() {
        return count;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
