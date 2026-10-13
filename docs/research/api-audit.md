# Lullaby API audit for Hytale server 0.7.0

Every fact below was read with javap 25 from the HytaleServer.jar of the 0.7.0 release install, or from Assets.zip with unzip, or from the Feats, Atlas and Dominion sources. Class names drop the `com.hypixel.hytale.` prefix in prose. Code blocks use full names where an import matters.

Bytecode listings used for this audit are kept next to this file in `javap/` (full) and `slim/` (condensed) for anyone who wants to re-check a claim.

## 0. The short answer

Vanilla skips the night only when every player in the world is in bed. There is no count or percentage setting anywhere in the jar or in Assets.zip. The only "50 percent" in the code is a chat notification threshold.

A plugin cannot make vanilla start the slumber early. `UpdateWorldSlumberSystem` aborts a running slumber on its very next tick when any player in the world is awake, and the abort dismounts the sleepers. So writing a `WorldSlumber` into the world resource while anyone is awake makes things worse.

That leaves two workable designs, both verified against bytecode.

1. Drive the time yourself (least invasive). Leave the vanilla systems alone. When the Lullaby threshold is met, call `WorldTimeResource.startDayTimeInterpolation` or `setDayTime` and then wake the sleepers the same way vanilla does. Sleepers see the vanilla grey fade but not the vanilla sleep clock.
2. Replace the two vanilla world systems. Unregister `StartSlumberSystem` and `UpdateWorldSlumberSystem` through `EntityStore.REGISTRY.unregisterSystem(Class)` and register Lullaby copies whose ready check and abort check use the threshold. Sleepers then get the real vanilla sleep clock, success sound and wake up. On shutdown Lullaby registers fresh vanilla instances again. This is possible because every constructor and type getter involved is public, but it needs a live smoke test.

To prevent a skip, the clean options are to block the bed interaction (cancel `UseBlockEvent.Pre` on beds, or the gameplay config tag `BlockedRootInteractionTags ["Type=Bed"]` that vanilla instances use), or to keep the vanilla systems unregistered.

## 1. The vanilla sleep pipeline

### 1.1 Registration

`builtin.beds.BedsPlugin` (manifest id `Hytale:Beds`, depends on `Hytale:Mounts` and `Hytale:InteractionModule`) registers in `setup()`, in this order.

```java
// components and resource
ComponentType<EntityStore, PlayerSomnolence> playerSomnolenceComponentType   // registerComponent(PlayerSomnolence.class, PlayerSomnolence::new), no codec, not persisted
ComponentType<EntityStore, SleepTracker>     sleepTrackerComponentType
ResourceType<EntityStore, WorldSomnolence>   worldSomnolenceResourceType     // registerResource(WorldSomnolence.class, WorldSomnolence::new), not persisted

// systems
new RegisterTrackerSystem(PlayerRef type, SleepTracker type)
new WakeUpOnDismountSystem(MountedComponent type, PlayerSomnolence type)
new EnterBedSystem(MountedComponent type, PlayerRef type)
new UpdateSleepPacketSystem(PlayerRef type, PlayerSomnolence type, SleepTracker type, WorldSomnolence type, WorldTimeResource type)
new StartSlumberSystem(PlayerSomnolence type, WorldSomnolence type, WorldTimeResource type)
new UpdateWorldSlumberSystem(PlayerSomnolence type, WorldSomnolence type, WorldTimeResource type, MountedComponent type)
new SleepNotificationSystem()

// interaction type
Interaction.CODEC.register("Bed", BedInteraction.class, BedInteraction.CODEC)
```

Public accessors.

```java
public static BedsPlugin BedsPlugin.get();
public ComponentType<EntityStore, PlayerSomnolence> getPlayerSomnolenceComponentType();
public ComponentType<EntityStore, SleepTracker> getSleepTrackerComponentType();
public ResourceType<EntityStore, WorldSomnolence> getWorldSomnolenceResourceType();
public static ComponentType<EntityStore, PlayerSomnolence> PlayerSomnolence.getComponentType();
public static ComponentType<EntityStore, SleepTracker> SleepTracker.getComponentType();
public static ResourceType<EntityStore, WorldSomnolence> WorldSomnolence.getResourceType();
```

Lullaby's manifest should list `"Hytale:Beds": "*"` and `"Hytale:Weather": "*"` under Dependencies so `BedsPlugin.get()` is set before Lullaby's setup.

### 1.2 Components

`builtin.beds.sleep.components.PlayerSleep` is a sealed interface. Its only permitted subclasses are the three below, so a plugin cannot add a fourth state.

```java
public enum PlayerSleep.FullyAwake implements PlayerSleep { INSTANCE }
public record PlayerSleep.NoddingOff(java.time.Instant realTimeStart) implements PlayerSleep {
    public static PlayerSomnolence createComponent();          // new PlayerSomnolence(new NoddingOff(Instant.now()))
}
public record PlayerSleep.Slumber(java.time.Instant gameTimeStart) implements PlayerSleep {
    public static PlayerSomnolence createComponent(WorldTimeResource time);   // gameTimeStart = time.getGameTime()
}
```

`PlayerSomnolence implements Component<EntityStore>`.

```java
public static final PlayerSomnolence AWAKE;     // shared instance holding FullyAwake
public PlayerSomnolence();                      // FullyAwake
public PlayerSomnolence(PlayerSleep state);
public PlayerSleep getSleepState();
```

The state field is private with no setter. State changes always replace the whole component with `putComponent`. After a player has slept once the component stays on the entity holding `FullyAwake`. A player who never slept has no `PlayerSomnolence` at all.

`SleepTracker` only remembers the last `UpdateSleepState` packet sent to that player so packets are sent on change. `RegisterTrackerSystem` (a `HolderSystem`) calls `holder.ensureComponent(SleepTracker)` for every entity with `PlayerRef`.

### 1.3 World resource

`builtin.beds.sleep.resources.WorldSleep` is sealed with two permitted subclasses.

