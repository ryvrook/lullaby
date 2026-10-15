# Lullaby

Vanilla Hytale ends the night only when every player in the world is in bed, including the friend in Creative
and the one who went to make tea. Lullaby ends it when a share of the players sleep, 50% by default, or a fixed
number of them. Sleepers keep the vanilla sleep clock, fade and success sound. It runs on the server only.

## Requirements

Hytale server 0.7.0 release or newer, built and tested against 0.7.1, and Java 25. Lullaby needs no other mod.
It depends on the built in `Hytale:Beds` and `Hytale:Weather` plugins, which every server has.

A pre-release server logs a warning that the plugin targets `>=0.7.0`. The plugin loads anyway.

## Install

Drop `Lullaby-<version>.jar` into `UserData/Mods` for a singleplayer world (`%APPDATA%\Hytale\UserData\Mods` on
Windows, `~/Library/Application Support/Hytale/UserData/Mods` on macOS) or into the server's `mods/` folder.
Start the server and look for `Lullaby ready, mode native, 50% of players needed` in the log. The config is
written to `mods/Ryvrook_Lullaby/Lullaby.json`.

## Commands

| Command | What it does |
|---|---|
| `/lullaby` (alias `/sleepers`) | Mode, requirement, who is in bed out of who counts, how many more are needed, the cooldown and the nights skipped in your world |
| `/lullaby ignore` | Stop counting yourself, or start again. Kept across restarts |
| `/lullaby admin skip [--world <name>]` | End the night now. Refused during the day |
| `/lullaby admin set percent <n>` | Need `n` percent of the players, 0 to 100 |
| `/lullaby admin set count <n>` | Need a fixed number of players |
| `/lullaby admin set dynamic` | Speed the night up with every sleeper. Needs `AllowDynamic` |
| `/lullaby admin world <name> enabled <true or false>` | Turn Lullaby on or off in one world |
| `/lullaby admin reload` | Re-read `Lullaby.json` |

The two player commands are open to everyone. An admin command needs two nodes the server generates,
`ryvrook.lullaby.command.lullaby.admin` and the one for the sub command, such as
`ryvrook.lullaby.command.lullaby.admin.skip` or `ryvrook.lullaby.command.lullaby.admin.set.percent`. Grant
them in `permissions.json` or your permission plugin. The console has all of them. The `set` and `world`
commands write `Lullaby.json` at once.

I left `/sleep` alone because other mods use it.

## Settings

Edit `Lullaby.json` and run `/lullaby admin reload`, or restart.

| Key | Default | What it does |
|---|---|---|
| `Mode` | `"auto"` | `auto` and `native` take over the vanilla sleep systems and fall back to manual if that fails. `manual` leaves vanilla alone |
| `Requirement` | `"percent"` | `percent`, `count` or `dynamic` |
| `Percent` | `50` | Share of eligible players who need to sleep, rounded up |
| `Count` | `1` | Players needed when `Requirement` is `count` |
| `MinimumSleepers` | `1` | Never skip with fewer sleepers than this, unless fewer players count at all |
| `ExcludeCreative` | `true` | Leave Creative players out of the count |
| `AfkSeconds` | `300` | Leave out players who have not moved, turned the camera or chatted for this long. `0` turns it off |
| `SkipSeconds` | `4` | Length of the fast forward in manual mode, up to 60. `0` jumps at once |
| `CooldownSeconds` | `60` | Real seconds after a skip before the next one |
| `ProgressMessages` | `true` | Chat line when the number of sleepers changes, only with two or more players online |
| `ShowToasts` | `true` | Same line as a toast for eligible players still awake |
| `ShowTitle` | `false` | "Good morning" title after a skip |
| `ClearStorms` | `true` | Clear stormy weather after a skip |
| `StormWeathers` | `["Storm", "Rain", "Snow_Heavy"]` | A weather id containing one of these words, any case, is stormy |
| `AllowDynamic` | `false` | Allow the experimental dynamic requirement. While false, dynamic is read as percent |
| `DynamicMaxSpeed` | `20` | How fast a dynamic night runs when every eligible player sleeps, 1 to 200 |
| `Worlds` | `{}` | Per world overrides |

