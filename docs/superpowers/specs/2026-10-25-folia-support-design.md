# Folia Support Design

Date: 2026-10-25
Branch: `feat/folia-support`
Status: approved in conversation, awaiting written review

## 1. Goal

Make the IceCream jar load and run correctly on Folia 26.3, with near-full feature parity, while keeping
behaviour on Paper 26.3 the same as today.

### What was asked

- A full review of the project, focused on Folia readiness (unrelated bugs are noted, not fixed).
- Folia support with near-full parity, backed by research.
- Purpose: compatibility. A module may turn itself off on Folia when the platform lacks what it needs, as
  long as that is logged and documented.

### Assumptions confirmed during design

- Paper stays the primary target. Its behaviour only changes where section 9 says so.
- One jar serves both platforms.
- Work happens on `feat/folia-support`.

## 2. Research findings

All sources were read on 2026-10-25. Short names:

- FP = Folia CraftBukkit patch,
  `https://github.com/PaperMC/Folia/blob/ver/26.3.x/folia-server/paper-patches/features/0001-Region-Threading-Base.patch`
- FM = Folia Minecraft patch,
  `https://github.com/PaperMC/Folia/blob/ver/26.3.x/folia-server/minecraft-patches/features/0001-Region-Threading-Base.patch`
- FD = `https://docs.papermc.io/paper/dev/folia-support/`

| Topic | Finding | Source |
| --- | --- | --- |
| Availability | Folia 26.3 exists (builds 3 to 5, channel ALPHA, latest 2026-10-20). The repository default branch is `ver/26.3.x`. | `https://fill.papermc.io/v3/projects/folia/versions` |
| Declaring support | `folia-supported: true` in `plugin.yml` is required. Without it Folia throws "not marked as supporting Folia" and does not load the plugin. Paper ignores the key. | Folia API patch 0003 |
| Detection | PaperMC docs recommend `ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia"))`. Present in paper-api 26.3 (checked with javap). | FD |
| Schedulers | Global, Region, Entity and Async schedulers are part of the Paper API. On Paper they all run on the main thread, so one code path serves both platforms. | FD, Paper `CraftServer` |
| Bukkit scheduler | `CraftScheduler.handle()` throws `UnsupportedOperationException` on Folia. Every `runTask*` and `cancelTasks` goes through it. | FP |
| Delays | Global, region and entity `runDelayed` and `runAtFixedRate` require delays of at least 1 tick. Entity tasks have a `retired` callback that runs instead of the task when the entity is removed. `run` returns null and `execute` returns false when the entity is already retired. | Paper scheduler API |
| Entity access | `CraftEntity.getHandle()` calls `ensureTickThread`, so most non-player entity methods throw off the owning region. `CraftPlayer.getHandle()` deliberately skips the check. | FP |
| Teleport | `teleport` always throws ("Must use teleportAsync while in region threading"). `teleportAsync` may be called from any thread. | FP |
| Potion effects | `LivingEntity.addEffect` checks entity ownership. | FM |
| World-global state | `setTime`, `setFullTime`, `setStorm`, `setThundering`, `setWeatherDuration`, `setThunderDuration` and `setGameRule` require the global region thread. `getGameRuleValue`, `getFullTime` and `getTime` have no check. | FP |
| Region-owned reads | `getHighestBlockYAt`, `getChunkAt`, block get and set, and `BlockState#update` require the owning region. `isChunkLoaded` has no check. | FP |
| Players list | `getOnlinePlayers()` and `World#getPlayers()` are copies of a `CopyOnWriteArrayList`, so iteration is safe. | FM |
| Sleep | Vanilla night skip still works. `ServerLevel.tickSleep()` runs on the global region, fires `TimeSkipEvent` there, and wakes each sleeper through that player's entity scheduler. The global tick reads every player's sleep state. | FM |
| Commands | Player commands run on the player's region, console commands on the global region. | Folia README |
| Click callbacks | Adventure click callbacks (used by Paper dialogs) are stored in a `ConcurrentHashMap` and run on the clicking player's thread. | FP |
| Locator bar | `ServerWaypointManager` methods are no-ops on Folia, so the locator bar does not work there at all. | FM |
| Runtime worlds | `createWorld` and `unloadWorld` throw, so `WorldLoadEvent` and `WorldUnloadEvent` only happen at startup. | FP |
| Shutdown | `RegionShutdownThread` waits for every region scheduler to halt, then calls `stopServer()`, which disables plugins. On that thread entity and chunk checks pass, global checks fail, and scheduled tasks never run. Players and level data are saved after plugins are disabled. | FM |
| Cloud | cloud-paper 2.0.1 detects Folia and runs each command on the sender's scheduler context. With `simpleCoordinator()` handlers run on the calling thread. | `https://github.com/Incendo/cloud-minecraft/blob/2.0.1/cloud-paper/src/main/java/org/incendo/cloud/paper/PaperCommandPreprocessor.java` |
| squaremap | Folia supported since 1.1.15. 1.4.3 declares `foliaSupported = true`. On Folia `LayerProvider#getMarkers()` is called from a per-world markers executor, not a region thread. | `https://github.com/jpenilla/squaremap/releases/tag/v1.1.15` |
| DiscordSRV | Folia supported since 1.27.0. 1.30.5 declares `folia-supported: true`. | `https://github.com/DiscordSRV/DiscordSRV/releases/tag/v1.27.0` |
| CarbonChat | Folia supported since 2.1.0-beta.10. `CarbonChatEvent` comes from `AsyncChatEvent`, off the main thread. | `https://github.com/Hexaoxide/Carbon/releases/tag/v2.1.0-beta.10` |
| LuckPerms | Folia supported (first tagged release 5.5.53; the dev server uses 5.5.71). | `https://github.com/LuckPerms/LuckPerms/pull/3615` |
| run-paper | 3.0.2 provides `runPaper.folia.registerTask()` which adds `runFolia`. | `https://github.com/jpenilla/run-task/blob/v3.0.2/plugin/src/main/kotlin/xyz/jpenilla/runpaper/RunPaperExtension.kt` |

