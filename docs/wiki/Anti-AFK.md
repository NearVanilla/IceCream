# Anti-AFK

Players who stay AFK for too long are asked to type a short code in chat. Anyone who doesn't type it in time is kicked. It replaces the old Captcha AFK plugin.

Permission: `icecream.modules.antiafk.exempt` skips the check entirely. Operators don't have it by default, so grant it explicitly to staff who should be exempt. Players in spectator mode are also skipped.

## How it works

1. After `timeout-minutes` without deliberate input (5 hours by default), any open menu is closed and the player sees a title, a chat message and an action bar countdown with a 6-character code.
2. The player types the code in normal chat. Case and spaces don't matter. What they type is hidden from everyone else, whether it is right or wrong.
3. A correct code resets the timer. A wrong one repeats the code privately, and there is no limit on attempts.
4. If `answer-seconds` (60 by default) runs out, the player is kicked, and everyone is told if `announce-kick` is on.

Codes never contain 0, O, 1, I or L, so they can't be misread.

## What counts as activity

| Counts | Doesn't count |
| --- | --- |
| Pressing movement, jump, sneak or sprint keys, including steering a vehicle | Being moved by water, pistons, other players or a vehicle |
| Turning the camera at least `min-rotation-degrees` (20 by default), when not riding anything | Small back and forth camera jiggles, and camera turns while riding |
| Chatting and running commands | Holding attack or arm swings |
| Clicking or dragging in an inventory, changing hotbar slot, swapping hands, dropping items | Pressure plates and tripwires |
| Using a different block, entity or item than last time | Using the same block, entity or item over and over (a held fishing rod, an auto-clicker) |
| Respawning | |

While a code is on screen, only typing it correctly clears it. Moving around doesn't.

A player who is still on the death screen after the timeout plus the answer window is kicked without a code, since the death screen hides it.

## Configuration

Under `modules.antiafk` in `config.yml`:

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Turns the module on or off |
| `timeout-minutes` | `300` | Minutes without activity before the code appears (at least 1) |
| `answer-seconds` | `60` | Seconds to type the code (15 to 600) |
| `code-length` | `6` | Characters in the code (4 to 12) |
| `min-rotation-degrees` | `20` | How far the camera must turn to count (1 to 180) |
| `sound` | `minecraft:block.note_block.pling` | Played when the code appears and again with 10 seconds left. Empty for none |
| `announce-kick` | `true` | Tell everyone when a player is kicked |
| `messages.*` | | MiniMessage text. `<code>` and `<seconds>` are filled in, and `<player>` in `kick-announcement` |

An invalid number disables the module and logs the reason at startup. An invalid sound logs a warning and plays nothing.

## Notes

- Keep `player-idle-timeout=0` in `server.properties`. Vanilla's idle kick only looks at movement and would work against this module.
- Works with and without CarbonChat. With CarbonChat, answers are hidden from every channel.
- This is a practical AFK check, not an anti-cheat. A macro that presses different keys will get past it.