Values out of range are pulled back in. `Percent` above 100 becomes 100, a `Count` or `MinimumSleepers` below 1
becomes 1, negative seconds become 0. An unknown `Mode` reads as `auto` and an unknown `Requirement` as
`percent`.

`Worlds` maps a world name, any case, to `Enabled`, `Requirement`, `Percent` and `Count`. Anything left out
follows the server wide value. A world with `Enabled` false behaves like vanilla.

```json
"Worlds": {
  "lobby": { "Enabled": false },
  "survival": { "Requirement": "count", "Count": 2 },
  "hardcore": { "Percent": 100 }
}
```

## Who counts

A player in bed always counts. Spectators never do. Creative players are out while `ExcludeCreative` is on,
idle players are out after `AfkSeconds`, and so is anyone who ran `/lullaby ignore`. With `Percent` at 50 and
five eligible players, three need to be in bed. A player has to lie down for about three seconds before they
count, same as vanilla, and the bed has to be claimed first.

## How a night ends

In native mode Lullaby replaces the vanilla `StartSlumberSystem` and `UpdateWorldSlumberSystem` with copies that
start the slumber once the threshold is met and only put players who are actually in bed into it. Everything
else is vanilla, the sleep clock, the grey fade, the sound and the jump to morning. Players who stayed awake see
the morning arrive. Vanilla systems go back in on shutdown, on `/plugin reload` and when the mode changes to
`manual`. If the swap fails on some future game version, Lullaby logs one warning, puts vanilla back and runs
manual. `/lullaby` then shows "manual after the native fallback".

Manual mode fast forwards the clock to morning over `SkipSeconds`, wakes the sleepers the vanilla way and plays
the sound. Sleepers get the fade but not the clock. If enough sleepers get up mid skip, the clock stops where it
is.

Dynamic is experimental. While at least one player sleeps the clock runs between 1 and `DynamicMaxSpeed` times
as fast, in proportion to the share asleep. One of ten players asleep at the default makes the night run almost
three times as fast.

After any skip the world gets `Lullaby the night was skipped, 3 players slept`, the cooldown starts and the
night is counted. With `ClearStorms` on, every environment with stormy weather gets the most likely calm weather
from its own forecast for that hour, so a desert does not get forest weather. The next hour rolls weather as
usual, and weather forced with `/weather set` is left alone.

## With the other mods

None. Lullaby does not talk to TrackerAPI, Coinage, Dominion, Chronicle Stats, Feats or Atlas, and none of them
need it. It runs fine next to all six.

## Limits

- Another mod that replaces `StartSlumberSystem` or `UpdateWorldSlumberSystem` clashes with native mode. Set
  `Mode` to `manual`.
- The vanilla chat notice ("Steve is sleeping") and the small counter sleepers see on screen still count every
  player, because vanilla and the client draw them. Lullaby's own line has the real numbers.
- A headless server has no players, so the threshold, the wake up and dynamic nights can only be checked on a
  client with two or more players in the world. I test those by hand. The 50 unit tests cover the maths and the
  decisions.

## Building

JDK 25, wrapper included.

```bash
./gradlew build          # jar in build/libs/, runs the 50 tests
./gradlew installMod     # also copies it into your local UserData/Mods, -PmodsDir=<path> for elsewhere
```

The server jar comes from Hytale's Maven repository as compile only. `hytale_channel` and `hytale_version` in
`gradle.properties` pick it, `release` and `0.7.1` by default. [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) has
the thread model, the source layout and the headless smoke test.

Issues and pull requests are welcome on [GitHub](https://github.com/ryvrook/lullaby). Keep a change to one
thing and keep `./gradlew build` green.

## Licence

MIT, copyright (c) 2026 ryvrook. See [LICENSE](LICENSE).
