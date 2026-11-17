package com.nearvanilla.iceCream.testutil;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.nearvanilla.iceCream.IceCream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.incendo.cloud.paper.PaperCommandManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class IceCreamFixtureTest {
  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  @Test
  void installSetsConfigFromYaml() {
    IceCreamFixture.install(server, "modules:\n  hat:\n    enabled: true\n");

    assertTrue(IceCream.config.getBoolean("modules.hat.enabled"));
  }

  @Test
  void installReturnsTheInstalledInstance() {
    IceCream installed = IceCreamFixture.install(server, "");

    assertNotNull(installed);
    assertSame(installed, IceCream.instance);
  }

  @Test
  void installSetsLoggerAndParser() {
    IceCreamFixture.install(server, "");

    assertNotNull(IceCream.logger);
    assertEquals("IceCreamTest", IceCream.logger.getName());
    assertNotNull(IceCream.annotationParser);
  }

  @Test
  void instanceDescribesItself() {
    IceCream plugin = IceCreamFixture.install(server, "answer: 42\n");

    assertTrue(plugin.isEnabled());
    assertEquals("IceCream", plugin.getName());
    assertSame(server, plugin.getServer());
    assertSame(IceCream.logger, plugin.getLogger());
    assertSame(IceCream.config, plugin.getConfig());
    assertEquals(42, plugin.getConfig().getInt("answer"));
  }

  @Test
  void instanceProvidesTheNamespaceForNamespacedKeys() {
    IceCreamFixture.install(server, "");

    NamespacedKey key = new NamespacedKey(IceCream.instance, "x");

    assertEquals("icecream", key.getNamespace());
    assertEquals("x", key.getKey());
  }

  @Test
  void instanceOpensPluginResources() throws IOException {
    IceCreamFixture.install(server, "");

    try (InputStream stream = IceCream.instance.getResource("config.yml")) {
      assertNotNull(stream);
      assertTrue(stream.read() >= 0);
    }
  }

  @Test
  void instanceReturnsAFreshStreamForEachResourceRequest() throws IOException {
    IceCreamFixture.install(server, "");

    try (InputStream first = IceCream.instance.getResource("data/wandering_trades.json");
        InputStream second = IceCream.instance.getResource("data/wandering_trades.json")) {
      assertNotNull(first);
      assertNotNull(second);
      assertNotSame(first, second);
      first.readAllBytes();
      assertTrue(second.read() >= 0);
    }
  }

  @Test
  void instanceReturnsNullForAMissingResource() {
    IceCreamFixture.install(server, "");

    assertNull(IceCream.instance.getResource("missing.yml"));
  }

  @Test
  void dataFolderIsAnExistingDirectoryUsedByTheInstance() {
    IceCream plugin = IceCreamFixture.install(server, "");

    File folder = IceCreamFixture.dataFolder();
    assertNotNull(folder);
    assertTrue(folder.isDirectory());
    assertEquals(folder, plugin.getDataFolder());
  }

  @Test
  void instanceCanRegisterListeners() {
    IceCreamFixture.install(server, "");

    assertDoesNotThrow(
        () -> server.getPluginManager().registerEvents(new Listener() {}, IceCream.instance));
  }

  @Test
  void registeredListenersReceiveEvents() {
    IceCreamFixture.install(server, "");
    AtomicBoolean joined = new AtomicBoolean();
    server
        .getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onJoin(PlayerJoinEvent event) {
                joined.set(true);
              }
            },
            IceCream.instance);

    server.addPlayer();

    assertTrue(joined.get());
  }

  @Test
  void instanceCanScheduleTasks() {
    IceCreamFixture.install(server, "");
    AtomicBoolean flag = new AtomicBoolean();

    server.getScheduler().runTask(IceCream.instance, () -> flag.set(true));
    server.getScheduler().performOneTick();

    assertTrue(flag.get());
  }

  @Test
  void annotationParserAcceptsParseCalls() {
    IceCreamFixture.install(server, "");
    Object command = new Object();

    assertTrue(IceCream.annotationParser.parse(command).isEmpty());

    verify(IceCream.annotationParser).parse(command);
  }

  @Test
  void commandManagerIsLeftNull() {
    IceCreamFixture.install(server, "");

    assertNull(IceCream.commandManager);
  }

  @Test
  void uninstallClearsStatics() {
    IceCreamFixture.install(server, "");
    IceCream.commandManager = mock(PaperCommandManager.class);

    IceCreamFixture.uninstall();

    assertNull(IceCream.instance);
    assertNull(IceCream.config);
    assertNull(IceCream.logger);
    assertNull(IceCream.commandManager);
    assertNull(IceCream.annotationParser);
  }

  @Test
  void uninstallUnregistersTheListenersOfTheInstalledPlugin() {
    IceCreamFixture.install(server, "");
    AtomicBoolean joined = new AtomicBoolean();
    server
        .getPluginManager()
        .registerEvents(
            new Listener() {
              @EventHandler
              public void onJoin(PlayerJoinEvent event) {
                joined.set(true);
              }
            },
            IceCream.instance);

    IceCreamFixture.uninstall();
    server.addPlayer();

    assertFalse(joined.get());
  }

  @Test
  void uninstallDeletesTheDataFolder() {
    IceCreamFixture.install(server, "");
    File folder = IceCreamFixture.dataFolder();
    assertTrue(folder.exists());

    IceCreamFixture.uninstall();

    assertFalse(folder.exists());
    assertNull(IceCreamFixture.dataFolder());
  }

  @Test
  void uninstallWithoutInstallDoesNothing() {
    assertDoesNotThrow(IceCreamFixture::uninstall);
  }

  @Test
  void installTwiceReplacesTheFirstInstall() {
    IceCreamFixture.install(server, "value: first\n");
    File first = IceCreamFixture.dataFolder();

    IceCreamFixture.install(server, "value: second\n");

    assertEquals("second", IceCream.config.getString("value"));
    assertFalse(first.exists());
    assertTrue(IceCreamFixture.dataFolder().isDirectory());
  }
}
