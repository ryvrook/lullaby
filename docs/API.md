# Lullaby API, version 1

Lullaby 1.1.0 has a small read API for other plugins and fires two events on the engine event bus. It tells you
who is in bed in each world, how many players the world's rule needs, how far the world is towards a skip and how
many nights were skipped. Nothing in it changes Lullaby's behaviour. It is all in the `dev.ryvrook.lullaby.api`
package of the Lullaby jar.

## Linking to Lullaby

Compile against the Lullaby jar as `compileOnly` and list Lullaby in your manifest.

```json
"OptionalDependencies": { "Ryvrook:Lullaby": ">=1.1.0" }
```

Keep every use of the Lullaby types in one class and load that class only after `PluginManager` reported
`Ryvrook:Lullaby` as enabled, so your plugin still loads on a server without Lullaby. Once that class is loaded,
`LullabyApi.isPresent()` tells you whether Lullaby has started.

```java
LullabyApi.onEnable(api -> {
    SleepStatus s = api.status("default");
    getLogger().at(Level.INFO).log("%d of %d in bed, %d nights skipped", s.inBed(), s.needed(), s.nightsSkipped());
});
LullabyApi.onDisable(() -> { /* drop what you kept */ });
```

`onEnable` runs at once when Lullaby is already up, and again every time Lullaby starts. `onDisable` runs every
time it stops. Call `LullabyApi.removeHooks(enable, disable)` when your own plugin stops while Lullaby keeps
running.

A `/plugin reload` of Lullaby loads a new copy of the api classes that your plugin does not see. Two seconds after
it started again, Lullaby reloads every enabled plugin that names it under `Dependencies` or
`OptionalDependencies`, one after another and each after the plugins it reads itself, so they register their
hooks on the new copy. It takes the suite lock in the system property `ryvrook.dependents.lock` for that pass, the
same one TrackerAPI and Coinage use, so a plugin reloaded by two of them in a row starts once. A plugin that uses
these types without listing Lullaby is not reloaded and stays unbound until you reload it yourself.

## LullabyApi

Every method is safe from any thread and returns at once. The numbers are the last count Lullaby made on the
world's thread, which runs every 0.3 seconds while players are in that world.

| Method | Returns |
| --- | --- |
| `static LullabyApi get()` | The running API, or null |
| `static boolean isPresent()` | True while Lullaby is up |
| `static void onEnable(Consumer<LullabyApi>)` | Runs now when up, and after every start |
| `static void onDisable(Runnable)` | Runs every time Lullaby stops |
| `static void removeHooks(Consumer<LullabyApi>, Runnable)` | Drops your hooks, either may be null |
| `static final int API_VERSION` | 1 |
| `int apiVersion()` | The running Lullaby's API version, use this one at run time since javac inlines the constant |
| `String version()` | The Lullaby version, such as `1.1.0` |
| `String mode()` | `native` while Lullaby's slumber systems run, `manual` otherwise |
| `List<String> worlds()` | Worlds counted since start, sorted, removed worlds drop out |
| `SleepStatus status(String world)` | The last count for one world |
| `List<SleepStatus> statuses()` | `status` for every world in `worlds()` |
| `int sleepers(String world)` | Players in bed |
| `int needed(String world)` | Sleepers the rule asks for, at least 1 |
| `double progress(String world)` | 0 to 1, see below |
| `int nightsSkipped(String world)` | Nights skipped in the world, from `stats.json` |
| `int nightsSkipped()` | Nights skipped in all worlds |
| `Map<String, Integer> nightsSkippedByWorld()` | A sorted copy of the per world counts |

A world Lullaby never counted, or one that is empty, answers zeros with `needed` 1. World names are matched
exactly as the engine spells them.

## SleepStatus

A record of plain values.

| Component | Meaning |
| --- | --- |
| `world` | The world name |
| `enabled` | False when the world is left to vanilla through `Worlds.<name>.Enabled` |
| `requirement` | `percent`, `count` or `dynamic` |
| `online` | Every valid player in the world |
| `eligible` | Players the rule counts, without spectators, Creative, idle and ignored players, sleepers always included |
| `inBed` | Eligible players in bed, nodding off or asleep |
| `ready` | Players in bed for 3.2 seconds or more, the ones that count for a skip |
| `needed` | Sleepers the rule asks for |
| `progress` | `inBed / needed` capped at 1, or `inBed / eligible` for a dynamic rule |
| `cooldownSeconds` | Seconds before another skip may start, 0 when none |
| `nightsSkipped` | Nights skipped in this world |

`missing()` gives `needed - inBed`, never below 0.

## Events

Both are synchronous engine events without a key, built only when something listens. Register them like any
global event. They fire on the world's thread, so keep listeners short and hand a web request or a file write to
another thread.

```java
getEventRegistry().registerGlobal(NightSkippedEvent.class, e -> post(e.world(), e.sleepers()));
getEventRegistry().registerGlobal(SleepProgressEvent.class, e -> show(e.world(), e.inBed(), e.needed()));
```

`NightSkippedEvent` fires once for each night Lullaby counts, right after the clock moved to morning and the
sleepers were woken.

| Method | Meaning |
| --- | --- |
| `world()` | The world name |
| `sleepers()` | Players in bed when the night ended, 0 after an admin skip with nobody in bed |
| `nightsSkipped()` | Nights skipped in the world including this one |
| `cause()` | `native`, `manual`, `dynamic`, `vanilla` or `admin` |

The cause is `native` for a Lullaby slumber in native mode, `manual` for the fast forward in manual mode,
`dynamic` for a dynamic night that reached morning, `vanilla` when everyone slept and the vanilla slumber finished
the night, and `admin` for `/lullaby admin skip`. More values may come later, so treat an unknown one as a skip.

`SleepProgressEvent` fires when the number of players in bed changes in a world that may sleep, up or down. It
also fires with 0 when everyone got up, when the night was skipped, when the world emptied and when the sleep
hours ended. It does not depend on `ProgressMessages`. Lullaby counts every 0.3 seconds, so somebody who gets in
and out of bed within one count is not seen.

| Method | Meaning |
| --- | --- |
| `world()` | The world name |
| `requirement()` | `percent`, `count` or `dynamic` |
| `online()`, `eligible()`, `inBed()`, `ready()`, `needed()` | As in `SleepStatus` |
| `progress()` | As in `SleepStatus` |
| `missing()` | `needed - inBed`, never below 0 |

## Versions

`API_VERSION` is 1 in Lullaby 1.1.0. Methods, event fields and record components are only ever added, and each
addition raises the version. Nothing is renamed or removed within the 1.x line.
