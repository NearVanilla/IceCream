# Command Tracker

Records the commands players run, so staff can see who ran what, where and when while moderating or looking into an incident.

Permission: `icecream.modules.commandtracker.commandlog` allows `/commandlog`. Operators have it by default.

## What is recorded

For every command a player runs:

- The player's UUID and their name at the time
- The full command line, with sensitive arguments hidden (see Redaction)
- The world and coordinates they were standing at
- The time
- Whether another plugin cancelled the command (shown as "(cancelled)", since it may not have run)

Console and RCON commands are recorded too when `record-console` is on. Command blocks are never recorded.

Commands are written to `plugins/IceCream/commandtracker.db` (SQLite) on a separate thread, so the server never waits on the disk.

## Looking things up

`/commandlog` (or `/cmdlog`) shows the newest commands first. Add any of these flags to narrow it down. They can be combined, in any order.

| Flag | Short | Example | Shows |
| --- | --- | --- | --- |
| `--player` | `-p` | `--player Steve` | Commands run by that player, under any name they have used. `--player CONSOLE` shows console commands |
| `--command` | `-c` | `--command tp` | That command, whichever alias was typed |
| `--since` | `-s` | `--since 2h` | Commands run in the last 2 hours |
| `--until` | `-u` | `--until 2026-11-20T18:00` | Commands run before that time |
| `--search` | `-f` | `--search "diamond_block"` | Command lines containing that text. Quote it to include spaces |
| `--page` | `-n` | `--page 2` | That page of the results, starting at 1 |

Times are either a duration back from now (`30s`, `15m`, `2h`, `7d`, `2w`, or combined like `1d12h`), or a date or date and time in the server's time zone (`2026-11-20` or `2026-11-20T14:30`).

Examples:

- `/cmdlog -p Steve -s 1d` shows what Steve ran in the last day
- `/cmdlog -c gamemode -s 7d` shows who changed game mode this week
- `/cmdlog -n 2 -s 2026-11-20 -u 2026-11-21` shows page 2 of everything run on 20 November

Hover over an entry for the player's UUID, the exact time and the location. Click it to get a teleport command to that spot in your chat box. Click `[Previous]` and `[Next]` to change page with the same filters.

## Redaction

Some commands carry passwords or private messages. Their arguments are hidden before they are stored, so they never reach the database:

- `redact.all-arguments`: every argument is replaced with `[redacted]`. By default this covers login and register commands, and `/r` and `/reply`
- `redact.after-first-argument`: the first argument, the recipient, is kept and the message is replaced. By default this covers `/msg`, `/tell`, `/w`, `/whisper` and similar

Entries match a command by its name or any of its aliases, so `login` also covers `/l` when that is an alias of the login command.

## Configuration

Under `modules.commandtracker` in `config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Turns the module on or off |
| `record-console` | `false` | Also record console and RCON commands |
| `retention-days` | `30` | Days to keep commands. Older ones are deleted at startup and then every hour. `0` keeps everything |
| `page-size` | `10` | Commands per page of `/commandlog` (1 to 50) |
| `date-format` | `yyyy-MM-dd HH:mm:ss` | How times are shown, as a Java date pattern |
| `ignored-commands` | empty | Commands that are never recorded |
| `tracked-commands` | empty | When not empty, only these commands are recorded |
| `redact.all-arguments` | login commands, `r`, `reply` | Commands whose arguments are all hidden |
| `redact.after-first-argument` | `msg`, `tell`, `w`, `whisper` and similar | Commands whose message is hidden but recipient kept |

Command names are written without the slash. A namespace such as `minecraft:` is ignored, so `tp` also matches `/minecraft:tp`. If a command is in both `ignored-commands` and `tracked-commands`, it is ignored.

## Notes

- Only commands typed by players (or run by clicking chat links) are recorded. Commands that plugins run on a player's behalf are not.
- Redaction only applies to commands recorded after it was configured. Changing it does not alter what is already stored.
- `--command` looks for the command's real name, so it finds `/tp` and `/minecraft:tp` together. Typed lines are kept exactly as typed apart from redaction.
