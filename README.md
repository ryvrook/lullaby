# Lullaby

Multiplayer sleep for Hytale servers.

In vanilla Hytale the night only ends when every single player in the world is in bed, including players in
Creative mode, spectators and anyone who stepped away from the keyboard. Lullaby lets a percentage or a fixed
number of players skip the night instead. Sleepers keep the vanilla sleep clock, fade and sounds, the rest of
the server sees a short progress line in chat, and the morning arrives with a clear sky. Everything runs on the
server, so players join with an unmodified client.

## Features

- **Percent or count.** Skip the night when 50% of players sleep, or when a fixed number such as 2 do.
- **A minimum number of sleepers**, so on a big server one player cannot skip the night alone unless you
  allow it.
- **Fair counting.** Spectators, players in Creative mode and idle players are left out of the count, and any
  player can opt out with `/lullaby ignore`. A player in bed always counts.
- **Per world rules.** Turn Lullaby off in a lobby or give one world its own percentage or count.
- **Native mode.** Lullaby takes over the vanilla sleep logic, so sleepers see the real sleep clock and hear the
  real success sound. When a future game version changes that logic, Lullaby falls back to manual mode by
  itself.
- **Dynamic nights**, experimental and off by default. Instead of a threshold, time runs faster the more
  players sleep, up to 20 times as fast, until morning. Set `AllowDynamic` to true before using it.
- **Progress messages** such as "Lullaby 2 of 5 players sleeping, 1 more needed", sent once per change, with an
  optional toast for the players still awake.
- **Clear mornings.** Storms and rain are cleared after a skip without forcing the weather.
- **A cooldown** after each skip so nights cannot chain.
- **Nights skipped** are counted per world and shown by `/lullaby`.
- **Admin commands** to skip now, change the requirement, toggle a world and reload the config.

## Server versions

| Lullaby | Hytale server |
|---|---|
| 1.0.0 | 0.7.0 release or newer |

Pre-release servers log a warning that the plugin targets `>=0.7.0`, because the server treats a pre-release
version as outside that range. The plugin still loads.

## Commands

| Command | What it does |
|---|---|
| `/lullaby` | Show the sleep status of your world. The alias is `/sleepers` |
| `/lullaby ignore` | Stop counting yourself for the sleep check, or start again |

`/lullaby` shows the mode, the requirement, how many players are in bed out of how many count, how many more
are needed, the cooldown when one is running and the number of nights skipped in that world.

`/lullaby ignore` is remembered across restarts. An ignored player never holds up a skip. They can still sleep
in a bed, and then they count like any other sleeper.

Every player command is open to everyone. Lullaby does not register `/sleep`, because other mods use it.

## Admin commands

> **Permissions.** Every `/lullaby admin` command is guarded by permission nodes the server generates. A sender
> needs `ryvrook.lullaby.command.lullaby.admin` and the node of the sub command, which is
> `ryvrook.lullaby.command.lullaby.admin.<sub>`, for example `ryvrook.lullaby.command.lullaby.admin.skip`.
> The `set` commands sit one level deeper, for example `ryvrook.lullaby.command.lullaby.admin.set.percent`.
> Grant the nodes to your staff in `permissions.json` or your permission plugin. The server console has every
> permission.

| Command | What it does |
|---|---|
| `/lullaby admin skip` | End the night now in your world. Add `--world <name>` for another world |
| `/lullaby admin set percent <n>` | Need `n` percent of the players, from 0 to 100 |
| `/lullaby admin set count <n>` | Need a fixed number of players |
| `/lullaby admin set dynamic` | Speed the night up with every sleeper instead |
| `/lullaby admin world <name> enabled <true or false>` | Turn Lullaby on or off in one world |
| `/lullaby admin reload` | Re-read `Lullaby.json` |

Every admin command works from the server console. `skip` refuses during the day, so it cannot jump over a
whole day by accident. The `set` and `world` commands write `Lullaby.json` straight away and take effect at
once.

## Install

1. Download `Lullaby-<version>.jar`.
2. Put it in your Mods folder.
   - Windows `%APPDATA%\Hytale\UserData\Mods`
   - macOS `~/Library/Application Support/Hytale/UserData/Mods`
   - Dedicated server, the server's `mods/` directory