## 3. Approach

Chosen: use the Paper region-aware schedulers directly, through one small helper class, with Folia
branches only where the platform genuinely differs (shutdown and the locator bar).

Rejected:

- Shading a compatibility library (FoliaLib, UniversalScheduler, MorePaperLib). These bridge Spigot,
  Paper and Folia. IceCream is Paper only, and a library does not remove the per-module ownership work.
- Two code paths (Bukkit scheduler on Paper, Folia schedulers on Folia). Doubles code and testing, and
  contradicts the PaperMC recommendation.

## 4. Foundation

### 4.1 Declaration and build

- `src/main/resources/plugin.yml`: add `folia-supported: true`.
- `build.gradle`: register the Folia run task so `./gradlew runFolia` starts Folia 26.3 with the same
  downloaded plugins as `runServer`. This follows run-paper 3.0.2's own tester build: `pluginsMode` only
  covers plugin jars, so the shared settings move into a block that configures every `RunServer` task.
  `runFolia` gets its own run directory so Paper and Folia worlds stay separate.

```groovy
runPaper.folia.registerTask {
    runDirectory = file('run-folia')
}

tasks.withType(xyz.jpenilla.runpaper.task.RunServer).configureEach {
    minecraftVersion("26.3")
    jvmArgs("-Dcom.mojang.eula.agree=true")
    downloadPlugins {
        // the existing five url(...) entries, unchanged
    }
}
```

- `.gitignore`: add `run-folia`.
- The plugin jar keeps the default `PLUGIN_JAR_DETECTION` mode. Dependencies are unchanged.

### 4.2 SchedulerUtils

