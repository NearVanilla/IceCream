# DeathDB Module Design

## Goal

Add a `deathDb` module to IceCream that logs every player death (time, world, position and a full
inventory snapshot) to SQLite, and lets staff browse and restore those inventories through a chest
GUI. It is a modern reimplementation of the standalone DeathDB plugin
(`D:\Projects\Java\DeathDB`), with its defects fixed and its two commands collapsed into one GUI
driven command.

## Non-goals

- Importing data from the old DeathDB `data.db`. The schema and file name differ, no migration.
- Recording XP, death cause, killer or the ender chest.
- Partial item restore, teleport-to-death, or a back button between the preview and the list.
- Restoring to an offline player. Inventory writes need a loaded `Player`.

## User-facing behaviour

### Command

| Item | Value |
|---|---|
| Command | `/restoredeath <player>` |
| Alias | `/rd <player>` |
| Permission | `icecream.modules.deathdb.restoredeath` (declared in the Cloud annotation only) |
| Sender | Players only. The GUI needs a viewer, so the console gets a "players only" message. |
| Target | Any player that is online or has played before. Offline targets can be browsed. |

The target must be online at the moment the restore button is clicked.

### Death list GUI (54 slots)

```
 slots  0-44   one skull per death, newest first (45 per page)
 slot  45      previous page arrow (only when a previous page exists)
 slot  49      paper: page x of y, total recorded deaths
 slot  53      next page arrow (only when a next page exists)
 other row 5   gray filler pane
```

Each skull is named with the death time and carries lore with the world and block position. Left
clicking a skull opens the preview for that death. Paging reopens the list at the new page.

### Death preview GUI (54 slots)

```
 row 0 (0-8)    0 info clock | 2 helmet | 3 chestplate | 4 leggings | 5 boots | 7 offhand | rest filler
 rows 1-3       main inventory, 27 slots, same order as in game
 row 4 (36-44)  hotbar, 9 slots
 row 5 (45-53)  48 red pane: Cancel | 50 green pane: Restore | rest filler
```

- Empty armor and offhand slots show a labelled gray pane so staff can tell "empty" from "missing".
- Cancel closes the GUI and changes nothing.
- Restore replaces the target's inventory (storage, armor and offhand) with the snapshot. The items
  they were carrying are dropped at their feet, as in the old plugin. The restore is logged to the
  console with the staff name, target name and record id.
- The green pane uses `LIME_STAINED_GLASS_PANE` because the vanilla `GREEN` pane is dark and hard to
  tell from red for some players. It is a one line swap if dark green is preferred.

### Read-only guarantee

Both GUIs are display only. While either is the top inventory of a player, every click and drag is
cancelled, including clicks in the player's own inventory, shift clicks, number keys and double
click collection. Only a plain left click on a top inventory button triggers an action.

## Fixes over the old plugin

| Old defect | New behaviour |
|---|---|
| Blocking SQLite calls on the main thread | All database work runs on one dedicated thread, results return to the main thread via the scheduler |
| `ResultSet` and statements never closed | try-with-resources everywhere |
| `createDeathsTable` swallowed every exception | Schema failure fails module enable loudly |
| Time read with `getInt` (overflows in 2038) | Stored and read as `long` epoch milliseconds |
| Restore addressed a death by its position in a "last 5" list | Records are addressed by id and must belong to the target |
| Inventory serialized only non-null items (slot positions lost) | Fixed 41 slot snapshot (36 storage, 4 armor, 1 offhand) with explicit empties |
| One bad item silently skipped, then the player was wiped anyway | Any unreadable slot aborts the preview, nothing is modified |
| `getOfflinePlayer(...) as Player` cast crashed for offline targets | Offline targets handled through `OfflinePlayer` |
| Unbounded growth | `max-records-per-player` retention, oldest pruned first |

## Architecture

All code lives under `com.nearvanilla.iceCream.modules.deathDb`.