3. Start the world or server. The log shows a line such as `Lullaby ready, mode native, 50% of players needed`.

No other mods are required. Lullaby needs the Hytale 0.7.0 release server or newer.

## Configuration

`Lullaby.json` is created in the `Ryvrook_Lullaby` data folder on first start with every key at its default.
Edit it and run `/lullaby admin reload`, or restart the server.

| Key | Default | Meaning |
|---|---|---|
| `Mode` | `"auto"` | `auto` or `native` take over the vanilla sleep systems and fall back to manual if that fails. `manual` leaves them alone |
| `Requirement` | `"percent"` | `percent`, `count` or `dynamic` |
| `Percent` | `50` | Share of eligible players who need to sleep, from 0 to 100, rounded up |
| `Count` | `1` | Number of players who need to sleep when `Requirement` is `count` |
| `MinimumSleepers` | `1` | Never skip with fewer sleepers than this, unless fewer players count at all |
| `ExcludeCreative` | `true` | Leave players in Creative mode out of the count |
| `AfkSeconds` | `300` | Leave players out who have not moved, looked around or chatted for this long. `0` turns it off |
| `SkipSeconds` | `4` | How long the fast forward to morning takes in manual mode, up to `60`. `0` jumps at once |
| `CooldownSeconds` | `60` | Real seconds after a skip before Lullaby will skip again |
| `ProgressMessages` | `true` | Send a chat line to the world when the number of sleepers changes |
| `ShowToasts` | `true` | Also show that line as a toast to the eligible players who are still awake |
| `ShowTitle` | `false` | Show a big "Good morning" title to the world after a skip |
| `ClearStorms` | `true` | Clear stormy weather after a skip |
| `StormWeathers` | `["Storm", "Rain", "Snow_Heavy"]` | A weather whose id contains one of these words counts as stormy, ignoring case |
| `AllowDynamic` | `false` | Unlock the experimental dynamic requirement. While false, a dynamic rule is treated as percent and `set dynamic` is refused |
| `DynamicMaxSpeed` | `20` | How many times as fast the night runs in dynamic mode when every eligible player sleeps |
| `Worlds` | `{}` | Per world overrides, see below |

Values out of range are pulled back in. A `Percent` above 100 becomes 100, a `Count` or `MinimumSleepers`
below 1 becomes 1, a `SkipSeconds` above 60 becomes 60, `DynamicMaxSpeed` stays between 1 and 200, and negative
seconds become 0. An unknown `Mode` means `auto` and an unknown `Requirement` means `percent`.

`ProgressMessages` only speak up when the world has more than one player online, because a player alone
already knows who is in bed.

## Per world overrides

`Worlds` maps a world name, ignoring case, to the keys that differ there. Each entry can set `Enabled`,
`Requirement`, `Percent` and `Count`. Anything left out follows the server wide value.

```json
"Worlds": {
  "lobby": { "Enabled": false },
  "survival": { "Requirement": "count", "Count": 2 },
  "hardcore": { "Percent": 100 }
}
```

A world with `Enabled` set to `false` behaves exactly like vanilla, where everyone has to be in bed.
`/lullaby admin world <name> enabled false` writes such an entry for you.

## Who counts

The threshold is measured against the eligible players in that world.

- A player in bed always counts, both as a sleeper and as eligible.
- Spectators never count.
- Players in Creative mode do not count while `ExcludeCreative` is on.
- Players who have been idle for `AfkSeconds` do not count. Moving, turning the camera and chatting all reset
  the timer.
- Players who ran `/lullaby ignore` do not count.

With `Percent` at 50 and five eligible players, three need to be in bed. With `Count` at 2, two need to be in
bed, or everyone when only one player counts. A player has to lie in bed for about three seconds before they
count as ready, exactly as in vanilla, and a bed has to be claimed before a player can lie down in it.

Lullaby only acts during the vanilla sleep hours, from 19:30 to 04:47 game time, and only when at least one
player is actually asleep.

## How it works