```java
public enum WorldSleep.Awake implements WorldSleep { INSTANCE }

public final class WorldSlumber implements WorldSleep {
    public WorldSlumber(Instant startInstant, Instant targetInstant, float irlDurationSeconds);
    public Instant getStartInstant();
    public Instant getTargetInstant();
    public InstantData getStartInstantData();
    public InstantData getTargetInstantData();
    public float getProgressSeconds();
    public void incrementProgressSeconds(float dt);   // clamps at irlDurationSeconds
    public float getIrlDurationSeconds();
    public SleepClock createSleepClock();             // packet piece shown to sleepers
}

public class WorldSomnolence implements Resource<EntityStore> {
    public WorldSomnolence();                         // state = Awake.INSTANCE
    public WorldSleep getState();
    public void setState(WorldSleep state);           // plain setter, no checks
    public boolean useSleepNotificationCooldown(long nowMs, long cooldownMs);
    public void resetNotificationCooldown();
}
```

### 1.4 How a player enters bed

1. `builtin.beds.interactions.BedInteraction extends SimpleBlockInteraction` runs `interactWithBlock`. It finds or creates the `RespawnBlock` block entity for the bed.
2. If the bed's `RespawnBlock.getOwnerUUID()` equals the player's UUID, it calls `BlockMountAPI.mountOnBlock(ref, commandBuffer, blockPos, centre)`. On `BlockMountAPI.Mounted` it does `commandBuffer.putComponent(ref, PlayerSomnolence type, PlayerSleep.NoddingOff.createComponent())` and then `commandBuffer.run(store -> SleepNotificationSystem.maybeDoNotification(store, false))`.
3. If the bed has another owner the player gets `server.customUI.respawnPointClaimed` and nothing else. If the bed has no owner the respawn point pages open (`SetNameRespawnPointPage`, `SelectOverrideRespawnPointPage` or `OverrideNearbyRespawnPointPage`) and the player does not lie down. So a player must claim a bed before they can sleep in it.
4. `EnterBedSystem` is a `RefChangeSystem<EntityStore, MountedComponent>`. On add or set with `getBlockMountType() == BlockMountType.Bed` it calls `CanSleepInWorld.check(world)`. If the result is negative it only sends a message (`server.interactions.sleep.gameTimePaused`, `server.interactions.sleep.notWithinHours` with `sleepAtTheseHours`, or `server.interactions.sleep.disabled`) and plays the fail sound. It does not remove the NoddingOff state, so outside sleep hours a player still lies in bed as NoddingOff.

Note that the NoddingOff state is set even when sleeping is not currently allowed.

### 1.5 How a player leaves bed

`WakeUpOnDismountSystem` is a `RefChangeSystem<EntityStore, MountedComponent>`. On `onComponentRemoved` with `getBlockMountType() == BlockMountType.Bed` it does `commandBuffer.putComponent(ref, PlayerSomnolence type, PlayerSomnolence.AWAKE)`. That is the only player side wake path besides the end of a slumber.

### 1.6 Can sleep in this world

```java
public final class builtin.beds.sleep.systems.world.CanSleepInWorld {
    public static CanSleepInWorld.Result check(World world);
}
public interface CanSleepInWorld.Result { boolean isNegative(); }
public enum CanSleepInWorld.Status implements Result { CAN_SLEEP, GAME_TIME_PAUSED }
public record CanSleepInWorld.NotDuringSleepHoursRange(LocalDateTime worldTime, SleepConfig sleepConfig) implements Result  // always negative
```

Order of checks. `world.getWorldConfig().isGameTimePaused()` gives `GAME_TIME_PAUSED`. Then `SleepConfig.isWithinSleepHoursRange(worldTime.getGameDateTime())` false gives `NotDuringSleepHoursRange`. Otherwise `CAN_SLEEP`.

### 1.7 What decides the skip (StartSlumberSystem)

`StartSlumberSystem extends DelayedSystem<EntityStore>` with `SYSTEM_INTERVAL_S = 0.3f` and `NODDING_OFF_DURATION = Duration.ofMillis(3200)`. Each 0.3 s per world it does the following.

1. Return if `world.getPlayerRefs()` is empty.
2. Return if `CanSleepInWorld.check(world).isNegative()`.
3. Return unless `WorldSomnolence.getState() == WorldSleep.Awake.INSTANCE`.
4. Return unless `isEveryoneReadyToSleep`. That loops over every `PlayerRef` in `world.getPlayerRefs()`. A ref that is null or invalid is skipped. For each valid ref it calls the public helper below and returns false on the first player who is not ready.

```java
public static boolean StartSlumberSystem.isReadyToSleep(ComponentAccessor<EntityStore> acc, Ref<EntityStore> ref);
// ref null or invalid -> true
// no PlayerSomnolence  -> false
// FullyAwake           -> false
// NoddingOff           -> Instant.now() is after realTimeStart + 3.2 s
// Slumber              -> true

public static boolean StartSlumberSystem.canNotifyOthersAboutTryingToSleep(ComponentAccessor<EntityStore> acc, Ref<EntityStore> ref);
// same, but NoddingOff counts immediately
```

5. When everyone is ready it computes the wake instant and starts the slumber.

```java
Instant now = worldTime.getGameTime();
Instant target = computeWakeupInstant(now, sleepConfig.getWakeUpHour());  // today at WakeUpHour (hour plus minutes), plus one day if that is not after now
float irl = computeIrlSeconds(now, target);  // (float) ceil(max(3.0, TimeUnit.MILLISECONDS.toHours(millis between) / 6.0))
worldSomnolence.setState(new WorldSlumber(now, target, irl));
store.forEachEntityParallel(PlayerSomnolence type, (i, chunk, cb) -> {
    cb.putComponent(chunk.getReferenceTo(i), PlayerSomnolence type, PlayerSleep.Slumber.createComponent(worldTime));
    // plays sleepConfig.getSounds().getSuccessIndex() as a 2D UI sound to that PlayerRef
});
worldSomnolence.resetNotificationCooldown();
```

So vanilla requires every player in the world, including Creative players and spectators. There is no exclusion of any game mode, of AFK players or of players without a bed. The query in the last step is every entity with a `PlayerSomnolence` component, which includes players who slept earlier and are now `FullyAwake`. A copy of this system that does not require everyone must only put `Slumber` on players who are actually ready.

### 1.8 How the time jump happens (UpdateWorldSlumberSystem)