| File | Responsibility |
|---|---|
| `DeathDbModule` | Implements `Module`. Opens the repository, registers command and listeners, closes the repository in `unregister()`. |
| `InventorySnapshot` | The 41 slot layout: `capture`, `apply`, `serialize`, `deserialize`. The only owner of the byte format. |
| `InventoryRestorer` | Applies a snapshot to an online player and drops what they were carrying. |
| `storage/DeathRepository` | SQLite access. Single thread executor, `CompletableFuture` API, schema versioning, retention pruning. |
| `storage/DeathSummary`, `DeathRecord`, `DeathPage` | Immutable value records. Lists load summaries only, the inventory blob is loaded on demand. |
| `gui/DeathGui` | Holder interface: `onClick(Player viewer, int rawSlot)`. |
| `gui/DeathListGui`, `gui/DeathPreviewGui` | The two inventories and their click logic. |
| `gui/DeathGuiItems` | Item and text builders (MiniMessage with `Placeholder.unparsed`), time and position formatting. |
| `events/DeathRecordListener` | Snapshots the inventory on `PlayerDeathEvent` at `MONITOR` and hands the insert to the repository. |
| `events/DeathGuiListener` | Cancels all interaction with our GUIs and dispatches button clicks. |
| `commands/RestoreDeathCommand` | `/restoredeath` and `/rd`. |

### Data model

SQLite file: `plugins/IceCream/deathdb.db`. Schema version tracked with `PRAGMA user_version`.

```sql
CREATE TABLE deaths (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  player_id TEXT    NOT NULL,
  died_at   INTEGER NOT NULL,   -- epoch milliseconds
  world     TEXT    NOT NULL,
  x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL,
  inventory BLOB    NOT NULL
);
CREATE INDEX idx_deaths_player_time ON deaths (player_id, died_at DESC, id DESC);
```

A database whose `user_version` is newer than the code supports is refused (module fails to enable
instead of corrupting data).

### Inventory snapshot format

Slot order: indexes 0-35 are `getStorageContents()` (0-8 hotbar, 9-35 main), 36-39 are
`getArmorContents()` (boots, leggings, chestplate, helmet), 40 is the offhand. The explicit
accessors are used instead of `getContents()` so the layout does not depend on Paper's internal
ordering.

Bytes: `byte version (1)`, `int slotCount (41)`, then per slot `int length` (0 means empty) followed
by `ItemStack.serializeAsBytes()`. This is the same per slot approach `StaffModeUtils` already uses
in production. Paper's `serializeItemsAsBytes` is not used because its handling of null slots is
undocumented. Deserialization validates version, count and each length before allocating.

### Concurrency

- Item (de)serialization and Bukkit calls stay on the main thread. Only JDBC runs on the
  repository thread.
- Repository methods return `CompletableFuture`. GUI code completes on the repository thread and
  immediately hops back with `Bukkit.getScheduler().runTask`.
- Single connection, single writer thread. SQLite serialises writers anyway, so a pool adds nothing.
- `unregister()` shuts the executor down and waits for queued inserts so a shutdown does not lose
  the last deaths.

### Safety rules

- Re-check the viewer's permission on every click, and close the GUI if it was revoked.
- A `restored` flag on the preview holder makes a double click restore exactly once.
- Refuse the restore if the target is offline or dead (respawn screen), with a clear message. The
  GUI is not mutated.
- Restore never starts before the snapshot has fully deserialized.

## Configuration

```yaml
modules:
  deathdb:
    enabled: true
    # Maximum stored deaths per player; the oldest are pruned first. 0 keeps everything.
    max-records-per-player: 100
```

Existing servers keep their current `config.yml`, so the module stays disabled until these keys are
added (the code default for `enabled` is `false`, matching every other module).

## Dependencies and build

- `sqlite-jdbc` is bundled by the Paper server (3.49.1.0 in `run/libraries`), so it is neither
  shaded nor declared `compileOnly`. The repository only uses `java.sql`.
- A `test` source set is added: JUnit 6.1.2 and `sqlite-jdbc` 3.49.1.0 as `testImplementation`.
  It covers `DeathRepository` only. Bukkit dependent code needs a running server and is verified by
  a smoke run.

## Testing

Unit tests (`DeathRepositoryTest`): round trip including a timestamp beyond 32 bits, newest first
paging with clamping, empty history, wrong owner lookup, per player retention, reopen of an existing
file, refusal of a newer schema.

Smoke run on `runServer` with a real client: record a death, inspect the blob, browse pages, preview
layout, read-only guarantees, cancel, restore, double click, offline target, permission.
