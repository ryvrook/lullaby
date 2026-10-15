package dev.ryvrook.lullaby.sleep;

import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSleep;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.ryvrook.lullaby.config.Settings;
import dev.ryvrook.lullaby.data.LullabyData;
import dev.ryvrook.lullaby.model.Requirement;
import dev.ryvrook.lullaby.model.Rule;
import dev.ryvrook.lullaby.model.SleepSnapshot;
import dev.ryvrook.lullaby.weather.StormClearer;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Decides when a night ends. Every method that takes a store runs on that world's thread, called from a
 * Lullaby system tick. It sends the progress lines, runs manual and dynamic skips and answers the native
 * slumber systems.
 */
public final class SleepCoordinator {
    private static final float EVAL_INTERVAL_S = 0.3f;
    private static final float AFK_INTERVAL_S = 1.0f;
    private static final Duration COMPLETED_SLUMBER = Duration.ofHours(1);
    private static final long ERROR_LOG_GAP_MS = 60_000L;

    /** What a native slumber start should do. */
    public enum Start {
        NONE,
        /** The Lullaby threshold is met. */
        LULLABY,
        /** Everyone is ready, the plain vanilla rule. */
        VANILLA
    }

    private final Supplier<Settings> settings;
    private final LullabyData data;
    private final AfkTracker afk;
    private final Announcer announcer;
    private final HytaleLogger logger;
    private final Runnable saveData;
    private final BooleanSupplier nativeActive;
    private final Map<String, WorldState> states = new ConcurrentHashMap<>();
    private volatile long lastErrorLog;

    public SleepCoordinator(Supplier<Settings> settings, LullabyData data, AfkTracker afk, Announcer announcer,
                            HytaleLogger logger, Runnable saveData, BooleanSupplier nativeActive) {
        this.settings = settings;
        this.data = data;
        this.afk = afk;
        this.announcer = announcer;
        this.logger = logger;
        this.saveData = saveData;
        this.nativeActive = nativeActive;
    }

    private WorldState state(World world) {
        return states.computeIfAbsent(world.getName(), name -> new WorldState());
    }

    /** A fresh count for a world. World thread only. */
    public SleepSnapshot snapshot(World world, Store<EntityStore> store) {
        Settings s = settings.get();
        return Eligibility.snapshot(world, store, s, s.ruleFor(world.getName()), data, afk, System.currentTimeMillis());
    }

    /** Seconds left of the cooldown after the last skip in this world, 0 when none. */
    public long cooldownLeft(World world) {
        return secondsLeft(System.currentTimeMillis(), state(world).lastSkipAt, settings.get().cooldownSeconds());
    }

    /** Whole seconds left of a cooldown, rounded up, 0 once it ran out. */
    static long secondsLeft(long now, long lastSkipAt, int cooldownSeconds) {
        long left = lastSkipAt + cooldownSeconds * 1000L - now;
        return left <= 0 ? 0 : (left + 999) / 1000;
    }

    /** True while the cooldown after the last skip runs. A world that never skipped is not cooling. */
    static boolean cooling(long now, long lastSkipAt, int cooldownSeconds) {
        return now - lastSkipAt < cooldownSeconds * 1000L;
    }

    /** True while enough players stay in bed to keep a skip going. Below that it is cut short. */
    static boolean held(SleepSnapshot snap) {
        return snap.inBed() >= snap.needed();
    }

    /** How many times as fast a dynamic night runs, from 1 with nobody asleep to {@code maxSpeed} with everyone. */
    public static double dynamicSpeed(int maxSpeed, double share) {
        return 1.0 + (maxSpeed - 1) * Math.max(0.0, Math.min(1.0, share));
    }

    /** A vanilla slumber that moved the clock by at least an hour finished the night. One cut short barely moves it. */
    static boolean completedSlumber(Instant start, Instant end) {
        return Duration.between(start, end).compareTo(COMPLETED_SLUMBER) >= 0;
    }

