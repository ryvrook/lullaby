package dev.ryvrook.lullaby;

import dev.ryvrook.lullaby.api.LullabyApi;
import dev.ryvrook.lullaby.api.SleepStatus;
import dev.ryvrook.lullaby.data.LullabyData;
import dev.ryvrook.lullaby.sleep.SleepCoordinator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** The API over the coordinator and the data files. Reads volatile state only, so it is safe from any thread. */
final class LullabyApiImpl extends LullabyApi {
    private final String version;
    private final Supplier<String> mode;
    private final SleepCoordinator coordinator;
    private final LullabyData data;

    LullabyApiImpl(String version, Supplier<String> mode, SleepCoordinator coordinator, LullabyData data) {
        this.version = version;
        this.mode = mode;
        this.coordinator = coordinator;
        this.data = data;
    }

    static void publish(LullabyApiImpl api) {
        enable(api);
    }

    static void withdraw(LullabyApiImpl api) {
        disable(api);
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public String mode() {
        return mode.get();
    }

    @Override
    public List<String> worlds() {
        return Collections.unmodifiableList(coordinator.worlds());
    }

    @Override
    public SleepStatus status(String world) {
        return coordinator.status(world == null ? "" : world);
    }

    @Override
    public List<SleepStatus> statuses() {
        List<SleepStatus> out = new ArrayList<>();
        for (String w : coordinator.worlds()) {
            out.add(coordinator.status(w));
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public int sleepers(String world) {
        return status(world).inBed();
    }

    @Override
    public int needed(String world) {
        return status(world).needed();
    }

    @Override
    public double progress(String world) {
        return status(world).progress();
    }

    @Override
    public int nightsSkipped(String world) {
        return data.nightsSkipped(world);
    }

    @Override
    public int nightsSkipped() {
        return data.nightsSkippedTotal();
    }

    @Override
    public Map<String, Integer> nightsSkippedByWorld() {
        return Collections.unmodifiableMap(data.nightsByWorld());
    }
}