New class `com.nearvanilla.iceCream.utils.SchedulerUtils` (final, private constructor, static methods,
plugin is `IceCream.instance`).

| Method | Behaviour |
| --- | --- |
| `boolean isFolia()` | `ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia"))`, cached on first call. |
| `void runGlobal(Runnable task)` | Runs now if `Bukkit.isGlobalTickThread()`, otherwise `GlobalRegionScheduler.execute`. |
| `ScheduledTask runGlobalLater(Runnable task, long delayTicks)` | `GlobalRegionScheduler.runDelayed`, delay clamped to at least 1. |
| `ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks)` | `GlobalRegionScheduler.runAtFixedRate`, both clamped to at least 1. |
| `void runFor(Entity entity, Runnable task)` | Same as the next row with no retired callback. |
| `void runFor(Entity entity, Runnable task, Runnable retired)` | Runs now if `Bukkit.isOwnedByCurrentRegion(entity)`. Otherwise `entity.getScheduler().execute(plugin, task, retired, 1)`. If `execute` returns false (entity already removed), `retired` runs immediately on the calling thread. `retired` may be null. |
| `void runForNextTick(Entity entity, Runnable task)` | Always deferred: `entity.getScheduler().run(plugin, t -> task.run(), null)`. Replaces the existing "next tick" `runTask` calls that escape an event. |
| `CompletableFuture<Void> runForEach(Collection<? extends Player> players, Consumer<Player> action)` | Calls `runFor` for every player. The returned future completes once every player has either run the action or retired. An exception from one action is logged and does not stop the future from completing. |
| `void runAt(Location location, Runnable task)` | Runs now if `Bukkit.isOwnedByCurrentRegion(location)`, otherwise `RegionScheduler.execute(plugin, location, task)`. |
| `void remove(Entity entity)` | `runFor(entity, entity::remove)`. |

Why this preserves Paper behaviour: on Paper the main thread owns every entity, location and the global
region, so every call made from the main thread runs inline in the same tick, exactly as the code does
today. Calls from other threads (database callbacks) are scheduled on the main thread for the next tick,
which matches today's `runTask`.

### 4.3 Contributor rules

Add a "Threading" section to `ARCHITECTURE.md` and a step to `docs/creating_a_module.md`. Add the same
rule as a Lessons Learned entry to `CLAUDE.md`, `AGENTS.md` and `.github/copilot-instructions.md`, kept
identical across all three. The rules:

- Never use `Bukkit.getScheduler()` or `BukkitRunnable`. Use `SchedulerUtils`.
- Touch an entity other than the event subject or command sender only through `SchedulerUtils.runFor`.
- Change world time, weather or gamerules only through `SchedulerUtils.runGlobal`.
- Read blocks at arbitrary locations only on the owning region (`SchedulerUtils.runAt`).
- Use `teleportAsync`, never `teleport`.
- Module state reachable from more than one event handler or command must be thread-safe.

## 5. Module changes

### 5.1 Unchanged

These only touch the event's own entity or block, or the command sender: lootContainerProtection,
vineGrowth, readOnlyLectern, hat, isSlimeChunk, desertMobs, bonemealableSporeBlossoms, playerHeadDrops,
wanderingTrades, wanderful, phantomToggle, muteDeaths, deathLocation, firstJoinMessage, example.

`wanderful` registers its crafting recipe in `register()` at startup only, which is fine on Folia.

### 5.2 Small conversions