`UpdateWorldSlumberSystem extends TickingSystem<EntityStore>` runs every tick per world.

```java
if (!(worldSomnolence.getState() instanceof WorldSlumber slumber)) return;
slumber.incrementProgressSeconds(dt);
boolean done = slumber.getProgressSeconds() >= slumber.getIrlDurationSeconds();
boolean someoneAwake = isSomeoneAwake(store, PlayerSomnolence type);
if (!done && !someoneAwake) return;
worldSomnolence.setState(WorldSleep.Awake.INSTANCE);
Instant wake = start + (progress / irlDuration) * (target - start);   // computeWakeupTime, nanos
worldTime.setGameTime(wake, world, store);
store.forEachEntityParallel(PlayerSomnolence type, (i, chunk, cb) -> {
    if (state instanceof PlayerSleep.Slumber) {
        cb.putComponent(ref, PlayerSomnolence type, PlayerSomnolence.AWAKE);
        MountedComponent m = chunk.getComponent(i, MountedComponent type);
        if (m != null && m.getControllerType() == MountController.BlockMount) cb.tryRemoveComponent(ref, MountedComponent type);
    }
});
```

`isSomeoneAwake` returns true when any valid player ref in `world.getPlayerRefs()` has no `PlayerSomnolence` or has `FullyAwake`. It returns false when the world has no players.

Consequences.

- The jump is a single `setGameTime` at the end, not a fast forward. World time keeps its normal pace during the slumber.
- The slumber lasts `ceil(max(3, whole game hours / 6))` real seconds. The default night from 19.5 to 4.79 is about 9 game hours, so 3 real seconds. Even a 23 hour skip is 4 seconds.
- If anyone wakes or anyone awake is present, the slumber ends at once, time is set to the proportional point (close to the start), and all `Slumber` players are woken and dismounted. This is why a plugin must never write a `WorldSlumber` while a non sleeper is in the world unless it has replaced this system.
- What wakes everyone is this lambda. `WakeUpOnDismountSystem` then fires on the removed mount and writes `AWAKE` again, which is harmless.

### 1.9 Client packets (UpdateSleepPacketSystem)

`UpdateSleepPacketSystem extends DelayedEntitySystem<EntityStore>` at 0.25 s, query `PlayerRef and PlayerSomnolence and SleepTracker`. Per player it builds an `UpdateSleepState` and sends it only if it differs from the last one (`SleepTracker.generatePacketToSend`).

```java
public class protocol.packets.world.UpdateSleepState implements ToClientPacket {   // PACKET_ID 157
    public boolean grayFade; public boolean sleepUi; public SleepClock clock; public SleepMultiplayer multiplayer;
    public UpdateSleepState(boolean grayFade, boolean sleepUi, SleepClock clock, SleepMultiplayer multiplayer);
}
public class protocol.packets.world.SleepClock { public InstantData startGametime, targetGametime; public float progress, durationSeconds; }
public class protocol.packets.world.SleepMultiplayer { public int sleepersCount; public int awakeCount; public UUID[] awakeSample; }
```

- `FullyAwake` gets `(false, false, null, null)`.
- `NoddingOff` gets `(false,false,null,null)` when sleeping is not allowed. Otherwise `grayFade` turns true 1200 ms after lying down, `clock` is set only while the world is in `WorldSlumber`, and `multiplayer` is set once the player counts as ready. `SleepMultiplayer` counts ready versus not ready over all players in the world and lists up to 5 awake UUIDs (none when more than 5). It is null when the world has one player.
- `Slumber` gets `(true, true, clock, null)`.

Lullaby cannot change how the client draws these. With the vanilla systems in place the "awake" count shown to sleepers will include players Lullaby excludes. Treat that as cosmetic.

### 1.10 Notifications (SleepNotificationSystem)

`SleepNotificationSystem extends DelayedSystem<EntityStore>` at 1 s. Constants `SMALL_SERVER_PLAYER_COUNT = 4`, `BIG_SERVER_SLEEPERS_RATIO = 0.5d`, `COLOR = "#5AB5B5"`.

```java
public static void maybeDoNotification(Store<EntityStore> store, boolean loop);
public static Message getSleeperName(Ref<EntityStore> ref);   // DisplayNameComponent, else PlayerRef username, else "???"
```

It does nothing while sleeping is not allowed or while a slumber runs. Otherwise it counts sleepers with `canNotifyOthersAboutTryingToSleep`. With 4 or fewer players and at least one sleeper, or with more than 4 players and at least half sleeping, every awake player hears the notification sound and gets one of `server.interactions.sleep.notificationSingle`, `notificationSingleEnter`, `notificationSmall` (param `sleepers`) or `notificationBig` (params `sleepers`, `total`). The loop is gated by `SleepSoundsConfig.isNotificationLoopEnabled()` (default true) and a cooldown of `NotificationCooldownSeconds` (default 30). The 0.5 ratio is only for this message, it never decides a skip.

### 1.11 Config in the jar and in Assets.zip

`server.core.asset.type.gameplay.sleep.SleepConfig` (codec keys `WakeUpHour` float default 5.5, `AllowedSleepHoursRange` double[2] default null meaning no restriction, `Sounds`). Read only, there are no setters.

```java
public float getWakeUpHour();
public double[] getAllowedSleepHoursRange();
public SleepSoundsConfig getSounds();
public LocalTime getSleepStartTime();
public boolean isWithinSleepHoursRange(LocalDateTime t);    // ((h - start + 24) % 24) <= ((end - start + 24) % 24), h fractional
public Duration computeDurationUntilSleep(LocalDateTime t);
// reached via world.getGameplayConfig().getWorldConfig().getSleepConfig()
```

`SleepSoundsConfig` keys `Success` (SFX_Sleep_Success), `Fail` (SFX_Sleep_Fail), `Notification` (SFX_Sleep_Notification), `NotificationLoop` (SFX_Sleep_Notification_Loop), `NotificationCooldownSeconds` (30), `NotificationLoopEnabled` (true), with `getSuccessIndex()`, `getFailIndex()`, `getNotificationIndex()`, `getNotificationLoopIndex()`.

`Server/GameplayConfigs/Default.json` in Assets.zip contains this, which every other gameplay config inherits through `"Parent": "Default"`.