**Native**, the default through `auto`. Vanilla decides when a night ends in two internal systems, one that
starts the slumber once everyone is in bed and one that runs it and wakes everyone at the end. Lullaby replaces
both with its own copies on start. The copies keep every vanilla step, the sleep clock, the grey fade, the
success sound and the moment the clock jumps to morning. They start the slumber as soon as the Lullaby threshold
is met, only put players into the slumber who are actually in bed, and keep it going while enough players stay
in bed. Players who stay awake carry on as usual and see the morning arrive. On shutdown, on `/plugin reload`
and when the mode changes to `manual`, Lullaby puts fresh vanilla systems back.

If anything in that swap fails, for example because a future game version changed the systems, Lullaby logs one
warning, puts vanilla back and runs in manual mode instead. `/lullaby` then shows the mode as
"manual after the native fallback".

**Manual.** Vanilla is left alone. When the threshold is met Lullaby fast forwards the clock to morning over
`SkipSeconds`, then gets the sleepers out of bed the way vanilla does and plays the success sound. If so many
sleepers get up that the threshold is no longer met, the fast forward stops where it is. Sleepers see the grey
fade but not the vanilla sleep clock. When everyone is in bed, vanilla's own skip runs and Lullaby steps
aside, and still counts the night.

**Dynamic** is experimental and needs `AllowDynamic` set to true, otherwise a dynamic rule is read as percent
and the log says so once. It works in both modes. While at least one player sleeps, the clock runs between 1 and
`DynamicMaxSpeed` times as fast in proportion to the share of eligible players asleep. One of ten players
asleep at the default speed makes the night run almost three times as fast. When the clock reaches the wake up
time everyone in bed wakes up and the night counts as skipped. If everyone gets into bed, the normal vanilla
skip takes over.

Every skip, whatever the mode, wakes the sleepers, posts "Lullaby the night was skipped, N players slept" to the
world, clears storms when `ClearStorms` is on, counts the night and starts the cooldown.

## Weather

Hytale picks weather per environment once per game hour from a weighted forecast. After a skip Lullaby looks at
every environment of the world whose current weather is stormy and replaces it with the most likely calm weather
in that environment's forecast for the hour. The next hour change rolls weather as usual, so this only buys a
clear morning. Weather an admin forced with `/weather set` is left alone, and Lullaby never writes the world
config.

## Compatibility

- Needs the Hytale 0.7.0 release server or newer. Lullaby depends on the built in `Hytale:Beds` and
  `Hytale:Weather` plugins, which every server has.
- Native mode replaces two vanilla systems. Another mod that also replaces or changes `StartSlumberSystem` or
  `UpdateWorldSlumberSystem` will clash with it. Set `Mode` to `manual` in that case.
- The vanilla sleep notifications in chat ("Steve is sleeping") are unchanged and still count every player.
  The small counter sleepers see on screen also counts every player, because the client draws it. Lullaby's own
  progress line has the real numbers.
- Lullaby does not register `/sleep`, so it does not clash with mods that do.
- Other mods that move the clock, such as `/time` commands, keep working. Lullaby reads the clock fresh on every
  check.

## Building from source

Building needs JDK 25. The Gradle wrapper is included.

```bash
./gradlew build          # jar lands in build/libs/
./gradlew installMod     # also copies it into your local UserData/Mods folder
```

`installMod` picks the default Mods folder for Windows, macOS or Linux. Pass `-PmodsDir=<path>` to copy the jar
somewhere else.

The build pulls `com.hypixel.hytale:Server` from Hytale's Maven repository as a compile only dependency, so no
manual server jar is needed. `hytale_channel` and `hytale_version` in `gradle.properties` choose the server
artifact, `release` and `0.7.1` by default.

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for the thread model and the headless smoke test.

## Contributing

Issues and pull requests are welcome. Please keep changes focused and make sure `./gradlew build` passes.

The threshold maths lives in `model/Rule.java` and who counts in `sleep/Eligibility.java`. Every decision about
when a night ends is made in `sleep/SleepCoordinator.java`, which the native copies in `nativesys/` ask before
they start or cut short a slumber. New config keys go into `config/LullabyConfig.java` with their default, get
clamped in `config/Settings.java`, and need a row in the configuration table above. For changes to the native
systems or the coordinator, run the headless smoke test in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## License

Lullaby is released under the MIT License. Copyright (c) 2026 ryvrook. See [LICENSE](LICENSE) for the full text.
