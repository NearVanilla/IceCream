# Trial Vault Reset Design (Issue 110)

## Intent

The server uses the ForgetfulTrialVault plugin so players can loot a trial vault again. It misses
resets (for example the vault at `7668, 3, 2414`), depends on Rtag for raw NBT edits, and has known
bugs: two config keys are silently ignored, a reset can wipe every player's loot record on a vault,
and loots that happened before it was installed are never reset. An IceCream module replaces it.

## Goals

- A player who looted a vault can use a matching key on it once the reset delay has passed, and the
  vault becomes lootable for them again.
- Works for normal and ominous vaults.
- Vaults looted before the module existed (no recorded loot time) can be reset straight away.
- Loot times survive restarts without a separate data file.

## Non-goals

- Importing the old plugin's `Vaults.dat`. Unrecorded loots are resettable straight away, so the
  import is not needed.
- Background auto-reset when no one interacts with the vault.
- Admin commands, locales, or per-player permissions.

## Anti-goals

- Wiping or changing any other player's loot record on a vault.
- Letting a player loot a vault more than once per delay.

## Approaches

1. Key-click reset with loot times in the vault's persistent data (picked). The player clicks the
   vault with its key. If the delay has passed, the module removes them from the vault's rewarded
   players and cancels that click. The vault lights up within about a second and the next click
   opens it as normal. Uses only the Paper API (`org.bukkit.block.Vault#removeRewardedPlayer`), and
   the data lives with the block.
2. Proximity auto-reset. Resets eligible players when they approach, so the vault lights up on its
   own and opens in one click. Needs a background scan of players near vaults and drops the "with a
   key" behaviour the issue asks for.
3. Same-click reset. Also forces the vault state to `ACTIVE` during the click so vanilla accepts the
   key immediately. Saves one click, but depends on vanilla vault internals mid-interaction and
   cannot be checked in tests.

Approach 1 was picked because it is reliable and uses public API only. Vanilla refuses keys while a
vault is `INACTIVE`, so the extra click is unavoidable without approach 3.

## Design

### Recording loot times

- Listen to `BlockDispenseLootEvent` at `MONITOR` with `ignoreCancelled = true`, for blocks of type
  `VAULT` with a non-null player.
- One tick later, take a fresh `Vault` block state and store the current time for that player in
  its `PersistentDataContainer`, then `update()`. The write waits a tick so the snapshot cannot
  overwrite vanilla's own update to the rewarded players list.
- While writing, drop stored times for players no longer in `getRewardedPlayers()`.

### Resetting

On `PlayerInteractEvent` (right-click on a `VAULT` block, item type equal to the vault's
`getKeyItem()` type):

| Situation | Result |
| --- | --- |
| Player not in rewarded players | Do nothing, vanilla handles the key. |
| Rewarded, no recorded time | Reset. |
| Rewarded, delay passed | Reset. |
| Rewarded, delay not passed | Send the waiting message with the time left. Vanilla handles the click (fail sound). |
| Vault is `UNLOCKING` or `EJECTING` | Do nothing. Vanilla marks the player rewarded as unlocking starts, a tick before the loot time is recorded, so a reset then would allow a second loot. |

Reset means `removeRewardedPlayer(uuid)`, remove that player's stored time, `update()`, cancel the
interaction so the key is not used, and send the reset message.

### Config

```yaml
trialvaultreset:
  enabled: true
  # Minutes after looting a vault before a player can use a key to reset it. 1440 = 24 hours.
  reset-delay-minutes: 1440
  messages:
    reset: '<green>This vault has been reset for you. Use your key again.</green>'
    waiting: '<gray>You can reuse this vault in <white><time></white>.</gray>'
```

A negative delay is treated as 0. Messages are MiniMessage templates, with `<time>` filled in using
`Placeholder.unparsed(...)`.

### Structure

- `TrialVaultResetModule` follows the existing module pattern, toggled by
  `modules.trialvaultreset.enabled`.
- A pure decision class (rewarded, recorded time, now, delay) returns reset, wait with time left,
  or ignore. Unit tested without a server.
- A small duration formatter for the time left (for example `3h 12m`, `45s`). Unit tested.
- A listener that wires the events to the decision class and the vault block state.

### Tests

- Decision class: every row of the table above, plus delay 0 and negative delay.
- Duration formatter.
- Listener through MockBukkit where vault block states are supported. Any test MockBukkit cannot
  run must be visible as skipped, not counted as passing.

## Left to the builder

- Exact message wording and colours.
- The `NamespacedKey` and data layout for the stored times.
- How the time left is rounded.