```json
"World": {
  "DaytimeDurationSeconds": 1728,
  "NighttimeDurationSeconds": 1152,
  "Sleep": { "WakeUpHour": 4.79, "AllowedSleepHoursRange": [19.5, 4.79] }
}
```

`ForgottenTemple.json` and `TheDeepworks.json` set `"BlockedRootInteractionTags": ["Type=Bed"]`. A search of every class in the jar for sleep percent, ratio, threshold or required sleepers found nothing, and no gameplay config has such a key. The core commands `SleepCommand`, `SleepOffsetCommand` and `SleepTestCommand` under `server.core.command.commands.utility.sleep` tune the tick thread's `Thread.sleep` (`TickingThread.SLEEP_OFFSET`) and have nothing to do with beds.

### 1.12 Where a plugin can intervene

| Lever | Verdict |
| --- | --- |
| A config value for a count or percent | Does not exist |
| `WorldSomnolence.setState(new WorldSlumber(...))` | Public and works, but `UpdateWorldSlumberSystem` aborts it next tick if anyone is awake and dismounts the sleepers. Only safe when everyone is ready or the system is replaced |
| A method to start slumber early | None. `checkIfEveryoneIsReadyToSleep` and `isEveryoneReadyToSleep` are private |
| A new `PlayerSleep` or `WorldSleep` subtype | Impossible, both interfaces are sealed |
| Putting awake players into `NoddingOff` or `Slumber` to fool the check | Works for the check but those players get the grey fade and sleep UI on their screen, so it is not acceptable |
| Disabling a vanilla system | Possible with `EntityStore.REGISTRY.unregisterSystem(StartSlumberSystem.class)` (see section 9.4) |
| Driving time directly | Fully supported through `WorldTimeResource` (section 2) |
| Blocking beds | `UseBlockEvent.Pre` cancel, or gameplay config `BlockedRootInteractionTags` |
| Watching bed entry and exit | Own `RefChangeSystem` on `MountedComponent` (like `EnterBedSystem`) or on `PlayerSomnolence` |

Recommended wake routine for design 1, matching vanilla, run inside a system with a `CommandBuffer` or on the world thread.

```java
PlayerSomnolence s = store.getComponent(ref, PlayerSomnolence.getComponentType());
if (s != null && !(s.getSleepState() instanceof PlayerSleep.FullyAwake)) {
    cb.putComponent(ref, PlayerSomnolence.getComponentType(), PlayerSomnolence.AWAKE);
    MountedComponent m = store.getComponent(ref, MountedComponent.getComponentType());
    if (m != null && m.getControllerType() == com.hypixel.hytale.protocol.MountController.BlockMount) {
        cb.tryRemoveComponent(ref, MountedComponent.getComponentType());
    }
    SoundUtil.playSoundEvent2dToPlayer(playerRef, sleepConfig.getSounds().getSuccessIndex(), SoundCategory.UI);
}
```

In design 1 Lullaby should stand aside when `WorldSomnolence.getState()` is a `WorldSlumber` (vanilla is already skipping because everyone is in bed) and should call `cancelInterpolation()` if a vanilla slumber starts while its own interpolation runs.

## 2. Time of day

### 2.1 Classes

`server.core.modules.time.TimeModule` registers `WorldTimeResource` (per world game clock) and `TimeResource` (server clock with time dilation).

```java
public static ResourceType<EntityStore, WorldTimeResource> WorldTimeResource.getResourceType();
WorldTimeResource time = world.getEntityStore().getStore().getResource(WorldTimeResource.getResourceType());
```

Reading.

```java
public Instant getGameTime();               // field is volatile
public LocalDateTime getGameDateTime();     // UTC
public int getCurrentHour();
public float getDayProgress();              // second of day / 86400, so 0.0 is midnight and 0.5 is noon
public double getSunlightFactor();
public int getMoonPhase();
public boolean isDayTimeWithinRange(double from, double to);   // in day progress units, wraps when from > to
public boolean isScaledDayTimeWithinRange(double, double);
public Instant dayProgressToInstant(double progress);           // today at that progress
public boolean isInterpolating();
public static double getSecondsPerTick(World world);           // 86400 / (day + night real seconds), 30.0 by default
```

Writing.

```java
public void setGameTime(Instant t, World world, ComponentAccessor<EntityStore> acc);  // setGameTime0, updateMoonPhase, broadcastTimePacket
public void setDayTime(double progress, World world, ComponentAccessor<EntityStore> acc);
   // 0..1 else IllegalArgumentException. Target is today at progress, plus one day if that is before now, so it always moves forward
public void startDayTimeInterpolation(double progress, double seconds, boolean forward, boolean pauseOnComplete, World world, ComponentAccessor<EntityStore> acc);
   // forward true picks the next occurrence, false the previous. seconds <= 0 sets at once. Otherwise each tick advances
   // gameTime by (remaining nanos * dt / remainingSeconds) through setGameTime, so clients see a smooth fast forward
public void cancelInterpolation();
public void setGameTime0(Instant t);        // raw set without packets, avoid
public void broadcastTimePacket(ComponentAccessor<EntityStore> acc);
public void sendTimePackets(PlayerRef p);
```

Interpolation runs before the paused check inside `tick`, so it still advances in a world with paused time.

### 2.2 Units, day length, night and sunrise

- The clock is a `java.time.Instant` starting at `ZERO_YEAR = 0001-01-01T00:00:00Z`, UTC. A game day is 86400 game seconds (`SECONDS_PER_DAY`, `HOURS_PER_DAY = 24`).
- `DAYTIME_PORTION_PERCENTAGE = 0.6f`, `DAYTIME_SECONDS = 51840`, `NIGHTTIME_SECONDS = 34560`, `SUNRISE_SECONDS = 17280`. So day runs from 04:48 to 19:12 game time and night from 19:12 to 04:48.
- Real length comes from `World.getDaytimeDurationSeconds()` and `getNighttimeDurationSeconds()`, which use the per world override (`WorldConfig.getDaytimeDurationSecondsOverride()`) or the gameplay config (1728 and 1152 by default). A default day is 2880 real seconds (48 minutes), night is 1152 real seconds (19.2 minutes), and both parts run at 30 game seconds per real second.
- The sunrise constant is `WorldTimeResource.SUNRISE_SECONDS` (17280, 4.8 h). The vanilla `/time dawn` uses `(24 - 24*0.6) / 2 = 4.8` hours and `/time dusk` uses `4.8 + 14.4 = 19.2`. Vanilla's `WakeUpHour` is 4.79 and sleep is allowed from 19.5.
- A sensible Lullaby "is night" test is `SleepConfig.isWithinSleepHoursRange(time.getGameDateTime())`, or day progress outside `[0.2, 0.8]`.

