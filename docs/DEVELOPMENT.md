# Developing Lullaby

This guide is for contributors. It covers how Lullaby is put together, which threads do what, how the two
modes replace or sit beside the vanilla sleep logic, and how to smoke test a build on a headless server. The
README covers installing and configuring Lullaby.

## Building

Lullaby needs JDK 25. When no JDK 25 is installed, the foojay toolchain resolver in `settings.gradle.kts` lets
Gradle download one. Point `JAVA_HOME` at any JDK that can run Gradle and build with the wrapper.

```sh
./gradlew build installMod
```

The jar lands in `build/libs/Lullaby-<version>.jar` and `installMod` copies it into the local Mods folder. The
Hytale server artifact comes from the Hytale Maven repository, and `hytale_channel` and `hytale_version` in
`gradle.properties` select it.

## Tests

`./gradlew build` runs 48 JUnit tests under `src/test/java` without a server. The server jar sits on the test
classpath only for the codecs, bson and the logger. They cover the threshold maths in `Rule`, `SleepSnapshot`,
the clamping and per world inheritance in `Settings`, reading and writing `Lullaby.json` through the codec, the
wake up and night maths in `WakeTime`, the start, cooldown, hold and dynamic speed decisions in
`SleepCoordinator`, the progress line, the idle check, the storm words and the two data files with a broken one
kept aside. Anything that needs a store, a world or the registry swap is left to the smoke test below and to a
client with real players.

## Source layout

| Package | What lives there |
| --- | --- |
| `dev.ryvrook.lullaby` | `LullabyPlugin`, the lifecycle, the IO thread, config reloads and player events |
| `config` | `LullabyConfig` and `WorldRule`, the codecs for `Lullaby.json`, and `Settings`, the immutable copy world threads read |
| `model` | `Mode`, `Requirement`, `Rule` with the threshold maths, and `SleepSnapshot` |
| `sleep` | `SleepCoordinator`, `Eligibility`, `AfkTracker`, `Waker`, `WakeTime` and `Announcer` |
| `nativesys` | `NativeSystems`, which swaps the vanilla systems, and the two Lullaby copies |
| `systems` | `LullabyTickSystem`, the per world tick |
| `weather` | `StormClearer` |
| `data` | `LullabyData`, which keeps `ignored.json` and `stats.json` |
| `command` | `/lullaby`, `/lullaby ignore` and `/lullaby admin` |

## Thread model

- Every world runs its own thread. `LullabyTickSystem` is an ECS ticking system, so it runs once per tick on
  each world's thread with that world's store. All reads and writes of players, components, the world clock,
  the sleep resource and the weather resource happen there.
- The coordinator counts players every 0.3 seconds, the same beat as the vanilla slumber start, and polls
  positions for the idle check once a second. Dynamic nights and running manual skips need the full tick rate.
- Waking players uses the store's command buffers through `forEachEntityParallel`, exactly like vanilla, so it
  only ever runs inside a system tick. `/lullaby admin skip` therefore only raises a flag and the next tick of
  that world does the work.
- One daemon thread named `Lullaby-IO` writes `Lullaby.json`, `ignored.json` and `stats.json`, and carries out
  `/lullaby admin reload`. Registry changes for the native swap happen on that thread or in the plugin
  lifecycle, never on a world thread, because a registry change updates every world store.
- Chat lines, toasts, titles and sounds are safe to send from any thread. Command replies from the IO thread go
  back on the sender's world thread.
- Nothing blocks or sleeps on a world thread.
- Per world state lives in a map keyed by world name. A `RemoveWorldEvent` listener drops the entry, so
  instance and portal worlds that come and go do not pile up.

`Settings` is an immutable record behind a volatile field. A reload builds a new one and swaps it in, so a world
thread never sees half a config.

## Vanilla sleep in brief

`Hytale:Beds` decides when a night ends in two world systems.

- `StartSlumberSystem` runs every 0.3 seconds. Inside the sleep hours, when every valid player in the world is
  ready, it writes a `WorldSlumber` with a start, a wake up target and a real duration of about three seconds,
  and puts the slumber state on every entity that has a `PlayerSomnolence` component.
- `UpdateWorldSlumberSystem` runs every tick. It advances the slumber and, when it has run its course or when
  any player is awake, sets the clock to the matching point between start and target and wakes the sleepers by
  writing `PlayerSomnolence.AWAKE` and removing the bed mount.

A player is ready after lying in bed for 3.2 real seconds. There is no setting for a count or a share anywhere
in the engine or the assets, and writing a slumber while someone is awake is undone on the next tick. That is
why Lullaby either replaces both systems or moves the clock itself.

## Native mode

`NativeSystems.install` first builds fresh vanilla instances to prove their constructors still exist, then
unregisters the two vanilla classes from `EntityStore.REGISTRY` and registers `LullabyStartSlumberSystem` and
`LullabyUpdateSlumberSystem`. Both copies are registered on the global registry, not on the plugin's registry
proxy, because the vanilla systems Lullaby puts back must outlive Lullaby. `uninstall` removes the copies and
registers new vanilla instances, each step in its own try.

- The start copy asks `SleepCoordinator.nativeStart`. It answers `LULLABY` when the world is enabled, the rule
  is percent or count, no cooldown runs and the threshold is met. It answers `VANILLA` when every player is
  ready, which keeps vanilla behaviour for disabled worlds, dynamic worlds and the cooldown. Only players who are
  ready get the slumber state.
- The update copy asks `nativeShouldAbort` every tick of a slumber. A Lullaby slumber holds while at least the
  needed number of players stay in bed. A vanilla slumber holds while nobody is awake, as before. A finished
  night also wakes players still nodding off, a cut short one only wakes sleepers, as vanilla does.