    /**
     * What the native start system should do. The Lullaby threshold is only asked for when the world is
     * enabled, the rule is percent or count and no cooldown runs. Everything else falls back to the vanilla
     * rule, every player ready.
     */
    static Start decideStart(Rule rule, boolean cooling, BooleanSupplier thresholdMet, BooleanSupplier everyoneReady) {
        if (rule.enabled() && rule.requirement() != Requirement.DYNAMIC && !cooling && thresholdMet.getAsBoolean()) {
            return Start.LULLABY;
        }
        return everyoneReady.getAsBoolean() ? Start.VANILLA : Start.NONE;
    }

    /** Drops what Lullaby remembers about a world that was removed, such as a finished instance. */
    public void forget(String worldName) {
        if (worldName != null) {
            states.remove(worldName);
        }
    }

    /** Asks the world to skip on its next tick, used by /lullaby admin skip. Safe from any thread. */
    public void requestSkip(World world) {
        state(world).forceSkip = true;
    }

    // ---- the per tick work ------------------------------------------------

    /** Called every tick for every world by the Lullaby tick system. */
    public void tick(World world, Store<EntityStore> store, float dt) {
        try {
            tick0(world, store, dt);
        } catch (RuntimeException | LinkageError e) {
            logError(world, e);
        }
    }