### 2.3 Examples in the jar

`server.core.modules.time.commands.TimeCommand` with subcommands `SetTimeHourCommand`, `SetTimePeriodCommand` (enum `TimeOfDay` Dawn alias day and morning, Midday alias noon, Dusk alias night, Midnight), `TimePauseCommand`, `TimeDilationCommand`. Both setters extend `AbstractWorldCommand`, call `setPermissionGroups("hytale:WorldEditor")`, and do this.

```java
WorldTimeResource r = store.getResource(WorldTimeResource.getResourceType());
r.setDayTime(hour / WorldTimeResource.HOURS_PER_DAY, world, store);
```

`builtin.triggervolumes.effect.builtin.TimeEffect` is the one caller of `startDayTimeInterpolation(targetDayProgress, durationSeconds, forward, pauseOnComplete, world, store)`.

## 3. Weather

`builtin.weather.WeatherPlugin` (manifest id `Hytale:Weather`).

```java
public class builtin.weather.resources.WeatherResource implements Resource<EntityStore> {
    public static ResourceType<EntityStore, WeatherResource> getResourceType();
    public Int2IntMap getEnvironmentWeather();             // environment index -> weather index, mutable live map
    public int getWeatherIndexForEnvironment(int envIndex);
    public int getForcedWeatherIndex();                    // 0 means not forced
    public int getEffectiveWeatherIndex(int envIndex);     // forced if non zero, else the environment's
    public void setForcedWeather(String weatherIdOrNull);  // null clears. Resource only, not saved
    public boolean consumeForcedWeatherChange();
    public boolean compareAndSwapHour(int hour);           // true and stores the hour when it changed
}
public static IndexedLookupTableAssetMap<String, Weather> Weather.getAssetMap();   // getIndex(id), getAsset(index)
public static IndexedLookupTableAssetMap<String, Environment> Environment.getAssetMap();
public IWeightedMap<WeatherForecast> Environment.getWeatherForecast(int hour);
public String WeatherForecast.getWeatherId(); public int getWeatherIndex(); public double getWeight();
```

How weather is chosen. `WeatherSystem.TickingSystem` checks every tick. When the forced index is 0 and `compareAndSwapHour(time.getCurrentHour())` reports a new hour, it rerolls every environment with a `FastRandom` seeded from the world seed, the environment's weather seed key and the current `LocalDateTime`, using that hour's weighted forecast, and writes the result into `getEnvironmentWeather()`. Players get the new weather within one second through their `WeatherTracker` component (`playerUpdateDelay` is 1.0 s).

