# Player Status

Shows a status such as AFK, do not disturb or streaming next to player names in the tab list. IceCream sends each player's status to the proxy on the `icecream:status` plugin channel, and the bat tab plugin shows it with its `<statuscode>` placeholder.

Permission: `icecream.modules.playerstatus.use` for the `/status` commands.

## Commands

| Command | Effect |
| --- | --- |
| `/status` | Shows your current status |
| `/status afk` | Shows you as AFK |
| `/status dnd` | Shows you as do not disturb |
| `/status streaming` | Shows you as streaming |
| `/status clear` | Clears your status |

A player has one status at a time, so choosing a new one replaces the old one, and `/status clear` removes it. Your status is forgotten when you leave the server or go into spectator mode, which shows you as gone. While in spectator mode, you have no status.

## Configuration

Under `modules.playerstatus` in `config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Turns the module on or off |

How each status looks is set in bat's `status-codes`, keyed by these ids: `afk`, `do_not_disturb`, `streaming`.

## Plugin message

Each message is the player's UUID (`writeUTF`), the number of statuses (`writeInt`, 0 or 1), then the status id (`writeUTF`). A count of 0 clears the player's status. A message is sent whenever a player's status changes, and when the proxy starts listening for a player after they join.