    /** Logs a failure at most once a minute, so a broken tick cannot flood the log. */
    private void logError(World world, Throwable e) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog > ERROR_LOG_GAP_MS) {
            lastErrorLog = now;
            logger.at(Level.WARNING).withCause(e).log("Lullaby tick failed in world %s", world.getName());
        }
    }

    private void tick0(World world, Store<EntityStore> store, float dt) {
        WorldState st = state(world);
        Settings s = settings.get();
        long now = System.currentTimeMillis();
        st.afkTimer += dt;
        if (st.afkTimer >= AFK_INTERVAL_S) {
            st.afkTimer = 0;
            afk.poll(world.getPlayerRefs(), now);
        }
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        WorldSomnolence somnolence = store.getResource(WorldSomnolence.getResourceType());
        if (time == null || somnolence == null) {
            return;
        }
        if (st.forceSkip) {
            st.forceSkip = false;
            forceSkip(world, store, time, somnolence, st, s);
        }
        if (st.manualSkipping) {
            if (somnolence.getState() instanceof WorldSlumber) {
                // vanilla started its own slumber because everyone is in bed, so it finishes the night
                time.cancelInterpolation();
                st.manualSkipping = false;
            } else if (!time.isInterpolating()) {
                finish(world, store, time, st, s, true);
            }
        }
        if (st.dynamicActive) {
            dynamicStep(world, store, time, somnolence, st, s, dt);
        }
        st.evalTimer += dt;
        if (st.evalTimer >= EVAL_INTERVAL_S) {
            st.evalTimer = 0;
            evaluate(world, store, time, somnolence, st, s, now);
        }
    }

    private void evaluate(World world, Store<EntityStore> store, WorldTimeResource time, WorldSomnolence somnolence,
                          WorldState st, Settings s, long now) {
        Rule rule = s.ruleFor(world.getName());
        boolean vanillaSlumber = somnolence.getState() instanceof WorldSlumber;
        if (!nativeActive.getAsBoolean()) {
            observeVanillaSlumber(world, store, time, st, s, rule, vanillaSlumber);
        }
        if (world.getPlayerRefs().isEmpty()) {
            st.lastAnnounced = 0;
            st.dynamicActive = false;
            st.lastSnapshot = null;
            return;
        }
        SleepSnapshot snap = Eligibility.snapshot(world, store, s, rule, data, afk, now);
        st.lastSnapshot = snap;
        if (!rule.enabled() || !WakeTime.canSleep(world)) {
            st.lastAnnounced = 0;
            st.dynamicActive = false;
            return;
        }
        if (snap.inBed() != st.lastAnnounced) {
            if (snap.inBed() > 0 && s.progressMessages() && snap.online() > 1) {
                announcer.progress(world, snap, rule, s);
            }
            st.lastAnnounced = snap.inBed();
        }
        if (st.manualSkipping && !held(snap)) {
            // Too many sleepers got up during a manual fast forward, so it stops where it is, like a native
            // slumber that is cut short.
            time.cancelInterpolation();
            st.manualSkipping = false;
        }
        boolean cooling = cooling(now, st.lastSkipAt, s.cooldownSeconds());
        if (vanillaSlumber || st.manualSkipping || cooling) {
            st.dynamicActive = false;
            return;
        }
        if (rule.requirement() == Requirement.DYNAMIC) {
            if (snap.met() && !st.dynamicActive) {
                st.dynamicTarget = null;
            }
            st.dynamicActive = snap.met();
            return;
        }
        if (!nativeActive.getAsBoolean() && snap.met()) {
            startManual(world, store, time, st, s);
        }
    }

    /**
     * Manual mode leaves vanilla in charge when everyone is in bed. A vanilla slumber that ends after moving
     * the clock by at least an hour finished the night, so it is counted and announced like a Lullaby skip.
     * One that was cut short barely moves the clock.
     */
    private void observeVanillaSlumber(World world, Store<EntityStore> store, WorldTimeResource time, WorldState st,
                                       Settings s, Rule rule, boolean vanillaSlumber) {
        if (vanillaSlumber) {
            if (st.vanillaSlumberStart == null) {
                st.vanillaSlumberStart = time.getGameTime();
                SleepSnapshot last = st.lastSnapshot;
                st.vanillaSlumberSleepers = last == null ? 0 : last.inBed();
            }
            return;
        }
        if (st.vanillaSlumberStart != null) {
            Instant start = st.vanillaSlumberStart;
            st.vanillaSlumberStart = null;
            if (rule.enabled() && completedSlumber(start, time.getGameTime())) {
                skipped(world, store, time, st, s, st.vanillaSlumberSleepers);
            }
        }
    }

    // ---- manual skip ------------------------------------------------------

    private void startManual(World world, Store<EntityStore> store, WorldTimeResource time, WorldState st, Settings s) {
        Instant target = WakeTime.wakeInstant(time.getGameTime(), WakeTime.wakeHour(world));
        st.manualSkipping = true;
        if (s.skipSeconds() <= 0) {
            time.setGameTime(target, world, store);
            finish(world, store, time, st, s, true);
            return;
        }
        time.startDayTimeInterpolation(WakeTime.dayProgress(target), s.skipSeconds(), true, false, world, store);
    }

    /** Morning is here. Wakes the sleepers, plays the success sound and runs the skip effects. */
    private void finish(World world, Store<EntityStore> store, WorldTimeResource time, WorldState st, Settings s,
                        boolean countIt) {
        st.manualSkipping = false;
        st.dynamicActive = false;
        st.dynamicTarget = null;
        int woken = Waker.wake(store, true, Waker.successSound(WakeTime.sleepConfig(world)));
        if (countIt) {
            skipped(world, store, time, st, s, woken);
        }
    }

    private void forceSkip(World world, Store<EntityStore> store, WorldTimeResource time, WorldSomnolence somnolence,
                           WorldState st, Settings s) {
        if (somnolence.getState() instanceof WorldSlumber) {
            somnolence.setState(WorldSleep.Awake.INSTANCE);
        }
        if (st.manualSkipping) {
            time.cancelInterpolation();
        }
        time.setGameTime(WakeTime.wakeInstant(time.getGameTime(), WakeTime.wakeHour(world)), world, store);
        finish(world, store, time, st, s, true);
    }

    // ---- dynamic ----------------------------------------------------------

    /**
     * One tick of a dynamic night. The clock gets extra game time on top of its own pace, so it runs
     * between 1x and DynamicMaxSpeed times as fast depending on the ready share. Reaching the wake up time
     * ends the night like a skip.
     */
    private void dynamicStep(World world, Store<EntityStore> store, WorldTimeResource time, WorldSomnolence somnolence,
                             WorldState st, Settings s, float dt) {
        SleepSnapshot snap = st.lastSnapshot;
        if (snap == null || !snap.met() || !(somnolence.getState() instanceof WorldSleep.Awake)) {
            st.dynamicActive = false;
            return;
        }
        Instant now = time.getGameTime();
        if (st.dynamicTarget == null) {
            st.dynamicTarget = WakeTime.wakeInstant(now, WakeTime.wakeHour(world));
        }
        double speed = dynamicSpeed(s.dynamicMaxSpeed(), snap.readyShare());
        double extraSeconds = (speed - 1.0) * dt * WakeTime.nightRate(world);
        if (extraSeconds <= 0) {
            return;
        }
        Instant next = now.plusNanos((long) (extraSeconds * 1_000_000_000L));
        if (next.isBefore(st.dynamicTarget)) {
            time.setGameTime(next, world, store);
            return;
        }
        time.setGameTime(st.dynamicTarget, world, store);
        finish(world, store, time, st, s, true);
    }

    // ---- native slumber hooks ---------------------------------------------

    /** Asked by the Lullaby start system every 0.3 seconds while the world may sleep and nobody slumbers. */
    public Start nativeStart(World world, Store<EntityStore> store) {
        try {
            return nativeStart0(world, store);
        } catch (RuntimeException | LinkageError e) {
            logError(world, e);
            return Eligibility.everyoneReady(world, store) ? Start.VANILLA : Start.NONE;
        }
    }

    private Start nativeStart0(World world, Store<EntityStore> store) {
        WorldState st = state(world);
        if (st.manualSkipping) {
            return Start.NONE;
        }
        Settings s = settings.get();
        Rule rule = s.ruleFor(world.getName());
        long now = System.currentTimeMillis();
        return decideStart(rule, cooling(now, st.lastSkipAt, s.cooldownSeconds()),
                () -> Eligibility.snapshot(world, store, s, rule, data, afk, now).met(),
                () -> Eligibility.everyoneReady(world, store));
    }

    /** The Lullaby start system began a slumber. */
    public void nativeStarted(World world, Start how) {
        WorldState st = state(world);
        st.lullabySlumber = how == Start.LULLABY;
        st.dynamicActive = false;
    }

    /**
     * Asked every tick of a running native slumber. A Lullaby slumber holds while enough players stay in
     * bed. A vanilla one holds only while nobody is awake, exactly as before.
     */
    public boolean nativeShouldAbort(World world, Store<EntityStore> store) {
        try {
            return nativeShouldAbort0(world, store);
        } catch (RuntimeException | LinkageError e) {
            logError(world, e);
            return Eligibility.someoneAwake(world, store);
        }
    }

    private boolean nativeShouldAbort0(World world, Store<EntityStore> store) {
        WorldState st = state(world);
        if (st.lullabySlumber) {
            Settings s = settings.get();
            Rule rule = s.ruleFor(world.getName());
            if (rule.enabled() && rule.requirement() != Requirement.DYNAMIC) {
                SleepSnapshot snap = Eligibility.snapshot(world, store, s, rule, data, afk, System.currentTimeMillis());
                return !held(snap);
            }
        }
        return Eligibility.someoneAwake(world, store);
    }

    /** A native slumber ended. {@code completed} is false when it was cut short. */
    public void nativeEnded(World world, Store<EntityStore> store, boolean completed, int sleepers) {
        WorldState st = state(world);
        st.lullabySlumber = false;
        if (!completed) {
            return;
        }
        Settings s = settings.get();
        if (!s.ruleFor(world.getName()).enabled()) {
            return;
        }
        WorldTimeResource time = store.getResource(WorldTimeResource.getResourceType());
        skipped(world, store, time, st, s, sleepers);
    }

    // ---- after every skip -------------------------------------------------

    private void skipped(World world, Store<EntityStore> store, WorldTimeResource time, WorldState st, Settings s,
                         int sleepers) {
        st.lastSkipAt = System.currentTimeMillis();
        st.lastAnnounced = 0;
        int total = data.countSkip(world.getName());
        saveData.run();
        int calmed = 0;
        if (s.clearStorms() && time != null) {
            try {
                calmed = StormClearer.clear(store, time, s.stormWeathers());
            } catch (RuntimeException | LinkageError e) {
                logger.at(Level.WARNING).withCause(e).log("Lullaby could not clear the weather in %s", world.getName());
            }
        }
        announcer.skipped(world, sleepers, s);
        logger.at(Level.INFO).log("Lullaby skipped the night in %s, %d slept, %d nights so far, storms cleared in %d environments",
                world.getName(), sleepers, total, calmed);
    }
}
