package dev.ryvrook.lullaby;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import dev.ryvrook.lullaby.command.LullabyCommand;
import dev.ryvrook.lullaby.config.LullabyConfig;
import dev.ryvrook.lullaby.config.Settings;
import dev.ryvrook.lullaby.data.LullabyData;
import dev.ryvrook.lullaby.nativesys.NativeSystems;
import dev.ryvrook.lullaby.sleep.AfkTracker;
import dev.ryvrook.lullaby.sleep.Announcer;
import dev.ryvrook.lullaby.sleep.SleepCoordinator;
import dev.ryvrook.lullaby.systems.LullabyTickSystem;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Lullaby lets a number or a share of the players skip the night instead of everyone.
 *
 * <p>In native mode it swaps the two vanilla slumber systems for copies that use the configured threshold,
 * so sleepers keep the vanilla sleep clock, fade and sounds. In manual mode, or when the swap fails on a
 * future game version, it leaves vanilla alone and moves the clock itself.
 */
public final class LullabyPlugin extends JavaPlugin {
    private static final long IO_DRAIN_SECONDS = 5L;

    private final Config<LullabyConfig> config;
    private volatile Settings settings;
    private LullabyData data;
    private AfkTracker afk;
    private SleepCoordinator coordinator;
    private NativeSystems natives;
    /** The one thread Lullaby reads and writes files and changes the system registry on. */
    private ExecutorService io;
    private final AtomicBoolean saveQueued = new AtomicBoolean();

    public LullabyPlugin(JavaPluginInit init) {
        super(init);
        this.config = withConfig("Lullaby", LullabyConfig.CODEC);
    }

    public Settings settings() {
        return settings;
    }

    public SleepCoordinator coordinator() {
        return coordinator;
    }

    public LullabyData data() {
        return data;
    }

    /** Dynamic nights are experimental, so a dynamic rule is ignored until AllowDynamic is true. */
    private void warnIfDynamicIgnored() {
        if (settings.dynamicIgnored()) {
            getLogger().at(Level.WARNING).log("Lullaby ignored a dynamic requirement because AllowDynamic is false, "
                    + "using percent instead. Set AllowDynamic to true in Lullaby.json to enable dynamic nights.");
        }
    }

    /** "native" while the Lullaby copies run, "manual" otherwise, with a note after a fallback. */
    public String modeName() {
        boolean active = natives != null && natives.isActive();
        if (active) {
            return "native";
        }
        return settings.mode().wantsNative() ? "manual after the native fallback" : "manual";
    }

    @Override
    protected void setup() {
        config.save();
        settings = Settings.from(config.get());
        warnIfDynamicIgnored();
        io = newIoExecutor();
        data = new LullabyData(getDataDirectory(), getLogger());
        data.load();
        afk = new AfkTracker();
        coordinator = new SleepCoordinator(this::settings, data, afk, new Announcer(getLogger()), getLogger(),
                this::saveDataSoon, () -> natives != null && natives.isActive());
        natives = new NativeSystems(coordinator, getLogger());

        getCommandRegistry().registerCommand(new LullabyCommand(this));

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
        getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
        getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onWorldRemoved);
        getEventRegistry().registerAsyncGlobal(EventPriority.LATE, PlayerChatEvent.class,
                future -> future.thenApply(this::onPlayerChat));

