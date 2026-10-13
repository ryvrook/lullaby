package dev.ryvrook.lullaby.model;

/**
 * The resolved rule for one world after the per world overrides are applied.
 *
 * @param enabled     false leaves the world entirely to vanilla, where everyone has to sleep
 * @param requirement percent, count or dynamic
 * @param percent     0 to 100, used by percent
 * @param count       at least 1, used by count
 */
public record Rule(boolean enabled, Requirement requirement, int percent, int count) {

    /**
     * How many sleepers this rule needs out of {@code eligible} players. Never below 1, never above the
     * eligible count, and never below {@code minimumSleepers} unless fewer players are eligible.
     */
    public int needed(int eligible, int minimumSleepers) {
        if (eligible <= 0) {
            return 1;
        }
        int raw = switch (requirement) {
            case PERCENT -> (int) Math.ceil(eligible * (percent / 100.0));
            case COUNT -> count;
            case DYNAMIC -> 1;
        };
        int wanted = Math.max(raw, minimumSleepers);
        return Math.max(1, Math.min(eligible, wanted));
    }

    /** Words for status lines and the start log, such as "50% of players needed". */
    public String describe() {
        return switch (requirement) {
            case PERCENT -> percent + "% of players needed";
            case COUNT -> count + (count == 1 ? " player needed" : " players needed");
            case DYNAMIC -> "dynamic, the night speeds up with every sleeper";
        };
    }
}
