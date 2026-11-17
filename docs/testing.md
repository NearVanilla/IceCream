# Testing

IceCream is tested with JUnit Jupiter, [MockBukkit](https://mockbukkit.org) and Mockito. The
tests run without a Minecraft server: MockBukkit stands in for Paper, and a fixture stands in for
the plugin itself. New modules and behaviour changes ship with unit tests. This page covers how to
run the tests, where the reports go, the rules every test follows and the patterns to copy.

## Running tests

Run these from the repository root. On Windows use `gradlew.bat` or Git Bash.

```
./gradlew test
./gradlew build
./gradlew test --tests "com.nearvanilla.iceCream.modules.hat.*"
./gradlew test --tests "com.nearvanilla.iceCream.modules.hat.HatModuleTest"
```

- `./gradlew test` runs every test and then writes the coverage report.
- `./gradlew build` runs the tests, the coverage minimum, the Spotless format check and the plugin
  JAR. Run `./gradlew spotlessApply` first so the format check passes. Run `build` right before
  committing.
- `--tests` takes a package pattern or a class name. Use it while you work. The coverage minimum
  belongs to `check` (and so to `build`), not to `test`, so a filtered run never trips it.

The test task prints one summary line at the end, for example
`Tests: 2964 passed, 0 failed, 0 skipped`. Read it. The build fails if any test is skipped (see
Rules).

## Reports

After a run, the reports are under `build/`:

| Report | Path |
| --- | --- |
| Test results (HTML) | `build/reports/tests/test/index.html` |
| Test results (JUnit XML) | `build/test-results/test/` |
| Coverage (HTML) | `build/reports/jacoco/test/html/index.html` |
| Coverage (XML) | `build/reports/jacoco/test/jacocoTestReport.xml` |
| Coverage (CSV) | `build/reports/jacoco/test/jacocoTestReport.csv` |

The coverage HTML report is the quickest way to see which lines a class still misses. JaCoCo 0.8.14
is configured in `build.gradle`; it is the first version that reads Java 25 class files.

### Continuous integration

The `Unit Tests` workflow (`.github/workflows/tests.yml`, job `Run Unit Tests`) runs on pushes to
`main`, on pull requests and by hand, whenever Java files, the build files, `src/test/resources` or
the workflow itself change. It runs the tests, the coverage report and the coverage minimum, then:

- publishes the results as a check run with `mikepenz/action-junit-report`, listing failed tests,
- writes the line and branch coverage to the job summary,
- uploads the `test-reports` artifact (the HTML test report and the JaCoCo reports, kept for seven
  days),
- comments on the pull request when the tests fail.

Pull requests from forks get a read-only token, so for them the check run and the comment are
skipped. The tests still run, and the log, summary and artifact are still there. The separate
`Build Check` workflow (`build.yml`) runs `./gradlew build -x test`, so the tests only run once.

## Rules

- **No skipped tests.** Never use `@Disabled`, and never leave a test that skips. MockBukkit throws
  `UnimplementedOperationException` for methods it has not implemented, and JUnit reports that as a
  skip instead of a failure, which would hide the gap. The build turns any skip into a failure. Work
  around the gap with a `PlayerMock` subclass or a Mockito stub (see MockBukkit gaps below), or delete
  a test that cannot run.
- **Reset static state.** The modules keep state in static fields: the `IceCream.*` fields, module
  `NamespacedKey` fields, stores and caches. A test that sets one puts it back in `@AfterEach`
  (usually `null`), or the next test inherits it. `IceCreamFixture.uninstall()` resets the
  `IceCream.*` fields and unregisters the fixture plugin's listeners. Call it before
  `MockBukkit.unmock()`.
- **No sleeps and no wall-clock timing.** Advance the scheduler with
  `server.getScheduler().performTicks(n)`. If code reads the real clock, give it a time to use, test
  only the parts that do not depend on the clock, or use generous offsets. Never assert on
  sub-second wall-clock tolerances.
- **Keep the coverage minimum.** `build.gradle` sets a minimum line coverage in
  `jacocoTestCoverageVerification`, currently 75 percent. The build fails below it. Raise it, rounded
  down to the nearest 5 percent, when coverage rises well above it. Do not lower it to get a change
  through; add the missing tests.
- **Test behaviour, not mocks.** Assert the outcome a player or server would see: an event
  cancelled or not, items, blocks, messages, persistent data. A test that only checks that a mock
  returned what it was told to return proves nothing.
- **Keep the output clean.** A test that makes production code log a warning or an error runs
  inside `LogCapture`, and asserts the record when the log is part of the behaviour.
- **Production code stays as it is.** Do not change behaviour to make a test easier. When a class
  cannot be tested otherwise, widening a `private` helper to package-private, or extracting a pure
  method from an event handler, is acceptable. If a test shows that production code is wrong, do not
  write a test that pins the wrong behaviour; fix the code or report it.
- **Layout.** A test lives in the same package as the class it tests, is named `ClassNameTest`, is
  package-private and uses JUnit Jupiter only.

### Checking that a test can fail

A test that cannot fail is worse than none. For each guard or branch a test claims to cover,
briefly delete or invert the guard in production, confirm the test fails, and restore it. Be wary
of MockBukkit side effects that make a test pass for the wrong reason. For example, `disconnect()`
clears a player's permission attachments, so a "no permission" test that disconnects first proves
nothing.

## Patterns

### The plugin fixture

The modules do not receive their dependencies. They read the public static fields on `IceCream`
(`instance`, `config`, `logger`, `commandManager` and `annotationParser`). `IceCreamFixture` fills
those fields so a module runs without the real plugin:

```java
@BeforeEach
void setUp() {
  server = MockBukkit.mock();
  IceCreamFixture.install(server, "modules:\n  example:\n    enabled: true\n");
}

@AfterEach
void tearDown() {
  IceCreamFixture.uninstall();
  MockBukkit.unmock();
}
```

The installed plugin is a Mockito mock of `IceCream`, not a loaded plugin. It reports the server,
logger and configuration you gave it, is enabled, is named `IceCream` and uses `icecream` as its
namespace, so `new NamespacedKey(IceCream.instance, "x")` works. `getResource` opens real files from
the plugin's resources and `IceCreamFixture.dataFolder()` is a temporary data folder.
`IceCream.annotationParser` is a Mockito mock, so a test can verify what a module parsed.
`saveResource` does nothing, and `IceCream.commandManager` stays null. Anything else on the mock
returns the Mockito default, so stub more in the test if a class needs it.

See `modules/example/ExampleModuleTest.java`.

### Module test checklist

Every `*Module` class gets a test class with at least these tests:

- `enabledWhenConfigTrue`: with `enabled: true`, `shouldEnable()` is true.
- `disabledWhenKeyMissing`: with an empty configuration, `shouldEnable()` is false.
- `registerWhenDisabledDoesNothing`: `register()` on a disabled module leaves `isEnabled()` false and
  registers nothing.
- `registerWhenEnabledMarksEnabled`: `register()` on an enabled module makes `isEnabled()` true.

A module with logic beyond wiring gets that logic tested too. To check that `register()` really
registered a listener, fire a real event afterwards and assert the outcome instead of verifying
mocks. See `modules/example/ExampleModuleTest.java`, and `modules/hat/HatModuleTest.java` for a
module whose registration can also fail.

### Listeners

Construct the real event and either call the handler or register the listener and fire the event
through the plugin manager. Assert the outcome. A listener declared with `ignoreCancelled = true`
needs a test that fires an already cancelled event through the plugin manager. See
`modules/bonemealableSporeBlossoms/events/SporeBlossomBonemealEventTest.java` and
`modules/spectator/events/SpectatorAdvancementCriterionGrantEventTest.java`.

When a handler checks a permission, grant it with
`player.addAttachment(IceCream.instance, PERMISSION, true)`, as
`modules/hat/events/HatInventoryListenerTest.java` does.

### Commands

Call the annotated command method directly. Pass it a `CommandSourceStack` whose `getSender()`
returns a `PlayerMock` (or `CommandTestUtils.stack(sender)`). Cover the success path, each guard
inside the method and, where the method handles them, non-player senders. Cloud handles command
permissions, so a command method test does not check them. See
`modules/staffMode/commands/StaffModeCommandTest.java`.

### Messages

Read a message as plain text with `MessageTestUtils.nextMessage(player)` and assert its wording.
Assert that the first message exists, and what it says, before asserting that no more arrive.

### Pure helpers

Test them directly, including the boundaries: zero, negative, empty and null where the signature
allows it. See `modules/playerSeen/PlayerSeenUtilsTest.java`.

### Integrations

The squaremap, DiscordSRV and CarbonChat integrations detect each plugin through the plugin
manager. `IntegrationTestUtils.installPlugin(name)` adds a stand-in plugin of that name, so the
plugin is present, and not adding it makes the plugin absent. The APIs are Mockito mocks handed out
through the plugins' own static providers. Test both cases, and clear the providers in `@AfterEach`.
See `modules/integrations/SquaremapIntegrationTest.java`.

### MockBukkit gaps

MockBukkit is incomplete, and each gap is worked around in a shared helper so tests do not repeat
it. Before you write a new workaround, check the helper list below. When you need a new one, make a
`PlayerMock` subclass or a Mockito stub (never let the test skip) and put it in
`src/test/java/com/nearvanilla/iceCream/testutil/` if more than one class could use it.
`AdvancementTestUtils` (no advancement support) and `InventoryTestUtils` (an inventory view that
cannot build a click event) are the smallest examples.

A full-plugin load test is not possible today: MockBukkit loads a plugin by proxying its class with a
ByteBuddy subclass, and `IceCream` is `final`, so the real plugin cannot be loaded.

### Shared helpers

The helpers live in `src/test/java/com/nearvanilla/iceCream/testutil/`. Paths below are relative to
`src/test/java/com/nearvanilla/iceCream/`.

| Helper | Use it for | Example |
| --- | --- | --- |
| `IceCreamFixture` | Installs and resets the static `IceCream` state, and gives a temporary data folder. | `modules/example/ExampleModuleTest.java` |
| `LogCapture` | Collects what `IceCream.logger` records, so a test can assert a warning and keep it out of the output. | `modules/integrations/SquaremapIntegrationTest.java` |
| `MessageTestUtils` | Reads player and console messages as plain text. | `modules/lootContainerProtection/events/LootContainerProtectionListenerTest.java` |
| `CommandTestUtils` | Command sources and contexts, a teleport recording player, and readers for click and hover events in a message. | `modules/mapTools/commands/MapToolsCommandListTest.java` |
| `InventoryTestUtils` | Inventory views that support `InventoryClickEvent`. | `modules/hat/events/HatInventoryListenerTest.java` |
| `AdvancementTestUtils` | Players and advancements for advancement events. | `modules/spectator/events/SpectatorAdvancementCriterionGrantEventTest.java` |
| `LootableTestUtils` | Loot tables, loot chests and storage minecarts, which MockBukkit does not implement. | `modules/lootContainerProtection/events/LootContainerProtectionListenerTest.java` |
| `LecternTestUtils` | Lecterns, books and a player looking at a block. | `modules/readOnlyLectern/events/LecternBlockBreakEventTest.java` |
| `SleepTestUtils` | Players that can sleep and wake, and worlds whose clock reacts to being written. | `modules/onePlayerSleep/events/OnePlayerSleepListenerTest.java` |
| `PlayerSeenTestUtils` | Resets the player seen date formatter, which is kept in a static field. | `modules/playerSeen/commands/SeenCommandTest.java` |
| `WorldTourTestUtils` | Tour players, the joined flag, command sources and click commands. | `modules/worldTour/commands/WorldTourCommandStartEndTest.java` |
| `WanderfulTestUtils` | The wand configuration for the wanderful module. | `modules/wanderful/commands/WanderfulCommandTest.java` |
| `WanderingTradesTestUtils` | Head trades, JSON streams and a reset for the module's static state. | `modules/wanderingTrades/HeadUtilsLoadTest.java` |
| `MapToolsTestUtils` | Worlds, an area store backed by a temporary file, editing players and builders for areas of every shape. Call `reset()` in teardown. | `modules/mapTools/commands/MapToolsCommandCreateTest.java` |
| `DialogTestUtils` | Building, showing and clicking Paper dialogs (see below). | `modules/mapTools/AreaFormsCreateTest.java` |
| `DisplayTestUtils` and `DisplayScenario` | Worlds that keep block display entities, and a map tools scenario with queries for what was drawn. | `modules/mapTools/AreaDisplaysGridTest.java` |
| `IntegrationTestUtils` | Stand-in plugins and the static API providers of the integrations. | `modules/integrations/SquaremapIntegrationTest.java` |

#### Dialogs

Paper builds dialogs through factories that it finds with a `ServiceLoader`, and MockBukkit ships
none, so code that opens a dialog fails with a `NoSuchElementException`. `DialogTestUtils` supplies
the two services, registered in `src/test/resources/META-INF/services`. They build plain recording
objects instead of real dialogs. Add the player with `DialogTestUtils.addPlayer(server, name)`,
which returns a `DialogPlayer`. A plain `PlayerMock` silently drops every dialog shown to it, so a
dialog test that uses one asserts nothing. A `DialogPlayer` keeps each dialog as a `ShownDialog` that
the test can read and click:

```java
DialogPlayer player = DialogTestUtils.addPlayer(server, "Alex");
AreaForms.openCreate(player);
ShownDialog dialog = player.lastDialog();
dialog.click("Create", dialog.responseWithInitialValues().text("label", "Home"), player);
```

## Test classpath notes

Two lines in the `dependencies` block of `build.gradle` exist only for the tests. Re-check both when
you bump `paperApi` or DiscordSRV.

- `org.yaml:snakeyaml:2.2` is declared before the DiscordSRV jar, because that jar bundles an old
  SnakeYAML that breaks Bukkit's `YamlConfiguration`. Declaring it first puts Paper's version ahead
  of the bundled one.
- `org.apache.logging.log4j:log4j-core` is a `testImplementation` dependency because DiscordSRV has a
  field of a log4j-core type and only loads when log4j-core is present.

The DiscordSRV jar also bundles old SLF4J 1.7 classes. If a test fails with a `LoggerFactory` or
SLF4J error, look there first.