| Module | File | Change |
| --- | --- | --- |
| flight | `FlightListener.restoreNextTick` | `SchedulerUtils.runForNextTick(player, ...)` |
| flight | `FlightModule.register` | Loop over online players with `runFor` |
| lightning | `StrikeCommand` | Chance roll stays on the sender thread. The strike and victim message run in `runFor(target, ...)`. The retired callback sends the sender "This player is not online." The sender's success message is sent inside the target task, after the strike. |
| staffMode | `StaffModeCommand.disableStaffMode` | Inventory swap stays immediate. `teleport` becomes `teleportAsync`. Invulnerability off, flight off, data cleanup, flight module restore and the disabled message run once the teleport future completes, through `runFor(player)`. Without a saved location they run immediately. |
| deathDb | `DeathListGui.open`, `DeathPreviewGui.open` | `Bukkit.getScheduler().runTask` becomes `SchedulerUtils.runFor(viewer, ...)`. Retired means the viewer left, so nothing happens. |
| deathDb | `DeathPreviewGui.restore` | Runs on the target with `runFor(target, restoreTask, retiredTask)`. The `isDead` check moves inside `restoreTask`. Results (success, offline, on respawn screen) reach staff with `runFor(staff, ...)`, which also closes the GUI. The `restored` flag is only touched on the staff thread. |
| mapTools | `IconMenu.finish` | `SchedulerUtils.runForNextTick(player, ...)` |
| playerSeen | `SeenCommand` | Reads hidden state and hidden-since from the spectator module's thread-safe registry (5.3) instead of another player's data. |
| locatorBar | `LocatorBarModule.register` | On Folia, log at info level "Locator Bar module is disabled: Folia does not run the server waypoint system." and stay disabled. Paper is unchanged. |

### 5.3 Spectator

Facts this relies on: `hidePlayer` and `showPlayer` change the viewer's own visibility map. The part that
touches the hidden player's tracker does nothing when the two players are in different regions, because
the tracker only covers nearby players. Hide and show therefore run on the viewer's thread.

New thread-safe registry in `SpectatorUtils`:

- `Set<UUID>` of spectating players (`ConcurrentHashMap.newKeySet()`).
- `Map<UUID, Long>` of hidden-since times.
- Updated whenever a player enters or leaves spectator mode, filled from player data on join, and cleared
  on quit.
- `isSpectating(UUID)` and `getHiddenSince(UUID)` read it from any thread.
- Player data stays the persisted source of truth.

