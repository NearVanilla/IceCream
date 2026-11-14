# Trial Vault Reset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `trialvaultreset` module that lets a player reset a trial vault they already looted by using its key once a configurable delay has passed.

**Architecture:** Loot times are stored per player in the vault block's `PersistentDataContainer`, written one tick after `BlockDispenseLootEvent`. A `PlayerInteractEvent` listener asks a pure decision class whether to reset, and on reset removes the player from the vault's rewarded players and cancels the click.

**Tech Stack:** Java 25, Paper API 26.3 (`org.bukkit.block.Vault`, `BlockDispenseLootEvent`), MiniMessage, JUnit 6, Mockito, MockBukkit.

**Spec:** `docs/superpowers/specs/2026-11-14-trial-vault-reset-design.md`

## Global Constraints

- Package: `com.nearvanilla.iceCream.modules.trialVaultReset`.
- Config path `modules.trialvaultreset`, keys `enabled` (default `true` in `config.yml`, `false` fallback in code), `reset-delay-minutes` (default `1440`), `messages.reset`, `messages.waiting`.
- A negative `reset-delay-minutes` is treated as 0.
- Player-facing messages are MiniMessage templates; `<time>` is filled with `Placeholder.unparsed("time", ...)`.
- No permissions in `plugin.yml`. No em dashes and no HTML in docs.
- Never change another player's rewarded entry or stored loot time.
- Do not commit; the user commits. Run `./gradlew spotlessApply build` at the end and check no tests were skipped.

## Review Focus

- A click with the wrong key (normal key on an ominous vault) must do nothing. Test in Task 3.
- A left click, or a right click with an empty hand, must do nothing. Test in Task 3.
- A vault that is `EJECTING` must not be reset. Test in Task 3.
- Recording a loot time must keep other players' times and drop times for players no longer rewarded. Test in Task 2.
- A cancelled `BlockDispenseLootEvent` or a non-vault block (trial spawner) must not record anything. Test in Task 3.

---

### Task 1: Reset decision and time formatting

**Files:**
- Create: `src/main/java/com/nearvanilla/iceCream/modules/trialVaultReset/ResetDecision.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/trialVaultReset/DurationFormat.java`
- Test: `src/test/java/com/nearvanilla/iceCream/modules/trialVaultReset/ResetDecisionTest.java`
- Test: `src/test/java/com/nearvanilla/iceCream/modules/trialVaultReset/DurationFormatTest.java`

**Interfaces:**
- Produces: `record ResetDecision(Outcome outcome, long remainingMs)` with nested `enum Outcome { IGNORE, RESET, WAIT }` and `static ResetDecision decide(boolean rewarded, Long lootTimeMs, long nowMs, long delayMs)`. `lootTimeMs` is null when no time is recorded. `remainingMs` is 0 unless `WAIT`.
- Produces: `final class DurationFormat` with `static String format(long ms)`.

- [ ] **Step 1: Write the failing tests**

`ResetDecisionTest`:
- `ignoresPlayerWhoHasNotLooted`: `decide(false, null, 1000, 0)` and `decide(false, 500L, 1000, 0)` give `IGNORE`.
- `resetsWhenNoTimeRecorded`: `decide(true, null, 1000, 86_400_000)` gives `RESET`.
- `resetsWhenDelayPassed`: `decide(true, 0L, 86_400_000, 86_400_000)` gives `RESET` (exactly at the delay counts).
- `waitsWithTimeLeft`: `decide(true, 0L, 86_399_000, 86_400_000)` gives `WAIT` with `remainingMs == 1_000`.
- `zeroDelayResetsImmediately`: `decide(true, 1000L, 1000, 0)` gives `RESET`.
- `negativeDelayActsAsZero`: `decide(true, 1000L, 1000, -5)` gives `RESET`.
- `lootTimeInTheFutureWaitsFullDelayAtMost`: `decide(true, 5000L, 1000, 10_000)` gives `WAIT` with `remainingMs == 10_000` (a clock that moved backwards never yields more than the delay).

`DurationFormatTest` (seconds are rounded up, then the largest non-zero unit and the next unit are shown, the next unit omitted when zero):
- `format(1)` is `"1s"`; `format(45_000)` is `"45s"`; `format(61_000)` is `"1m 1s"`.
- `format(11_520_000)` is `"3h 12m"`; `format(10_800_000)` is `"3h"`; `format(11_550_000)` is `"3h 12m"`.
- `format(90_000_000)` is `"1d 1h"`; `format(3_599_500)` is `"1h"`.
- `format(0)` is `"0s"`.

- [ ] **Step 2: Run the tests and see them fail**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.trialVaultReset.*"`
Expected: compilation failure, `ResetDecision` and `DurationFormat` not found.

- [ ] **Step 3: Implement both classes**