Reading whether it storms. There is no storm flag on `Weather`. Read the environment the player stands in (`WeatherTracker.getEnvironmentId()` or `getWeatherIndex()` on the player's `WeatherTracker` component) and map the weather index to its id with `Weather.getAssetMap().getAsset(index).getId()`. Storm ids in Assets.zip include `Zone1_Storm`, `Zone1_Rain`, `Zone1_Rain_Light`, `Zone1_Swamp_Rain`, `Zone2_Sand_Storm`, `Zone2_Thunder_Storm`, `Zone3_Rain`, `Zone3_Snow_Storm`, `Zone3_Snow_Heavy`, `Zone4_Storm`, `Zone4_Swamp_Storm`, `Zone4_AshWastes_Storm`, `Zone4_Wastes_Rain`, `Zone4_Wastes_Rain_Heavy`, `Zone4_GhostForest_Rain`, `Skylands_Rapid_Marsh_Stormy`. Clear ids include `Zone1_Sunny`, `Zone2_Sunny`, `Skylands_Sunny`. A configurable list of id substrings (Storm, Rain, Snow_Heavy) is the practical test.

Clearing. The vanilla commands are `WeatherSetCommand` (`/weather set <weather>`) and `WeatherResetCommand` (`/weather reset`, alias `clear`). Both call `WeatherSetCommand.setForcedWeather(world, id, store)`, which is protected static and also writes `WorldConfig.setForcedWeather` plus `markChanged()`, so it persists. Better options for Lullaby, all on the world thread.

1. Per environment, after the skip. Call `compareAndSwapHour(time.getCurrentHour())` first so the system does not reroll over you this hour, then for each entry in `getEnvironmentWeather()` whose id is stormy, write the index of the heaviest non stormy forecast from `Environment.getWeatherForecast(hour)`. The next natural hour change rerolls normally.
2. Global and temporary. `setForcedWeather("Zone1_Sunny")` on the resource only, then `setForcedWeather(null)` after a while. This forces the same weather in every environment of the world including caves and other zones, so it looks wrong outside Zone 1.

A time skip already crosses many hours, so vanilla rerolls weather by itself at the next tick. Clearing is only needed to guarantee a clear morning.

## 4. Players and worlds

```java
Universe.get();                                    // server.core.universe.Universe
Map<String, World> getWorlds(); World getWorld(String); World getWorld(UUID); World getDefaultWorld();
Collection<PlayerRef> getPlayers(); PlayerRef getPlayer(UUID); PlayerRef getPlayerByUsername(String, NameMatching); int getPlayerCount();

World.getPlayerRefs();            // unmodifiable view over a ConcurrentHashMap, safe to iterate from any thread
World.getPlayerCount();
World.getNonSpectatorPlayerCount();   // counts valid refs whose archetype lacks Spectating. Reads the store, world thread only
World.getPlayers();               // List<Player>, world thread
World.getName(); World.getWorldConfig(); World.getGameplayConfig(); World.getEntityStore(); World.isAlive();

PlayerRef.getUuid(); getUsername(); getReference();   // Ref<EntityStore>, may be null while changing worlds
PlayerRef.getWorldUuid(); getTransform();              // Transform with getPosition() Vector3d
PlayerRef.getHeadRotation();                           // Rotation3f
PlayerRef.getPacketHandler(); sendMessage(Message); hasPermission(String); hasPermission(String, boolean default)
PlayerRef.getComponent(ComponentType); getComponentConcurrent(ComponentType)
```

A player's world is `Universe.get().getWorld(playerRef.getWorldUuid())`, or on the world thread `ref.getStore().getExternalData().getWorld()`.

Game mode. `protocol.GameMode` has only `Adventure` and `Creative`. Read it with `store.getComponent(ref, Player.getComponentType()).getGameMode()`. Spectator is not a game mode but the component `server.core.modules.entity.component.Spectating` with `static boolean isSpectating(Ref<EntityStore>, ComponentAccessor<EntityStore>)`. Changes arrive as the cancellable ECS event `server.core.event.events.ecs.ChangeGameModeEvent` (`getGameMode()`), handled the way Feats does in `FeatsPlugin.GameModeSystem` with an `EntityEventSystem<EntityStore, ChangeGameModeEvent>` and a re-read on the world thread.

Join and leave.

```java
getEventRegistry().registerGlobal(PlayerReadyEvent.class, e -> ...);       // e.getPlayerRef() is Ref<EntityStore>, e.getPlayer(). Fires again on world change
getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, e -> ...);  // e.getPlayerRef() is PlayerRef, getDisconnectReason()
getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, e -> ...);  // getHolder(), getWorld(), join message control
getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, e -> ...);   // getHolder(), getWorld()
// PlayerConnectEvent and DrainPlayerFromWorldEvent are IAsyncEvent
```

After `/plugin reload` nobody fires `PlayerReadyEvent` for players already online, so load them in `start()` from `Universe.get().getPlayers()` as Feats does in `loadOnlinePlayers`.

AFK signals. There is no last activity timestamp or idle component anywhere in the jar.

- `PlayerMouseMotionEvent` and `PlayerMouseButtonEvent` (both `PlayerEvent<Void>`, cancellable) are dispatched from `InteractionModule.doMouseInteraction`, which `GamePacketHandler.handleMouseInteraction` calls for the client `MouseInteraction` packet. They are only built when a listener exists. How often the client sends that packet is unknown without a client.
- `PlayerInteractEvent` is referenced only by `CameraDemo`, so do not rely on it.
- `PlayerChatEvent` (async) is a reliable activity signal.
- The dependable signal is polling `PlayerRef.getTransform().getPosition()` and `getHeadRotation()` once a second and treating any change as activity. Movement packets update these on the world thread through `GamePacketHandler.handle(ClientMovement)`.

## 5. Messaging

All of these work without an asset pack unless noted.

```java
Message.raw(String); Message.translation(String key); Message.join(Message...);
msg.color("#hex"); msg.bold(true); msg.italic(true); msg.param(String, String|int|long|float|double|boolean|Message); msg.insert(...)

playerRef.sendMessage(Message);
world.sendMessage(Message);                 // every player in that world, hops to the world thread itself, also logged as [Broadcast]
Universe.get().sendMessage(Message);        // everyone
context.sendMessage(Message);               // command reply

// Toast (Feats UnlockNotifier.sendToast)
NotificationUtil.sendNotification(PacketHandler, Message title, Message body, ItemWithAllMetadata icon, NotificationStyle style);
NotificationUtil.sendNotificationToWorld(Message, Message, String iconPath, ItemWithAllMetadata, NotificationStyle, Store<EntityStore>);
NotificationUtil.sendNotificationToUniverse(...);
// icon from new ItemStack("Some_Item_Id", 1).toPacket(). NotificationStyle Default, Danger, Warning, Success

// Big title (Feats uses the 4 argument form)
EventTitleUtil.showEventTitleToPlayer(PlayerRef, Message title, Message subtitle, boolean major);
EventTitleUtil.showEventTitleToPlayer(PlayerRef, Message, Message, boolean, String zone, float duration, float fadeIn, float fadeOut);
EventTitleUtil.showEventTitleToWorld(Message, Message, boolean, String, float, float, float, World);
EventTitleUtil.hideEventTitleFromPlayer(PlayerRef, float fade);
// EventTitleStyle Default, Major, GoblinBreach, VoidEviction

// Sound
SoundUtil.playSoundEvent2dToPlayer(PlayerRef, int index, SoundCategory.UI);
SoundUtil.playSoundEvent2dToPlayer(PlayerRef, String id, SoundCategory);
SoundUtil.playSoundEvent2dToWorld(World, String id, SoundCategory);
SoundEvent.getAssetMap().getIndex("SFX_Sleep_Success");   // vanilla sleep sounds are usable
```

There is no action bar API in 0.7.0. The persistent HUD options are `CustomUIHud` (abstract `build(UICommandBuilder)`, added with `player.getHudManager().addCustomHud(playerRef, hud)`, updated with `hud.update(false, builder)`, removed with `removeCustomHud(playerRef, key)`), which needs `.ui` markup in an asset pack (Feats ships one with `"IncludesAssetPack": true`), and the `HudManager.dev_registerWidget` family, which is marked dev and was not verified. The vanilla sleep chat lines are translation keys (`server.interactions.sleep.*` in `Server/Languages/*/server.lang`) and would also need an asset pack to reword.

## 6. Commands and permissions

```java
public abstract class AbstractCommand {
    protected AbstractCommand(String name, String description);   // also (name), (name, description, boolean)
    public void requirePermission(String node); requirePermission(PermissionQuery);
    public void requireNoPermission();
    protected void setPermissionGroups(String... groups);   // vanilla time commands use "hytale:WorldEditor"
    protected void setPermissionGroup(GameMode);
    public void addAliases(String...); addSubCommand(AbstractCommand); addUsageVariant(AbstractCommand);
    public <D> RequiredArg<D> withRequiredArg(String, String, ArgumentType<D>);
    public <D> OptionalArg<D> withOptionalArg(String, String, ArgumentType<D>);
    public <D> DefaultArg<D> withDefaultArg(String, String, ArgumentType<D>, D, String);
    public FlagArg withFlagArg(String, String);
    public boolean hasPermission(CommandSender);
}
// basecommands
AbstractPlayerCommand  protected abstract void execute(CommandContext, Store<EntityStore>, Ref<EntityStore>, PlayerRef, World);   // runs on the sender's world thread
AbstractWorldCommand   protected abstract void execute(CommandContext, World, Store<EntityStore>);   // has an optional world argument
CommandBase            protected abstract void executeSync(CommandContext);   // works from console
AbstractCommandCollection(String name, String description)                     // parent that only holds subcommands
CommandContext: get(arg), provided(arg), sendMessage(Message), isPlayer(), senderAs(Class), senderAsPlayerRef(), sender()
ArgTypes: BOOLEAN INTEGER STRING GREEDY_STRING FLOAT DOUBLE PLAYER_REF WORLD WEATHER_ASSET GAME_MODE and more
getCommandRegistry().registerCommand(cmd);
```

Feats patterns to copy. The root player command calls `requireNoPermission()` so everyone can use it, otherwise the engine generates a node `<group>.<name>.command.<command>` (Feats notes `ryvrook.feats.command.feats`) and only admins pass. Admin subcommands are an `AbstractCommandCollection` named `admin` whose children are `CommandBase` and never call `requireNoPermission`, so each gets its own generated admin node. Toggles with an argument use `addUsageVariant` with a second command that has a `RequiredArg`. Replies from other threads go back through `Targets.reply`, which uses the sender's world thread.

Admin check in code is `playerRef.hasPermission("ryvrook.lullaby.admin")` or `PermissionsModule.get().hasPermission(uuid, node)`. `PermissionsModule.registerPermission(String node, String... groups)` exists for declaring a node. `getBasePermission()` on the plugin returns the generated base node.

## 7. Config

Pattern from `FeatsConfig` and `FeatsPlugin`.

```java
public static final BuilderCodec<LullabyConfig> CODEC = BuilderCodec.builder(LullabyConfig.class, LullabyConfig::new)
    .append(new KeyedCodec<>("SleepPercent", Codec.INTEGER), (c, v, extra) -> c.sleepPercent = v, (c, extra) -> c.sleepPercent).add()
    .append(new KeyedCodec<>("DisabledWorlds", Codec.STRING_ARRAY), (c, v, extra) -> c.disabledWorlds = v, (c, extra) -> c.disabledWorlds).add()
    .build();

// in the plugin constructor
private final Config<LullabyConfig> config = withConfig("Lullaby", LullabyConfig.CODEC);   // file Lullaby.json in getDataDirectory()
// setup(): config.save();   writes defaults and missing keys
// read:    config.get()       throws IllegalStateException "Config is not loaded" before load
// reload:  config.load().join(); then re-apply cached fields
```

Feats reloads on its own IO thread (`runIo(() -> { config.load().join(); applyConfig(); ... })`) from `/feats admin reload` and replies through `Targets.reply`. Codec types used by Feats are `Codec.BOOLEAN`, `INTEGER`, `STRING`, `STRING_ARRAY`. `Codec.FLOAT`, `Codec.DOUBLE` and `Codec.DOUBLE_ARRAY` exist (vanilla `SleepConfig` uses FLOAT and DOUBLE_ARRAY).

## 8. Threading

- Each `World` is a `TickingThread` and an `Executor`. ECS systems, entity events and `AbstractPlayerCommand.execute` run on it. `world.isInThread()` tells you where you are.
- Anything touching a `Store`, `Ref`, component or resource (`WorldSomnolence`, `WorldTimeResource`, `WeatherResource`, `PlayerSomnolence`, `Player.getGameMode`) must run on that world's thread. Hop with `world.execute(Runnable)`.
- `world.scheduleAfter(Runnable, long, TimeUnit)` schedules on `HytaleServer.SCHEDULED_EXECUTOR` and then queues the task on the world thread, and drops it if the world stopped accepting tasks.
- Safe from any thread are `Universe.get().getPlayers()`, `world.getPlayerRefs()` (concurrent map view), `PlayerRef.getUuid/getUsername/getTransform/getWorldUuid/sendMessage`, `world.sendMessage` (it hops itself), `NotificationUtil` and `EventTitleUtil` sends.
- Feats' once a second work is `HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS)` started in `start()` and cancelled in `shutdown()`. Inside, `MemoryTracker.poll` resolves the player's world from `ref.getStore().getExternalData().getWorld()` and calls `world.execute(() -> read(uuid, ref))`. `getTaskRegistry().registerTask(ScheduledFuture)` also exists for auto cleanup but Feats does not use it.
- Atlas runs its own `ScheduledThreadPoolExecutor` and reads `WorldTimeResource.getCurrentHour()` and `getDayProgress()` inside `world.execute` (`data/LiveState.readOnWorldThread`).
- For Lullaby the cleanest per world, per second loop is an ECS `DelayedSystem` (section 9), because it already runs on the right thread for every world.

## 9. ECS

### 9.1 Base classes

```java
public abstract class component.system.tick.TickingSystem<E> { public abstract void tick(float dt, int systemIndex, Store<E> store); }
public abstract class component.system.DelayedSystem<E> extends TickingSystem<E> {
    public DelayedSystem(float intervalSeconds);
    public abstract void delayedTick(float dt, int systemIndex, Store<E> store);
}
public abstract class component.system.tick.EntityTickingSystem<E> {   // per entity, implements QuerySystem
    public abstract void tick(float dt, int index, ArchetypeChunk<E> chunk, Store<E> store, CommandBuffer<E> cb);
    public Query<E> getQuery();
    public boolean isParallel(int, int);
}
public abstract class component.system.tick.DelayedEntitySystem<E> extends EntityTickingSystem<E> { public DelayedEntitySystem(float interval); }
public abstract class component.system.RefChangeSystem<E, T extends Component<E>> {
    public abstract ComponentType<E, T> componentType();
    public abstract Query<E> getQuery();
    public abstract void onComponentAdded(Ref<E>, T, Store<E>, CommandBuffer<E>);
    public abstract void onComponentSet(Ref<E>, T oldValue, T newValue, Store<E>, CommandBuffer<E>);
    public abstract void onComponentRemoved(Ref<E>, T, Store<E>, CommandBuffer<E>);
}
Query.and(Query...); Query.or(...); Query.not(...); Query.any();   // a ComponentType is itself a Query
getEntityStoreRegistry().registerSystem(system);
```

A world system runs once per world store, so `store.getExternalData().getWorld()` gives the world. Inside a tick, mutate entities through a `CommandBuffer` (`store.forEachEntityParallel(query, (i, chunk, cb) -> cb.putComponent(...))` as vanilla does) and call resource setters such as `WorldTimeResource.setGameTime(..., store)` directly, as `UpdateWorldSlumberSystem` does.

### 9.2 Who is sleeping right now

On the world thread, following vanilla exactly.

```java
Store<EntityStore> store = world.getEntityStore().getStore();
for (PlayerRef p : world.getPlayerRefs()) {
    Ref<EntityStore> ref = p.getReference();
    if (ref == null || !ref.isValid()) continue;
    boolean inBed = StartSlumberSystem.canNotifyOthersAboutTryingToSleep(store, ref);   // NoddingOff or Slumber
    boolean ready = StartSlumberSystem.isReadyToSleep(store, ref);                    // NoddingOff for 3.2 s, or Slumber
}
WorldSleep state = store.getResource(WorldSomnolence.getResourceType()).getState();   // Awake.INSTANCE or WorldSlumber
```

Lullaby should count eligible players (valid ref, not Spectating, not Creative if configured, not AFK, not in a disabled world) and ready players among them, and compare with the configured count or percent.

### 9.3 Hooks for bed events

A `RefChangeSystem<EntityStore, MountedComponent>` with query `Query.and(MountedComponent type, PlayerRef type)` sees bed entry (`onComponentAdded` or `onComponentSet` with `getBlockMountType() == BlockMountType.Bed`) and exit (`onComponentRemoved`). A `RefChangeSystem<EntityStore, PlayerSomnolence>` sees every sleep state change. Both are how vanilla itself listens.

### 9.4 Disabling or replacing vanilla systems (design 2)

```java
public static final ComponentRegistry<EntityStore> EntityStore.REGISTRY;
public void ComponentRegistry.unregisterSystem(Class<? extends ISystem<E>> cls);   // throws IllegalStateException after registry shutdown
public <T extends ISystem<E>> boolean ComponentRegistry.hasSystemClass(Class<T> cls);
```

`unregisterSystem` takes the write lock, removes the system and pushes the change to every live store through `Store.updateData`, the same path plugin loading uses. When `BedsPlugin` later shuts down its proxy checks `hasSystemClass` before unregistering, so a missing class is not an error. To restore vanilla on Lullaby shutdown, register new instances built from public parts.

```java
BedsPlugin beds = BedsPlugin.get();
registry.registerSystem(new StartSlumberSystem(beds.getPlayerSomnolenceComponentType(), beds.getWorldSomnolenceResourceType(), WorldTimeResource.getResourceType()));
registry.registerSystem(new UpdateWorldSlumberSystem(beds.getPlayerSomnolenceComponentType(), beds.getWorldSomnolenceResourceType(), WorldTimeResource.getResourceType(), MountedComponent.getComponentType()));
```

Register the restored instances on `EntityStore.REGISTRY` directly, not through Lullaby's own `getEntityStoreRegistry()` proxy, because the proxy unregisters everything it registered when Lullaby shuts down.

## 10. Unresolved strings and classes

- `~/Projects/dominion/docs/AGENT_BRIEF.md` does not exist. Only `DEVELOPMENT.md`, `LISTING.md` and `TESTING.md` are in that folder. Its API notes were re-verified above where used.
- How often the client sends `MouseInteraction` packets (and so how useful `PlayerMouseMotionEvent` is for AFK) cannot be known from the server jar.
- How the client renders `SleepMultiplayer`, `SleepClock` and `grayFade` is not visible in the server jar.
- `HudManager.dev_registerWidget` and `server.core.modules.ui.UIHudWidget` were not explored. They are named dev.
- `Interaction.CODEC` key `"Bed"` and the `"Type=Bed"` tag format in `BlockedRootInteractionTags` were seen but the tag matching code was not read.
- What other systems (crop growth, spawning, furnaces) do with a large `setGameTime` jump was not checked.
- No unresolved translation keys. Every `server.interactions.sleep.*` key used by the beds code exists in `Server/Languages/en-US/server.lang` (stored there without the `server.` prefix).

## 11. Risks

- Fields without setters. `PlayerSomnolence.state` (replace the component instead), `SleepConfig` and `SleepSoundsConfig` (asset data, read only at runtime), `WorldSlumber` start, target and duration are final.
- Sealed types. `PlayerSleep` and `WorldSleep` cannot be extended.
- Systems that cannot be switched off by config. `StartSlumberSystem`, `UpdateWorldSlumberSystem`, `SleepNotificationSystem` and `UpdateSleepPacketSystem` have no enable flag. Only `NotificationLoopEnabled` in gameplay config silences the notification loop. Removing them means `ComponentRegistry.unregisterSystem`, which is public but touches builtin state and needs a live test, including `/plugin reload` of Lullaby.
- Abort trap. Writing `WorldSlumber` while any player is awake makes vanilla dismount all sleepers on the next tick.
- Vanilla quirk. `StartSlumberSystem` puts `Slumber` on every entity carrying `PlayerSomnolence`, including awake players who slept before. A copy must filter.
- Vanilla counts Creative players and spectators, so a world with an idle admin never skips in vanilla.
- Beds must be claimed. A player cannot sleep in someone else's bed, and the first use of an unowned bed opens the respawn point page instead of lying down.
- Client facing packets. The grey fade, sleep clock and multiplayer counter are drawn by the client from `UpdateSleepState`. In design 1 sleepers get the grey fade with no clock. Sending a custom `UpdateSleepState` would desync `SleepTracker.lastSentPacket` and was not tested.
- `WeatherSetCommand.setForcedWeather` persists to the world config. Use `WeatherResource.setForcedWeather` or the per environment map instead.
- Time skip and weather reroll interact. Rewriting `getEnvironmentWeather()` before the weather system notices the new hour gets overwritten unless `compareAndSwapHour` is called first.
- `World.getNonSpectatorPlayerCount()` reads the store and must run on the world thread even though it looks like a simple getter.
