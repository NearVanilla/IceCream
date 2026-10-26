# Folia Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make IceCream load and run on Folia 26.3 with near-full parity, without changing Paper behaviour beyond spec section 9.

**Architecture:** One code path built on Paper's region-aware schedulers, wrapped by a small `SchedulerUtils` helper that runs inline when the current thread already owns the target. Folia-only branches exist only for shutdown handling and the Locator Bar module.

**Tech Stack:** Java 25, paper-api `26.3.build.200-stable`, Cloud 2 (cloud-paper 2.0.1), JUnit 6, run-paper 3.0.2, Spotless with google-java-format.

**Spec:** `docs/superpowers/specs/2026-10-25-folia-support-design.md`

## Global Constraints

- No new dependencies (main or test). paper-api stays `26.3.build.200-stable`.
- Never call `Bukkit.getScheduler()` or use `BukkitRunnable`/`BukkitTask` anywhere in `src/main`.
- Plugin reference for every scheduler call is `IceCream.instance`.
- Player-facing text uses MiniMessage templates, as the project rules require.
- Do not declare permissions in `plugin.yml`.
- Documentation: no em dashes, no HTML tags.
- AI instruction rules change identically in `CLAUDE.md`, `AGENTS.md` and `.github/copilot-instructions.md`.
- Run `./gradlew spotlessApply` before every build; `./gradlew build` must pass (it runs Spotless and tests).
- Do not commit or push unless the user asks. The commit steps below are replaced by "leave the change in the working tree".
- Paper behaviour changes only where spec section 9 lists them.

## Review Focus

1. A target player quits between scheduling and execution: the task must not throw; `/strike` and death restore tell the sender, everything else drops silently. Checked in Task 8 (manual items 2 and 3).
2. Folia shutdown: anything scheduled from `unregister()` never runs and global-region calls throw, so every `unregister()` must take its Folia branch. Checked in Task 8 (clean `runFolia` stop with players online, log has no IceCream exceptions).
3. `teleportAsync` completes with false or exceptionally: spectator disable and staff mode disable still finish restoring the player. Checked in Task 8 (manual item 5 with the saved world missing is not practical; covered by code review of the `whenComplete` paths in Tasks 3 and 4).
4. "Next tick" call sites that escape an event (flight re-apply, icon menu, Velocity re-send) must stay deferred on Paper, not become inline. Checked in Task 8 (manual item 8 and item 7 icon menu on Paper).
5. A spectating player who joins with the restore flag must not be re-hidden and must keep the real join message. Checked in Task 8 (manual item 5 on Folia).

---

### Task 1: Platform declaration and Folia run task

**Files:**
- Modify: `src/main/resources/plugin.yml`
- Modify: `build.gradle` (the `runServer { ... }` block)
- Modify: `.gitignore`

**Interfaces:**
- Produces: `./gradlew runFolia` (run directory `run-folia`), `folia-supported: true`.

- [ ] **Step 1:** Add `folia-supported: true` to `plugin.yml`.
- [ ] **Step 2:** In `build.gradle`, replace the `runServer { ... }` block with `runPaper.folia.registerTask { runDirectory = file('run-folia') }` plus a `tasks.withType(xyz.jpenilla.runpaper.task.RunServer).configureEach { ... }` block holding the existing `minecraftVersion("26.3")`, `jvmArgs(...)` and the five `downloadPlugins` URLs, unchanged.
- [ ] **Step 3:** Add `run-folia` to `.gitignore`.
- [ ] **Step 4:** Run `./gradlew tasks --all`. Expected: lists `runFolia` and `cleanFoliaCache`.
- [ ] **Step 5:** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.

### Task 2: SchedulerUtils and contributor rules

**Files:**
- Create: `src/main/java/com/nearvanilla/iceCream/utils/SchedulerUtils.java`
- Modify: `ARCHITECTURE.md` (new `## Threading` section), `docs/creating_a_module.md` (new step), `CLAUDE.md`, `AGENTS.md`, `.github/copilot-instructions.md` (one identical Lessons Learned entry)