`decide`: not rewarded gives `IGNORE`; clamp delay to at least 0; null loot time gives `RESET`; `remaining = min(delay, lootTime + delay - now)`; `remaining <= 0` gives `RESET`, otherwise `WAIT` with `remaining`. Add Javadoc in the project style (`@author 105hua`, `@since 2026-11-14`).

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.trialVaultReset.*"`
Expected: PASS, none skipped.

### Task 2: Loot time storage

**Files:**
- Create: `src/main/java/com/nearvanilla/iceCream/modules/trialVaultReset/LootTimes.java`
- Test: `src/test/java/com/nearvanilla/iceCream/modules/trialVaultReset/LootTimesTest.java`

**Interfaces:**
- Produces: `final class LootTimes` with constructor `LootTimes(String namespace)` and methods `Long get(PersistentDataContainer pdc, UUID player)`, `void put(PersistentDataContainer pdc, UUID player, long timeMs)`, `void remove(PersistentDataContainer pdc, UUID player)`, `void retainOnly(PersistentDataContainer pdc, Collection<UUID> players)`.
- Layout: one `LONG` entry per player at key `<namespace>:trialvaultreset.loot.<uuid>` (lowercase UUID string). `retainOnly` removes only keys in this namespace with this prefix whose UUID is not in `players`; it leaves every other key alone.

- [ ] **Step 1: Write the failing tests**

Use `MockBukkit.mock()` and a real container from `server.addPlayer().getPersistentDataContainer()`; namespace `"icecream"`.
- `getReturnsNullWhenMissing`.
- `putThenGetRoundTrips`: put `1234L` for player A, get A is `1234L`, get B is null.
- `removeOnlyAffectsThatPlayer`: put A and B, remove A, A is null, B unchanged.
- `retainOnlyDropsPlayersNotListed`: put A, B, C; `retainOnly(pdc, List.of(A, C))`; B is null, A and C unchanged.
- `retainOnlyLeavesUnrelatedKeys`: set `icecream:other` (`BOOLEAN` true) and `otherplugin:trialvaultreset.loot.<A>` (`LONG`); `retainOnly(pdc, List.of())`; both still present.

- [ ] **Step 2: Run the tests and see them fail**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.trialVaultReset.LootTimesTest"`
Expected: compilation failure, `LootTimes` not found.

- [ ] **Step 3: Implement `LootTimes`**

`retainOnly` iterates a copy of `pdc.getKeys()`, matches namespace and prefix, parses the UUID suffix (skip keys that do not parse), and removes the ones not in `players`.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.trialVaultReset.LootTimesTest"`
Expected: PASS, none skipped.

### Task 3: Config and listener

**Files:**
- Create: `src/main/java/com/nearvanilla/iceCream/modules/trialVaultReset/TrialVaultResetConfig.java`
- Create: `src/main/java/com/nearvanilla/iceCream/modules/trialVaultReset/events/TrialVaultResetListener.java`
- Test: `src/test/java/com/nearvanilla/iceCream/modules/trialVaultReset/events/TrialVaultResetListenerTest.java`

**Interfaces:**
- Consumes: `ResetDecision.decide`, `DurationFormat.format`, `LootTimes` (Tasks 1 and 2).
- Produces: `record TrialVaultResetConfig(long delayMs, String resetMessage, String waitingMessage)` with `static TrialVaultResetConfig load()` reading `IceCream.config` under `modules.trialvaultreset.` (`reset-delay-minutes` as a number, fallback 1440, negative clamped to 0 with a warning logged; message fallbacks equal the `config.yml` defaults in Task 4).
- Produces: `TrialVaultResetListener(TrialVaultResetConfig config, LootTimes lootTimes, LongSupplier clock, Consumer<Runnable> nextTick)` implementing `Listener`, with handlers `onLoot(BlockDispenseLootEvent)` (`MONITOR`, `ignoreCancelled = true`) and `onInteract(PlayerInteractEvent)` (`NORMAL`, `ignoreCancelled = true`).

Behaviour:
- `onLoot`: if the block type is `VAULT` and `getPlayer()` is not null, pass a task to `nextTick` that reads `block.getState()`; if it is an `org.bukkit.block.Vault` that `hasRewardedPlayer(uuid)`, call `retainOnly(pdc, getRewardedPlayers())`, `put(pdc, uuid, clock)` and `update()`.
- `onInteract`: act only for `Action.RIGHT_CLICK_BLOCK` on a `VAULT` block whose state is a `Vault` and whose block data is a `org.bukkit.block.data.type.Vault` not in state `EJECTING`, with `event.getItem()` non-null and of the same `Material` as a non-empty `vault.getKeyItem()`. Then switch on `ResetDecision.decide(vault.hasRewardedPlayer(uuid), lootTimes.get(pdc, uuid), clock, config.delayMs())`:
  - `RESET`: `removeRewardedPlayer(uuid)`, `lootTimes.remove(pdc, uuid)`, `update()`, `event.setCancelled(true)`, send `resetMessage`.
  - `WAIT`: send `waitingMessage` with `<time>` set to `DurationFormat.format(remainingMs)`; do not cancel.
  - `IGNORE`: nothing.

