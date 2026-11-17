package com.nearvanilla.iceCream.testutil;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.incendo.cloud.annotations.AnnotationParser;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.plugin.MockBukkitPluginLoader;

/**
 * Sets up the static state that the plugin's modules read from {@link IceCream}, so a module can be
 * tested without starting the real plugin.
 *
 * <p>The modules never receive their dependencies. They read the public static fields on {@link
 * IceCream} ({@code instance}, {@code config}, {@code logger}, {@code commandManager} and {@code
 * annotationParser}) directly. {@link #install(ServerMock, String)} fills those fields and {@link
 * #uninstall()} resets them, so one test cannot leak state into the next.
 *
 * <p>The installed plugin is a Mockito mock of {@link IceCream}, not a loaded plugin. It is stubbed
 * only as far as MockBukkit and the modules need: it reports the given server, logger and config,
 * is enabled, is named {@code IceCream} and has a temporary data folder. That is enough to register
 * listeners with {@code server.getPluginManager().registerEvents(listener, IceCream.instance)} and
 * to schedule tasks with {@code server.getScheduler().runTask(IceCream.instance, task)}. Anything
 * else on the mock returns the Mockito default (null, zero or an empty collection), so stub it
 * further in the test if a module needs more. {@code IceCream.annotationParser} is also a Mockito
 * mock, so a module can call {@code IceCream.annotationParser.parse(new XCommand())} and the test
 * can verify the call. {@code IceCream.commandManager} is left null.
 *
 * <p>Install in a setup method after {@code MockBukkit.mock()}, and uninstall in a teardown method
 * before {@code MockBukkit.unmock()}, so the plugin never outlives the server it points at:
 *
 * <pre>{@code
 * private ServerMock server;
 *
 * @BeforeEach
 * void setUp() {
 *   server = MockBukkit.mock();
 *   IceCreamFixture.install(server, "modules:\n  hat:\n    enabled: true\n");
 * }
 *
 * @AfterEach
 * void tearDown() {
 *   IceCreamFixture.uninstall();
 *   MockBukkit.unmock();
 * }
 * }</pre>
 *
 * <p>The fixture holds one install at a time, because the {@link IceCream} fields are static.
 * Installing again replaces the previous install, but tests should still pair every {@code install}
 * with an {@code uninstall}.
 */
public final class IceCreamFixture {
  private static Path dataFolder;

  private IceCreamFixture() {}

  /**
   * Fills the static fields on {@link IceCream} for a test, replacing any earlier install.
   *
   * <p>This sets {@code IceCream.config} from the YAML, {@code IceCream.logger} to the logger named
   * {@code IceCreamTest}, {@code IceCream.annotationParser} to a Mockito mock and {@code
   * IceCream.instance} to a Mockito mock stubbed to use those values and the given server. {@code
   * IceCream.commandManager} is left null.
   *
   * @param server the mock server the plugin belongs to, from {@code MockBukkit.mock()}
   * @param configYaml the plugin configuration as YAML text, or an empty string for an empty
   *     configuration
   * @return the installed plugin, the same object as {@code IceCream.instance}
   * @throws IllegalArgumentException if {@code configYaml} is not valid YAML
   * @throws UncheckedIOException if the temporary data folder cannot be created
   */
  @SuppressWarnings("unchecked")
  public static IceCream install(ServerMock server, String configYaml) {
    uninstall();

    YamlConfiguration config = new YamlConfiguration();
    try {
      config.loadFromString(configYaml);
    } catch (InvalidConfigurationException exception) {
      throw new IllegalArgumentException("Invalid test configuration YAML", exception);
    }

    try {
      dataFolder = Files.createTempDirectory("icecream-test");
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not create the plugin data folder", exception);
    }

    Logger logger = Logger.getLogger("IceCreamTest");
    IceCream plugin = mock(IceCream.class);
    when(plugin.getServer()).thenReturn(server);
    when(plugin.getLogger()).thenReturn(logger);
    when(plugin.getConfig()).thenReturn(config);
    when(plugin.isEnabled()).thenReturn(true);
    when(plugin.getName()).thenReturn("IceCream");
    stubPluginLoader(plugin);
    when(plugin.getDataFolder()).thenReturn(dataFolder.toFile());

    IceCream.config = config;
    IceCream.logger = logger;
    IceCream.annotationParser = mock(AnnotationParser.class);
    IceCream.instance = plugin;
    return plugin;
  }

  /**
   * Resets every static field on {@link IceCream} to null and deletes the temporary data folder.
   *
   * <p>Does nothing if nothing is installed. Deleting the folder is best effort: a file that is
   * still open, such as a database a module did not close, can block its deletion on Windows, in
   * which case it stays in the operating system's temporary directory.
   */
  public static void uninstall() {
    IceCream.instance = null;
    IceCream.config = null;
    IceCream.logger = null;
    IceCream.commandManager = null;
    IceCream.annotationParser = null;

    Path folder = dataFolder;
    dataFolder = null;
    if (folder != null) {
      deleteRecursively(folder);
    }
  }

  /**
   * Returns the temporary directory used as the plugin's data folder in the current install.
   *
   * @return the data folder, or null if nothing is installed
   */
  public static File dataFolder() {
    return dataFolder == null ? null : dataFolder.toFile();
  }

  // MockBukkit's plugin manager asks the plugin's loader to build the listener registrations, so a
  // mocked plugin needs one. The method is deprecated for removal in Paper but is still what
  // MockBukkit calls.
  @SuppressWarnings("removal")
  private static void stubPluginLoader(IceCream plugin) {
    when(plugin.getPluginLoader()).thenReturn(new MockBukkitPluginLoader());
  }

  private static void deleteRecursively(Path folder) {
    try (Stream<Path> paths = Files.walk(folder)) {
      paths.sorted(Comparator.reverseOrder()).forEach(IceCreamFixture::deleteQuietly);
    } catch (IOException ignored) {
      // Best effort, the folder is in the operating system's temporary directory.
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best effort, see deleteRecursively.
    }
  }
}