        getEntityStoreRegistry().registerSystem(new LullabyTickSystem(coordinator));
    }

    @Override
    protected void start() {
        applyMode();
        touchOnlinePlayers();
        getLogger().at(Level.INFO).log("Lullaby ready, mode %s, %s", modeName(), settings.defaultRule().describe());
    }

    @Override
    protected void shutdown() {
        // The IO thread stops first so a reload still in its queue cannot install the copies again after
        // they were removed. close() also refuses any install that slips through after a slow drain.
        stopIo();
        if (natives != null) {
            natives.close();
        }
        if (data != null) {
            data.save();
        }
    }

    /** Installs or removes the native copies to match the configured mode. Never call on a world thread. */
    private void applyMode() {
        if (settings.mode().wantsNative()) {
            natives.install();
        } else {
            natives.uninstall();
        }
    }

    // ---- config changes ---------------------------------------------------

    /** Re-reads Lullaby.json on the IO thread, switches mode when needed and completes with a reply. */
    public CompletableFuture<Message> reload() {
        CompletableFuture<Message> result = new CompletableFuture<>();
        try {
            io.execute(() -> {
                try {
                    config.load().join();
                    settings = Settings.from(config.get());
                    warnIfDynamicIgnored();
                    applyMode();
                    result.complete(Announcer.line("Lullaby.json reloaded, mode " + modeName() + ", "
                            + settings.defaultRule().describe() + ".", Announcer.SUCCESS));
                } catch (RuntimeException e) {
                    getLogger().at(Level.WARNING).withCause(e).log("Lullaby could not reload its config");
                    result.complete(Announcer.line("Lullaby.json could not be read, see the server log.", Announcer.ERROR));
                }
            });
        } catch (RejectedExecutionException e) {
            result.complete(Announcer.line("Lullaby is shutting down.", Announcer.ERROR));
        }
        return result;
    }

    /** Changes the config on the IO thread, writes Lullaby.json and completes with {@code done}. */
    public CompletableFuture<Message> updateConfig(Consumer<LullabyConfig> change, String done) {
        CompletableFuture<Message> result = new CompletableFuture<>();
        try {
            io.execute(() -> {
                try {
                    change.accept(config.get());
                    config.save().join();
                    settings = Settings.from(config.get());
                    warnIfDynamicIgnored();
                    result.complete(Announcer.line(done, Announcer.SUCCESS));
                } catch (RuntimeException e) {
                    getLogger().at(Level.WARNING).withCause(e).log("Lullaby could not save its config");
                    result.complete(Announcer.line("Lullaby.json could not be saved, see the server log.", Announcer.ERROR));
                }
            });
        } catch (RejectedExecutionException e) {
            result.complete(Announcer.line("Lullaby is shutting down.", Announcer.ERROR));
        }
        return result;
    }

    /** Queues one write of the data files. A write still waiting is not queued twice. */
    public void saveDataSoon() {
        if (!saveQueued.compareAndSet(false, true)) {
            return;
        }
        try {
            io.execute(() -> {
                saveQueued.set(false);
                data.save();
            });
        } catch (RejectedExecutionException e) {
            saveQueued.set(false); // shutting down, the shutdown save covers it
        }
    }

    // ---- players ----------------------------------------------------------

    private void onPlayerReady(PlayerReadyEvent event) {
        try {
            Ref<EntityStore> ref = event.getPlayerRef();
            if (ref == null || !ref.isValid()) {
                return;
            }
            PlayerRef player = ref.getStore().getComponent(ref, PlayerRef.getComponentType());
            if (player != null) {
                afk.touch(player.getUuid(), System.currentTimeMillis());
            }
        } catch (RuntimeException e) {
            getLogger().at(Level.FINE).withCause(e).log("Lullaby could not read a joining player");
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef player = event.getPlayerRef();
        if (player != null) {
            afk.forget(player.getUuid());
        }
    }

    /** Instance and portal worlds come and go, so their state is dropped with them. */
    private void onWorldRemoved(RemoveWorldEvent event) {
        if (event.getWorld() != null) {
            coordinator.forget(event.getWorld().getName());
        }
    }

    private PlayerChatEvent onPlayerChat(PlayerChatEvent event) {
        try {
            if (event.getSender() != null) {
                afk.touch(event.getSender().getUuid(), System.currentTimeMillis());
            }
        } catch (RuntimeException e) {
            getLogger().at(Level.FINE).withCause(e).log("Lullaby could not read a chat message");
        }
        return event;
    }

    /** After a /plugin reload nobody fires PlayerReady for players already online, so they start active here. */
    private void touchOnlinePlayers() {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                return;
            }
            long now = System.currentTimeMillis();
            for (PlayerRef player : universe.getPlayers()) {
                afk.touch(player.getUuid(), now);
            }
        } catch (RuntimeException e) {
            getLogger().at(Level.FINE).withCause(e).log("Lullaby could not read the players already online");
        }
    }

    // ---- IO thread --------------------------------------------------------

    private ExecutorService newIoExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread thread = new Thread(task, "Lullaby-IO");
                    thread.setDaemon(true);
                    return thread;
                }) {
            @Override
            protected void afterExecute(Runnable task, Throwable failure) {
                super.afterExecute(task, failure);
                if (failure != null) {
                    getLogger().at(Level.WARNING).withCause(failure).log("Lullaby IO task failed");
                }
            }
        };
        return executor;
    }

    /** Lets queued saves finish, then stops the IO thread. */
    private void stopIo() {
        if (io == null) {
            return;
        }
        io.shutdown();
        try {
            if (!io.awaitTermination(IO_DRAIN_SECONDS, TimeUnit.SECONDS)) {
                getLogger().at(Level.WARNING).log("Lullaby file thread did not finish within %d seconds", IO_DRAIN_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