- [ ] **Step 1: Write the failing tests**

Set up `MockBukkit.mock()`, a `PlayerMock`, and Mockito mocks for `Block`, `org.bukkit.block.Vault` (state) and `org.bukkit.block.data.type.Vault` (data). The state mock returns a real container from a second `PlayerMock`'s PDC. The listener gets a fixed clock (`() -> now`), `new LootTimes("icecream")`, a config of `new TrialVaultResetConfig(86_400_000, "reset", "wait <time>")`, and `nextTick` that collects runnables in a list. Default stubs: block type `VAULT`, data state `ACTIVE`, key item `new ItemStack(Material.TRIAL_KEY)`, event item a `TRIAL_KEY`, action `RIGHT_CLICK_BLOCK`. Compare messages as plain text.

Tests:
- `resetsUnrecordedLoot`: rewarded, no stored time; verify `removeRewardedPlayer(uuid)` and `update()`, event cancelled, message `"reset"`.
- `resetsAfterDelay`: stored time `now - 86_400_000`; reset as above and stored time removed.
- `waitsBeforeDelay`: stored time `now - 86_400_000 + 11_520_000`; no `removeRewardedPlayer`, event not cancelled, message `"wait 3h 12m"`.
- `ignoresPlayerWhoHasNotLooted`: not rewarded; no `update()`, not cancelled, no message.
- `ignoresWrongKey`: key item `OMINOUS_TRIAL_KEY`, event item `TRIAL_KEY`; nothing happens.
- `ignoresLeftClickAndEmptyHand`: `LEFT_CLICK_BLOCK` with key, and `RIGHT_CLICK_BLOCK` with null item; nothing happens.
- `ignoresEjectingVault`: data state `EJECTING`; nothing happens.
- `recordsLootNextTick`: fire `onLoot` with a `BlockDispenseLootEvent(player, block, List.of(), null)`; nothing stored until the collected runnable runs; after running, the stored time equals `now`, a stale entry for a player no longer rewarded is gone, and `update()` was called.
- `doesNotRecordForOtherBlocksOrNoPlayer`: block type `TRIAL_SPAWNER`, and a null player; no runnable collected.

A cancelled `BlockDispenseLootEvent` is filtered by `ignoreCancelled`, which the event bus applies; cover it by asserting the annotation on `onLoot` has `ignoreCancelled() == true` and `priority() == MONITOR`.

- [ ] **Step 2: Run the tests and see them fail**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.trialVaultReset.events.*"`
Expected: compilation failure, listener and config not found.

- [ ] **Step 3: Implement `TrialVaultResetConfig` and `TrialVaultResetListener`**

Messages are deserialized at send time with `MiniMessage.miniMessage()`.

- [ ] **Step 4: Run the tests and see them pass**

Run: `./gradlew test --tests "com.nearvanilla.iceCream.modules.trialVaultReset.*"`
Expected: PASS, none skipped.

### Task 4: Module wiring and config

**Files:**
- Create: `src/main/java/com/nearvanilla/iceCream/modules/trialVaultReset/TrialVaultResetModule.java`
- Modify: `src/main/java/com/nearvanilla/iceCream/IceCream.java` (field next to the other modules, `register()` in `onEnable`, `unregister()` in `onDisable`)
- Modify: `src/main/resources/config.yml` (new `trialvaultreset` section after `lootcontainerprotection`)

**Interfaces:**
- Consumes: everything from Tasks 1 to 3.
- Produces: `TrialVaultResetModule implements Module` with `unregister()`, following `LootContainerProtectionModule`. `registerEvents()` builds the listener with `TrialVaultResetConfig.load()`, `new LootTimes(IceCream.instance.namespace())`, `System::currentTimeMillis`, and `task -> Bukkit.getScheduler().runTask(IceCream.instance, task)`. Log name: "Trial Vault Reset".

- [ ] **Step 1: Add the module, the registration and this config block**

```yaml
  trialvaultreset:
    enabled: true
    # Minutes after looting a vault before a player can use a key to reset it. 1440 = 24 hours.
    reset-delay-minutes: 1440
    messages:
      reset: '<green>This vault has been reset for you. Use your key again.</green>'
      waiting: '<gray>You can reuse this vault in <white><time></white>.</gray>'
```

- [ ] **Step 2: Format and run the full build**

Run: `./gradlew spotlessApply build`
Expected: BUILD SUCCESSFUL. Then check the test reports (`build/test-results/test/*.xml`) for `skipped="0"` on every suite, including `ConfigUtilsTest`, which checks the bundled config.
