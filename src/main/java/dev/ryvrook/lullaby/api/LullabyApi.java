package dev.ryvrook.lullaby.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The read API of Lullaby for other plugins, who sleeps where, how many a world needs and how many nights were
 * skipped. The two engine bus events {@link NightSkippedEvent} and {@link SleepProgressEvent} carry the moments.
 *
 * <p>Compile against the Lullaby jar as {@code compileOnly} and list {@code Ryvrook:Lullaby} under
 * {@code OptionalDependencies} in your manifest, for example {@code ">=1.1.0"}. Keep every use of these types in
 * one class that you only load after {@link #isPresent()} answered true, so your plugin still loads on a server
 * without Lullaby.
 *
 * <p>Bind from {@link #onEnable(Consumer)}, which runs at once when Lullaby is already up and again every time it
 * starts, and drop your state in {@link #onDisable(Runnable)}. A {@code /plugin reload} of Lullaby loads a new copy
 * of this class that your classes do not see, so two seconds after it started again Lullaby reloads every enabled
 * plugin that lists it in its manifest. A plugin that uses these types without listing Lullaby is not reloaded
 * and stays unbound.
 *
 * <pre>{@code
 * LullabyApi.onEnable(api -> {
 *     SleepStatus s = api.status("default");
 *     log(s.inBed() + " of " + s.needed() + " in bed, " + api.nightsSkipped("default") + " nights skipped");
 * });
 * }</pre>
 *
 * <p>Every method is safe from any thread and returns at once. The numbers come from the last count Lullaby made
 * on the world thread, which runs every 0.3 seconds while players are in the world.
 *
 * <p>Methods and record components are only ever added. {@link #API_VERSION} goes up when they are.
 */
public abstract class LullabyApi {
    /** Raised whenever a method, an event field or a record component is added. */
    public static final int API_VERSION = 1;

    private static final Logger LOG = Logger.getLogger("Lullaby");
    private static volatile LullabyApi current;
    private static final List<Consumer<LullabyApi>> ENABLE = new CopyOnWriteArrayList<>();
    private static final List<Runnable> DISABLE = new CopyOnWriteArrayList<>();

    protected LullabyApi() {
    }

    /** The running API, or null when Lullaby is not loaded or not started yet. */
    public static LullabyApi get() {
        return current;
    }

    /** True while Lullaby is up. */
    public static boolean isPresent() {
        return current != null;
    }

    /** Runs the hook now when Lullaby is up, and every time it starts. */
    public static void onEnable(Consumer<LullabyApi> hook) {
        Objects.requireNonNull(hook, "hook");
        ENABLE.add(hook);
        LullabyApi api = current;
        if (api != null) {
            run(() -> hook.accept(api));
        }
    }

    /** Runs the hook every time Lullaby stops. */
    public static void onDisable(Runnable hook) {
        Objects.requireNonNull(hook, "hook");
        DISABLE.add(hook);
    }

    /** Removes hooks added with {@link #onEnable} and {@link #onDisable}. Either may be null. */
    public static void removeHooks(Consumer<LullabyApi> enable, Runnable disable) {
        if (enable != null) {
            ENABLE.remove(enable);
        }
        if (disable != null) {
            DISABLE.remove(disable);
        }
    }

    /** {@link #API_VERSION} of the running Lullaby, read through the instance so it is never inlined by javac. */
    public abstract int apiVersion();

    /** The Lullaby version, such as 1.1.0. */
    public abstract String version();

    /** "native" while the Lullaby copies of the slumber systems run, "manual" otherwise. */
    public abstract String mode();

    /** The worlds Lullaby has counted since it started, sorted by name. A removed world drops out. */
    public abstract List<String> worlds();

    /** The last count for a world. A world Lullaby never counted, or one without players, gives zeros. */
    public abstract SleepStatus status(String world);

    /** {@link #status} for every world in {@link #worlds()}. */
    public abstract List<SleepStatus> statuses();

    /** Players in bed in the world, nodding off or asleep. */
    public abstract int sleepers(String world);

    /** Sleepers the world's rule asks for out of the players who count. At least 1. */
    public abstract int needed(String world);

    /** How far the world is towards a skip, from 0 to 1, see {@link SleepStatus#progress()}. */
    public abstract double progress(String world);

    /** Nights skipped in the world since stats.json was started. */
    public abstract int nightsSkipped(String world);

    /** Nights skipped in all worlds together. */
    public abstract int nightsSkipped();

    /** Nights skipped per world, a copy sorted by name. */
    public abstract Map<String, Integer> nightsSkippedByWorld();

    /** Lullaby internal. Publishes the API and runs the enable hooks. */
    protected static void enable(LullabyApi api) {
        Objects.requireNonNull(api, "api");
        current = api;
        for (Consumer<LullabyApi> hook : ENABLE) {
            run(() -> hook.accept(api));
        }
    }

    /** Lullaby internal. Withdraws the API and runs the disable hooks. */
    protected static void disable(LullabyApi api) {
        if (api == null || current != api) {
            return;
        }
        current = null;
        for (Runnable hook : DISABLE) {
            run(hook);
        }
    }

    private static void run(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "A Lullaby API hook of another plugin failed", t);
        }
    }
}