Enable (`enableSpectator(player, broadcast)`, runs on the player's thread):

1. Save previous gamemode, location, map visibility and hidden-since to player data, as today. Add the
   player to the registry.
2. `runForEach(other online players, viewer -> viewer.hidePlayer(plugin, player))`.
3. When that future completes, `runFor(player, ...)`: set spectator gamemode, hide on squaremap, send
   fake leave messages (if broadcast), send the Velocity state and show the boss bar.

This keeps the existing rule that the player is hidden before the gamemode change refreshes the tab list.
On Paper all three steps complete in the same tick.

Disable (`disableSpectator(player, broadcast)`, new `broadcast` parameter, existing callers pass true):

1. Clear the toggle and hidden-since data, remove from the registry, read the stored gamemode and location.
2. If a stored location parses, `teleportAsync` to it, then continue from step 3 on the player's thread.
   Otherwise continue immediately.
3. Restore the gamemode, clear DiscordSRV metadata, `runForEach(viewers, viewer -> viewer.showPlayer(...))`,
   restore squaremap visibility, send fake join messages (if broadcast), send the Velocity state and hide
   the boss bar.

The order change from today (teleport before gamemode) prevents a player in survival mode from sitting
inside a wall during the asynchronous teleport.

Other spectator paths:

- `hideSpectatingPlayersFrom(joiner)` already runs on the joining player's thread, which is the viewer.
  It now reads the registry instead of other players' data.
- `SpectatorPrivateMessageCommandEvent` and `CarbonChatIntegration` read the registry for both sender and
  recipient. Carbon events arrive off the main thread, so this also removes a race that exists on Paper.
  The predicates passed to `CarbonChatIntegration` change to registry lookups.
- The 1-tick Velocity re-send in `SpectatorPlayerJoinEvent` uses `runForNextTick`.
- DiscordSRV vanished metadata uses the entity metadata store, whose methods are synchronized. No change.

### 5.4 One Player Sleep

All module state is confined to the global region thread, except the two fields listed below.

- Heartbeat: `SchedulerUtils.runGlobalTimer(this::tick, 1, 1)`.
- `pendingEntries` becomes a `ConcurrentHashMap`. Bed-enter handlers (player region) put into it. The
  heartbeat and `shouldBlockNightSkip` (global region, from `TimeSkipEvent`) read and drain it.
- The cooldown becomes one `volatile long coolingUntilNanos`. `isCoolingDown()` becomes a pure read that
  any thread may call. It is written only on the global thread.
- `forgetPlayer` and `forgetWorld` (called from bed leave, quit, death, world change and world unload
  handlers) run through `runGlobal`.
- `observeTimeChange` runs through `runGlobal`. When the event already fires on the global thread (night
  skip and plugin time writes) this is inline, so the `ownTimeWrite` guard keeps working.
- The heartbeat keeps checking whether each sleeper is still valid (online, alive, sleeping, in the same
  world) from the global thread. Folia allows these reads for players, and its own `SleepStatus` reads
  the same state from the same thread.
- `wakePlayer` runs through `runFor(player)`, as Folia's own `wakeUpAllPlayers` does. The "another
  plugin prevented bed leaving" warning is logged inside that task.
- `requestWakeUp` (the `[Wake up]` command, on the clicker's thread) runs through `runGlobal`. Replies use
  `sendMessage`, which is thread-safe.
- `Bukkit.broadcast` is called from the global thread, which is allowed.

### 5.5 World Tour

Environment:

- `saveWeather`, `setWeather`, `restoreWeather`, `saveTime`, `setTime`, `restoreTime`,
  `saveKeepInventory`, `enableKeepInventory`, `restoreKeepInventory` and `applyTourEnvironment` only
  run on the global thread. Commands call them through `runGlobal` and send their confirmation message
  inside that task, after the change.
- The original-state maps are only touched on the global thread.
- Snapshot persistence (for Folia shutdown, section 6): whenever a snapshot is first taken for a world,
  it is also written to that world's persistent data container under these keys:
  - `icecream:worldtour.original.storm` (BOOLEAN)
  - `icecream:worldtour.original.thundering` (BOOLEAN)
  - `icecream:worldtour.original.weather_duration` (INTEGER)
  - `icecream:worldtour.original.thunder_duration` (INTEGER)
  - `icecream:worldtour.original.full_time` (LONG)
  - `icecream:worldtour.original.keep_inventory` (BOOLEAN)

  Each restore removes the matching keys.
- Startup recovery: in `register()`, `runGlobal` checks every world for those keys. It restores what it
  finds, removes the keys, and logs that an unfinished World Tour environment was restored.

Host state and players:

- `currentHost` and `currentGlowing` become `volatile`.
- Host transitions (start, takeover, takeover confirm, clear) become `synchronized` module methods. For
  example `tryStart(Player)` returns false when a host already exists, so two players in different
  regions cannot both start a tour.
- `pendingTakeovers` and `disconnectTasks` become `ConcurrentHashMap`. `disconnectTasks` holds
  `ScheduledTask`.
- `setGlowing(target)` adds the effect through `runFor(target)`. `clearGlowing()` removes it through
  `runFor(previous)`.
- `endTour` clears the joined flag and sends the message through `runForEach(online players, ...)`. Each
  player checks their own flag on their own thread.
- `tpall`: the command runs on the host's thread, so the host's location is read there. Then
  `runForEach(online players, ...)`: each participant checks their joined flag, calls `teleportAsync`
  and sends their message. An `AtomicInteger` counts the teleports. The host gets the success message and
  count once every teleport future has completed.
- Takeover paths set the requester's joined flag through `runFor(requester)`.
- Disconnect failsafe: `runGlobalLater(timeoutTicks)` replaces `runTaskLater`. A player's entity scheduler
  is retired when they quit, so the global scheduler is the only fit. The task body is unchanged.

### 5.6 Map Tools

Shared state:

- `MapToolsModule.sessions` becomes a `ConcurrentHashMap`.
- `EditSession` belongs to its player's thread. `changed` and `areaId` become `volatile` because other
  players' saves read them in `editorsOf` and `blockedByEditor`.
- In `saveArea` and `deleteArea`, any change to another editor's session (`setChanged(false)`, restarting
  the edit, refreshing their displays) runs through `runFor(editor)`.
- `MapAreasStore`: every read and write takes one lock. `getAreas()` returns an immutable snapshot that is
  replaced on each change. `save()` stays synchronous on the calling thread, as on Paper today, and is
  serialised by the lock, so two saves can no longer race on `map-areas.json.tmp`.
- `AreaLayer.put` and `AreaLayer.remove` become `synchronized`. The `sorted` list squaremap reads from its
  own thread is already `volatile`.

World access:

- `MapToolsUtils.groundBlock` returns null unless
  `Bukkit.isOwnedByCurrentRegion(world, blockX >> 4, blockZ >> 4)`. This reuses the existing "chunk not
  loaded" result, so claim corners in another region are not drawn. On Paper the check is always true.
- `AreaDisplays`: the four drawing maps become `ConcurrentHashMap`. Drawing for a player always runs on
  that player's thread. A segment or highlight whose location is not owned by the current region is
  skipped instead of spawned. Old displays are removed with `SchedulerUtils.remove`, because after a long
  teleport they may belong to another region. `refreshAllAreas` calls `runFor(editor)` per editor.
- `AreaBorderTask` runs every 10 ticks on `runGlobalTimer` and calls `runFor(player, body)` for each
  online player. The body (permission check, display update, action bar) is unchanged.
- `/maptools tp`: `runAt(claim location, ...)` reads the ground height on the region that owns the claim,
  then calls `player.teleportAsync`.
- Console commands (create, set, delete) run on the global thread and go through the same thread-safe
  paths.

## 6. Shutdown on Folia

On Folia, `onDisable` runs on the shutdown thread after region schedulers have stopped. Scheduled tasks
never run there, global-region calls throw, and teleport is impossible. Changes to a player's own state
pass the thread checks, and players are saved afterwards. Paper behaviour is unchanged in every row.

| Module | Folia shutdown behaviour |
| --- | --- |
| flight | Suspend survival flight directly for each player, each in its own try/catch that logs a warning. |
| worldTour | Cancel failsafe tasks. Remove the host's glow directly, in a try/catch. Skip the environment restore; the persisted snapshot (5.5) is restored at the next startup. |
| spectator | For every spectating player, set `icecream:spectator.restore_on_join` (BOOLEAN) and skip the live restore. Dispose the Carbon subscriptions as today. On join, a player with that flag skips the normal spectator re-enable: the flag is removed, `disableSpectator(player, false)` runs, and the real join message is kept. |
| onePlayerSleep | Cancel the heartbeat and clear state. Skip waking players, because they are disconnected right after. |
| mapTools | Clear the in-memory display maps without removing entities. Displays are spawned with `setPersistent(false)`, so they are not saved. Squaremap layers are unregistered as today. |
| deathDb, lootContainerProtection | Unchanged. |

Each module checks `SchedulerUtils.isFolia()` in its `unregister()` to choose between the Paper and Folia
paths.

## 7. Error handling

- Retired callbacks are used where the user should hear about it (strike target left, restore target left).
  Elsewhere a retired task is dropped silently, matching today's `isOnline()` guards.
- `runForEach` logs per-player exceptions at warning level and still completes.
- `teleportAsync` futures that complete exceptionally or with false are logged at warning level, and the
  follow-up steps still run so a player is never left half-converted (for example stuck in spectator
  without a teleport).
- Folia shutdown try/catch blocks log a warning naming the player and module.

## 8. Testing

Automated (`./gradlew build`, which also runs Spotless):

- Existing `DeathRepositoryTest`.
- New `MapAreasStoreTest`: concurrent writers from several threads produce a consistent store and a
  readable `map-areas.json`; `getAreas()` snapshots stay unchanged after later writes.
- No other new tests: the remaining changes are thread hand-offs that need a running server, and
  MockBukkit is not added because it has no region semantics. They are covered by the server runs and the
  manual checklist below.

Server runs (by the implementer):

- `./gradlew runServer` (Paper 26.3) and `./gradlew runFolia` (Folia 26.3 build 5).
- Every module logs enabled, and squaremap, DiscordSRV, CarbonChat, LuckPerms and ArmorStandEditor load.
- Console paths work on the global thread: Map Tools create, set and delete; `worldtour forceend`.
- A clean stop logs no exceptions from IceCream.

Manual in-game checklist (two players, run on both platforms; on Folia put the players far enough apart
to be in different regions, at least several thousand blocks):

1. `/spectator` on and off, with the other player watching the tab list and world; rejoin while spectating.
2. `/strike` the other player.
3. `/restoredeath` on the other player and restore an inventory.
4. World Tour: start, weather, time, glow on the other player, tpall, takeover, end. Then start a tour and
   stop the server; after restart weather, time and keepInventory are restored.
5. Spectate, stop the server, rejoin: the player is back in their previous gamemode and location.
6. One player sleeps: night accelerates; the other player clicks `[Wake up]`.
7. Map Tools: edit mode, create and edit a claim while the other player edits a nearby claim, save, delete,
   `/maptools tp`, icon menu.
8. Staff mode on and off; fly toggle; death DB GUI paging.

Open items to verify during the Folia run:

- A sleeping player is woken when disconnected at shutdown.
- Flight suspension and glow removal succeed on the shutdown thread.
- Brigadier suggestions for Cloud commands work on Folia.
- run-paper 3.0.2 resolves the Folia 26.3 ALPHA build.

## 9. Behaviour changes on Paper

- Staff mode disable, spectator disable and World Tour `tpall` use `teleportAsync`. The teleport completes
  a tick or so later than today, and the follow-up steps wait for it.
- Spectator disable teleports before restoring the gamemode (previously the reverse).
- World Tour restores a leftover environment snapshot at startup. On Paper this only happens after a crash
  during a tour, which today leaves the tour weather, time and keepInventory in place indefinitely.
- Spectator, `/seen`, `/msg` interception and Carbon checks read the in-memory registry. Results are the
  same.

## 10. Out of scope (noted by the audit, not fixed)

- `/maptools tp` uses the default heightmap, so Nether claims teleport onto the roof.
- The `/maptools tp` target is the average of the corners, which can lie outside a concave polygon.
- A failed `map-areas.json` save is only logged; callers still report success and a `.tmp` file is left.
- The Map Tools confirm-leave Save button continues even when the save was refused.
- Hand-edited `map-areas.json` with a missing shape, center or radius throws during load.
- Read-only lecterns are not protected from block explosions (beds, respawn anchors); the piston listener
  can never fire; `isEligibleForToggle` is unused.
- `/hat` lets survival players remove a Curse of Binding helmet and accepts whole stacks.
- The lightning module Javadoc says a failed strike hits the sender; the code only sends a message.
- Desert mobs converts non-natural spawns (spawners, eggs, reinforcements) and drops riders and baby state.
- Wandering trades can offer duplicate heads, logs a warning per trader when loading failed, and throws on
  a missing `secondaryCost`.
- Wanderful frame handlers ignore cancellation, and `/wanderful` can hand out a null wand or lose it when the
  inventory is full.
- First join message logs at info on every first join when no message is configured.
- `/seen` with an uncached name may make a blocking profile lookup.