**Interfaces:**
- Produces (all `public static`, final class, private constructor, Javadoc in the style of `PlayerProfileUtils`):
  - `boolean isFolia()`
  - `void runGlobal(Runnable task)`
  - `ScheduledTask runGlobalLater(Runnable task, long delayTicks)`
  - `ScheduledTask runGlobalTimer(Runnable task, long delayTicks, long periodTicks)`
  - `void runFor(Entity entity, Runnable task)`
  - `void runFor(Entity entity, Runnable task, Runnable retired)` (retired may be null)
  - `void runForNextTick(Entity entity, Runnable task)`
  - `CompletableFuture<Void> runForEach(Collection<? extends Player> players, Consumer<Player> action)`
  - `void runAt(Location location, Runnable task)`
  - `void remove(Entity entity)`

- [ ] **Step 1:** Implement the class with exactly the behaviour in spec 4.2. Notes the table leaves open: `isFolia()` caches in a `static volatile Boolean`; delays are clamped with `Math.max(1, ...)`; when `entity.getScheduler().execute(...)` returns false, run `retired` (if non-null) on the calling thread; `runForEach` builds one `CompletableFuture` per player, completes it from both the task and the retired callback, catches and logs (`IceCream.logger.log(Level.WARNING, ...)`) an exception from `action`, and returns `CompletableFuture.allOf(...)`.
- [ ] **Step 2:** Add the Threading rules from spec 4.3 to `ARCHITECTURE.md` and a "Step 4 - Threading" section to `docs/creating_a_module.md`. Add one Lessons Learned bullet, worded identically in the three AI documents: "Folia is supported. Never use `Bukkit.getScheduler()`; use `SchedulerUtils`. Touch entities other than the event subject or command sender only through `SchedulerUtils.runFor`, change world time, weather and gamerules only through `SchedulerUtils.runGlobal`, use `teleportAsync` instead of `teleport`, and keep module state that several threads reach thread-safe."
- [ ] **Step 3:** Run `./gradlew spotlessApply build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4:** Run `grep -cP "\x{2014}" ARCHITECTURE.md docs/creating_a_module.md CLAUDE.md AGENTS.md .github/copilot-instructions.md`. Expected: 0 for `ARCHITECTURE.md` and `docs/creating_a_module.md`, 1 for each AI document (the existing em dash rule quotes the character).

### Task 3: Small conversions (flight, lightning, staff mode, death DB, icon menu, locator bar)

**Files:**
- Modify: `modules/flight/events/FlightListener.java`, `modules/flight/FlightModule.java`
- Modify: `modules/lightning/commands/StrikeCommand.java`
- Modify: `modules/staffMode/commands/StaffModeCommand.java` (`disableStaffMode`)
- Modify: `modules/deathDb/gui/DeathListGui.java`, `modules/deathDb/gui/DeathPreviewGui.java`
- Modify: `modules/mapTools/IconMenu.java` (`finish`)
- Modify: `modules/locatorBar/LocatorBarModule.java` (`register`)

(All module paths are under `src/main/java/com/nearvanilla/iceCream/`.)

**Interfaces:**
- Consumes: `SchedulerUtils.runFor`, `runForNextTick`, `isFolia` from Task 2.

- [ ] **Step 1:** Apply the rows of spec 5.2 for flight, lightning, staff mode, death DB, icon menu and locator bar. `FlightModule.unregister` keeps calling `FlightUtils.suspendFlight` directly, wrapping each player in try/catch with a warning when `SchedulerUtils.isFolia()` (spec 6). Staff mode: keep the inventory swap before the teleport; run the rest in `teleportAsync(...).whenComplete((ok, error) -> SchedulerUtils.runFor(player, rest))`, logging a warning when `error != null` or `ok` is false; when no saved location exists, run `rest` directly.
- [ ] **Step 2:** Run `grep -rn "Bukkit.getScheduler\|getServer().getScheduler\|\.teleport(" src/main/java/com/nearvanilla/iceCream/modules/{flight,lightning,staffMode,deathDb,locatorBar} src/main/java/com/nearvanilla/iceCream/modules/mapTools/IconMenu.java`. Expected: no hits.
- [ ] **Step 3:** Run `./gradlew spotlessApply build`. Expected: BUILD SUCCESSFUL.

### Task 4: Spectator

**Files:**
- Modify: `modules/spectator/SpectatorUtils.java`, `modules/spectator/SpectatorModule.java`
- Modify: `modules/spectator/events/SpectatorPlayerJoinEvent.java`, `SpectatorPlayerQuitEvent.java`, `SpectatorPrivateMessageCommandEvent.java`
- Modify: `modules/integrations/CarbonChatIntegration.java` (only if its predicates need adapting; the predicate types stay `Predicate<Player>`)
- Modify: `modules/playerSeen/commands/SeenCommand.java`

**Interfaces:**
- Consumes: `SchedulerUtils.runFor`, `runForEach`, `runForNextTick`, `isFolia`.
- Produces:
  - `SpectatorModule.RESTORE_ON_JOIN_KEY` = `new NamespacedKey(IceCream.instance, "spectator.restore_on_join")`
  - `SpectatorUtils.isSpectating(UUID playerId)` returns boolean
  - `SpectatorUtils.getHiddenSince(UUID playerId)` returns long, 0 when unknown
  - `SpectatorUtils.track(Player player)` (reads player data into the registry, called on join)
  - `SpectatorUtils.untrack(UUID playerId)` (called on quit)
  - `SpectatorUtils.disableSpectator(Player player, boolean broadcastMessages)`; the existing one-argument overload calls it with true

- [ ] **Step 1:** Add the registry (`ConcurrentHashMap.newKeySet()` and a `ConcurrentHashMap<UUID, Long>`) and the methods above. `getHiddenSince(Player)` is replaced by the UUID version; update its caller in `SeenCommand`, which also switches its hidden check to `isSpectating`.
- [ ] **Step 2:** Rewrite `enableSpectator(player, broadcast)` and `disableSpectator(player, broadcast)` to the step order in spec 5.3, using `runForEach` for hide and show, `teleportAsync(...).whenComplete` for the disable teleport, and `runFor(player, ...)` for the follow-up steps. A failed or false teleport is logged at warning level and the follow-up still runs.
- [ ] **Step 3:** `hideSpectatingPlayersFrom`, the private message listener and the Carbon predicates passed from `SpectatorModule.register` read `isSpectating(uuid)`. The join listener calls `track(player)` first; if the player has `RESTORE_ON_JOIN_KEY`, it removes the key, calls `disableSpectator(player, false)` and returns without touching the join message; otherwise it behaves as today, with the Velocity re-send on `runForNextTick`. The quit listener calls `untrack`.
- [ ] **Step 4:** `SpectatorUtils.cleanup()`: on Folia, set `RESTORE_ON_JOIN_KEY` to true for every online player who is spectating instead of calling `disableSpectator`; Carbon cleanup and boss bar teardown stay. Paper path unchanged.
- [ ] **Step 5:** Run `./gradlew spotlessApply build`. Expected: BUILD SUCCESSFUL.

### Task 5: One Player Sleep

**Files:**
- Modify: `modules/onePlayerSleep/OnePlayerSleepModule.java`
- Modify: `modules/onePlayerSleep/events/OnePlayerSleepListener.java` (only if a handler needs to pass IDs instead of the event object)

**Interfaces:**
- Consumes: `SchedulerUtils.runGlobal`, `runGlobalTimer`, `runFor`, `isFolia`.
- Produces: unchanged public method names (`isCoolingDown`, `queueSleepCheck`, `forgetPlayer`, `forgetWorld`, `observeTimeChange`, `shouldBlockNightSkip`, `requestWakeUp`, `unregister`).

- [ ] **Step 1:** Apply spec 5.4. `heartbeat` becomes a `ScheduledTask` from `runGlobalTimer(this::tick, 1, 1)`. `pendingEntries` becomes `ConcurrentHashMap`. `enabled` becomes `volatile`. The cooldown is one `volatile` field read by a side-effect-free `isCoolingDown()`, false before any wake request and after `unregister()`. `forgetPlayer`, `forgetWorld`, `observeTimeChange` and `requestWakeUp` wrap their bodies in `SchedulerUtils.runGlobal`. `wakePlayer` wraps its body in `SchedulerUtils.runFor(player, ...)`.
- [ ] **Step 2:** `unregister()`: on Folia, cancel the heartbeat, unregister the listener and clear state without waking anyone (spec 6). Paper path unchanged.
- [ ] **Step 3:** Run `./gradlew spotlessApply build`. Expected: BUILD SUCCESSFUL.

### Task 6: World Tour

**Files:**
- Modify: `modules/worldTour/WorldTourModule.java`
- Modify: `modules/worldTour/commands/WorldTourCommand.java`
- Modify: `modules/worldTour/events/WorldTourListener.java`

**Interfaces:**
- Consumes: `SchedulerUtils.runGlobal`, `runGlobalLater`, `runFor`, `runForEach`, `isFolia`.
- Produces:
  - `WorldTourModule.tryStart(Player player)` returns boolean, `synchronized`
  - `scheduleDisconnectTask(UUID, ScheduledTask)` (parameter type changes from `BukkitTask`)
  - World data keys exactly as listed in spec 5.5, created in `initKeys()`
  - `restoreLeftoverEnvironment()` (called from `register()` through `runGlobal`)

- [ ] **Step 1:** Module: apply spec 5.5. Each `save*` writes its values to the world's persistent data container the first time it snapshots that world; each `restore*` removes the keys it restored. `restoreLeftoverEnvironment()` restores whatever keys a world still holds, removes them and logs one info line per world. `currentHost` and `currentGlowing` become `volatile`; `setCurrentHost`, `clearHost` and `tryStart` are `synchronized`; `pendingTakeovers` and `disconnectTasks` become `ConcurrentHashMap`. `setGlowing` and `clearGlowing` use `runFor`. `endTour` uses `runForEach`.
- [ ] **Step 2:** `unregister()`: cancel failsafe tasks; on Folia, remove the host's glow directly in a try/catch and clear the host without restoring the environment; on Paper, `clearHost(true)` as today.
- [ ] **Step 3:** Commands: `start` uses `tryStart`; `weather`, `time` and `start` run the environment change and the confirmation message inside `SchedulerUtils.runGlobal`; `tpall` follows spec 5.5 (host location read on the host thread, per-participant check and `teleportAsync` inside `runForEach`, `AtomicInteger` count, host message after `CompletableFuture.allOf` of the teleport futures); takeover paths set the requester's joined flag with `runFor(requester, ...)`; `glow` uses the module's `setGlowing`.
- [ ] **Step 4:** Listener: the failsafe uses `SchedulerUtils.runGlobalLater(task, timeoutTicks)`.
- [ ] **Step 5:** Run `./gradlew spotlessApply build`. Expected: BUILD SUCCESSFUL.

### Task 7: Map Tools

**Files:**
- Modify: `modules/mapTools/MapAreasStore.java`, `AreaLayer.java`, `MapToolsModule.java`, `EditSession.java`, `MapToolsUtils.java`, `AreaDisplays.java`, `AreaBorderTask.java`, `commands/MapToolsCommand.java` (`teleport`)
- Test: `src/test/java/com/nearvanilla/iceCream/modules/mapTools/MapAreasStoreTest.java`

**Interfaces:**
- Consumes: `SchedulerUtils.runFor`, `runAt`, `remove`, `runGlobalTimer`, `isFolia`.
- Produces: `MapAreasStore` keeps its public method names and signatures; `getAreas()` still returns `Collection<MapArea>`, now an immutable snapshot.

- [ ] **Step 1: Write the failing test.** `MapAreasStoreTest` with a `@TempDir` and only rect areas (no Bukkit classes):
  - `concurrentPutsAreAllSaved()`: 8 threads each `putArea` 25 areas with distinct ids, started together through a `CountDownLatch`; after joining, a fresh `MapAreasStore` on the same file `load()`s and `getAreas().size()` equals 200, and no `map-areas.json.tmp` file is left.
  - `getAreasIsASnapshot()`: take `getAreas()`, `putArea` one more area, assert the earlier collection's size is unchanged and a new `getAreas()` call has one more.
- [ ] **Step 2:** Run `./gradlew test --tests "*MapAreasStoreTest"`. Expected: FAIL (lost areas, a failed move, or the live view growing).
- [ ] **Step 3:** Make `MapAreasStore` thread-safe as spec 5.6 says: one private lock object guards `areas`, `knownModified`, `load`, `save`, `getArea` and every mutator; a `volatile` immutable `List<MapArea>` snapshot is rebuilt after every change and returned by `getAreas()`.
- [ ] **Step 4:** Run `./gradlew test --tests "*MapAreasStoreTest"`. Expected: PASS.
- [ ] **Step 5:** Apply the rest of spec 5.6: `AreaLayer.put`/`remove` `synchronized`; `MapToolsModule.sessions` `ConcurrentHashMap` and `borderTask` a `ScheduledTask` from `runGlobalTimer(task, 10, 10)`; `EditSession.changed` `volatile`; `MapToolsUtils.groundBlock` ownership guard; `saveArea` and `deleteArea` wrap each editor's work in `runFor(editor, ...)`; `AreaDisplays` maps `ConcurrentHashMap`, unowned locations skipped in `spawn` callers (return null from `spawn` and skip null), old entities removed with `SchedulerUtils.remove`, `refreshAllAreas` per editor through `runFor`; `AreaBorderTask.run` dispatches each player's body with `runFor`; `/maptools tp` computes the height inside `runAt(new Location(world, x, 0, z), ...)` then calls `teleportAsync`.
- [ ] **Step 6:** `MapToolsModule.unregister()`: on Folia, clear the display maps without removing entities (spec 6); Paper unchanged.
- [ ] **Step 7:** Run `./gradlew spotlessApply build`. Expected: BUILD SUCCESSFUL, all tests pass.

### Task 8: Verification on both servers

**Files:**
- Create: `docs/wiki/Folia.md` (the manual checklist from spec section 8, plus the two platform notes: Folia 26.3 is ALPHA, and the locator bar is unavailable on Folia)

- [ ] **Step 1:** Run `grep -rn "Bukkit.getScheduler\|BukkitRunnable\|BukkitTask\|\.teleport(" src/main/java`. Expected: no hits.
- [ ] **Step 2:** Run `./gradlew build`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3:** Start `./gradlew runServer` in the background, wait for "Done", check the log for every module's enabled line and for squaremap, DiscordSRV, CarbonChat, LuckPerms and ArmorStandEditor loading. From the console run `maptools create claim rect world 0 0 10 10 foliatest red Folia test`, `maptools set claim foliatest label Renamed`, `maptools delete claim foliatest` and `worldtour forceend`, then `stop`. Expected: no IceCream stack traces.
- [ ] **Step 4:** Repeat Step 3 with `./gradlew runFolia`. Expected: the same, plus the Locator Bar disabled line.
- [ ] **Step 5:** Write `docs/wiki/Folia.md` and hand the user the manual two-player checklist and the open items from spec section 8.

### Task 9: Final review

- [ ] **Step 1:** Dispatch one reviewer over the full working-tree diff against `main`, with the spec and this plan, focused on the Review Focus list and on Paper behaviour drift.
- [ ] **Step 2:** Address confirmed findings, then rerun `./gradlew spotlessApply build`.
