# Changelog

Every notable change to Lullaby is recorded here. The format follows Keep a Changelog and the project uses
semantic versioning.

## 1.1.0 - 2026-10-16

Nothing changes for players and no setting changed, so updating is a jar swap.

### Added

- A read API for other plugins in `dev.ryvrook.lullaby.api`, version 1. `LullabyApi` gives the sleepers, the
  needed sleepers and the progress of each world, the nights skipped per world and in total, and the mode, with
  static `onEnable` and `onDisable` hooks. See `docs/API.md`.
- `NightSkippedEvent` and `SleepProgressEvent` on the engine event bus, with plain values only.
- A `/plugin reload` of Lullaby reloads the enabled plugins that list it in their manifest two seconds later, under
  the same suite lock as TrackerAPI and Coinage.
- 17 more unit tests, 67 in all.

### Changed

- The skip log line names the cause and reads `sleepers 3, nights skipped 12` instead of `1 nights so far`.

## 1.0.1 - 2026-10-15

Nothing changes for players and no setting changed, so updating is a jar swap.

### Changed

- The skip log line says how many environments had their storms cleared.
- `./gradlew build` runs 50 JUnit tests over the threshold maths, the config, the night check and the skip
  decisions.

## 1.0.0 - 2026-10-13

First release. Needs the Hytale 0.7.0 release server or newer.

### Added

- Skip the night when a percentage of players (50% by default) or a fixed number of players is in bed, with
  `MinimumSleepers` as a floor.
- Fair counting. Spectators, players in Creative mode, players idle for `AfkSeconds` and players who ran
  `/lullaby ignore` are left out, and a player in bed always counts.
- Native mode, the default through `auto`, which replaces the vanilla `StartSlumberSystem` and
  `UpdateWorldSlumberSystem` with threshold aware copies so sleepers keep the vanilla sleep clock, fade and
  sounds. Vanilla is restored on shutdown, on `/plugin reload` and when the mode changes.
- Automatic fallback to manual mode with one warning when the native swap fails on a future game version.
- Manual mode, which fast forwards the clock to morning over `SkipSeconds` and wakes the sleepers the vanilla
  way, and steps aside when vanilla is already skipping because everyone is in bed.
- Dynamic nights, experimental and off by default behind `AllowDynamic`, where time runs up to `DynamicMaxSpeed`
  times as fast in proportion to the share of players asleep.
- Per world overrides of `Enabled`, `Requirement`, `Percent` and `Count` under `Worlds`.
- Progress lines in chat once per change, with optional toasts for players still awake, a skip line, and an
  optional "Good morning" title.
- Clear mornings. Stormy weather is replaced with the most likely calm weather of each environment after a skip,
  without forcing the weather.
- A cooldown after each skip and a per world count of skipped nights.
- `/lullaby` (alias `/sleepers`) and `/lullaby ignore` for every player.
- `/lullaby admin skip`, `set percent`, `set count`, `set dynamic`, `world <name> enabled` and `reload`, each
  guarded by its own admin node that the engine makes for it.