- Any exception during install logs one warning, restores vanilla and leaves Lullaby in manual mode.
- `/lullaby admin reload` with a changed `Mode` installs or uninstalls on the IO thread. A registry change
  waits for the write side of the registry's data update lock, which every world holds for reading while it
  ticks, so calling it from a world thread would deadlock.
- Shutdown stops the IO thread first and then calls `NativeSystems.close`, which puts vanilla back and refuses
  any later install, so a reload still queued on the IO thread cannot leave the copies registered after
  Lullaby is gone.
- Lullaby cannot touch world state from the shutdown thread. When the plugin is reloaded or stopped while a
  Lullaby slumber runs and someone is awake, the fresh vanilla update system cuts that slumber short on its
  next tick, exactly as vanilla would, so the sleepers wake close to the time they lay down and the night is
  not skipped.
- A failure inside `nativeStart` or `nativeShouldAbort` is logged at most once a minute and answered with the
  plain vanilla rule, so a Lullaby bug can never stop a night from ending the vanilla way.

## Manual mode

The coordinator checks the threshold every 0.3 seconds. When it is met it calls
`WorldTimeResource.startDayTimeInterpolation` towards the vanilla wake up time over `SkipSeconds`, or sets the
time at once when that is 0. When the interpolation ends it wakes everyone in bed with `Waker` and plays the
vanilla success sound. If the players in bed drop below the needed number during the fast forward, the
interpolation is cancelled where it is and nothing is counted. If vanilla starts its own slumber meanwhile,
Lullaby cancels its interpolation and lets vanilla finish. A vanilla slumber that moved the clock by at least an hour is counted as a skipped
night.

## Dynamic nights

Dynamic is gated by `AllowDynamic`. `Settings.from` turns a dynamic rule into percent while the switch is
false, records that in `dynamicIgnored` so the plugin logs one warning, and `/lullaby admin set dynamic`
refuses. While the rule is dynamic, the world may sleep, no cooldown runs and enough players are ready, every tick adds
`(speed - 1) * dt * nightRate` game seconds through `setGameTime`, where speed grows from 1 to
`DynamicMaxSpeed` with the ready share and `nightRate` is 34560 game seconds over the world's night length.
Reaching the wake up time wakes everyone and counts as a skip. It never touches the slumber resource, so it
works the same in native and manual mode, and it steps aside while a slumber runs.

## Smoke test on a headless server

You need `HytaleServer.jar` and `Assets.zip` from a Hytale install. The commands below use `$HYTALE` for the
folder that holds `Server/HytaleServer.jar` and `Assets.zip`, and `$JAVA` for a Java 25 binary.

1. Make an empty server folder, create `mods` in it and copy the built jar there. Do not pass `--mods` for the
   same folder or the plugin loads twice.
2. Give the server a `permissions.json` containing `{"users":{},"groups":{}}`.
3. Start the server and pipe console commands into it. Without `--bare` it generates a world, which lets the
   admin commands act on it. Keep the pipe open long enough for the server to stop cleanly.

```sh
cd /path/to/testserver
( sleep 75; printf 'time midnight\n'; sleep 3; printf 'lullaby admin skip\n'; sleep 3; printf 'time\n'; \
  sleep 3; printf 'lullaby admin reload\n'; sleep 4; printf 'plugin reload Ryvrook:Lullaby\n'; sleep 10; \
  printf 'stop\n'; sleep 40 ) | \
  "$JAVA" -jar "$HYTALE/Server/HytaleServer.jar" --assets "$HYTALE/Assets.zip" \
  --auth-mode offline --disable-sentry --bind 127.0.0.1:5533 > boot.log 2>&1
grep -n -e 'Lullaby' -e 'ryvrook' boot.log
```

What to expect in `boot.log`.

- `Lullaby replaced the vanilla slumber systems with its own copies` and
  `Lullaby ready, mode native, 50% of players needed` during start.
- `Lullaby skipped the night in default` after the admin skip, and `time` reporting about 04:48.
- `Lullaby restored the vanilla slumber systems` on `/plugin reload` and on `stop`, each followed by a new
  `replaced` line on reload.
- `mods/Ryvrook_Lullaby/Lullaby.json` with every key, and `stats.json` with the skipped night.
- No stack trace that mentions `dev.ryvrook`.

To check the switch between modes, change `Mode` to `manual` in `Lullaby.json` while the server runs and send
`lullaby admin reload`. Expect the `restored` line and `mode manual` in the reply. Change it back and reload
again for the `replaced` line.

Some engine messages are expected and not caused by Lullaby. The engine logs
`Skipping pack at <DataDir>: missing or invalid manifest.json` for data folders such as `mods/Ryvrook_Lullaby`
when it scans for asset packs. It is harmless, and Lullaby keeps only its own JSON files there so it never
looks like a pack. A console `plugin reload` prints
`Failed to reload plugin` even though the plugin is enabled again a moment later, and a `--bare` server on
0.7.0 logs `Listeners is empty after starting ServerManager` with no mods installed at all. Lullaby still loads
and swaps the systems on a bare server, so `--bare` is a quick boot check, while the world run above is the full
check.

A headless server has no players, so it cannot show the threshold, wake up or dynamic behaviour. I check those
on a client with two or more players in the world.

## Pull requests

Keep each change focused on one thing and make sure `./gradlew build` passes with no new warnings. A change to
a rule in `model`, `config` or the static decisions of `SleepCoordinator` comes with a test. For changes to the
native systems or the coordinator, run the smoke test above and say in the pull request what you checked. Player facing text uses plain sentences and commas.
