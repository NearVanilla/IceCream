package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.LogCapture;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.InOrder;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.Registry;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.marker.Icon;
import xyz.jpenilla.squaremap.api.marker.Marker;

/** Loading the marker icons from the icon folder into squaremap. */
class MapIconsTest {
  private static final List<String> DEFAULTS =
      List.of("flag", "home", "pin", "portal", "shop", "star", "tree");

  private Squaremap squaremap;
  private Registry<BufferedImage> registry;
  private File folder;
  private LogCapture log;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    IceCreamFixture.install(MockBukkit.mock(), "");
    log = LogCapture.of(IceCream.logger);
    squaremap = mock(Squaremap.class);
    registry = mock(Registry.class);
    when(squaremap.iconRegistry()).thenReturn(registry);
    folder = new File(IceCreamFixture.dataFolder(), "map-icons");
  }

  @AfterEach
  void tearDown() {
    MapIcons.unload(squaremap);
    log.close();
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void writeIcon(String fileName, int width, int height) throws IOException {
    assertTrue(folder.isDirectory() || folder.mkdirs());
    assertTrue(
        ImageIO.write(
            new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB),
            "png",
            new File(folder, fileName)));
  }

  /** Makes the plugin's saveResource copy the bundled resource into the data folder. */
  private void saveBundledResources() {
    doAnswer(
            invocation -> {
              String path = invocation.getArgument(0);
              File target = new File(IceCreamFixture.dataFolder(), path);
              assertTrue(target.getParentFile().isDirectory() || target.getParentFile().mkdirs());
              try (InputStream in = IceCream.class.getClassLoader().getResourceAsStream(path)) {
                assertNotNull(in, "missing bundled resource " + path);
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
              }
              return null;
            })
        .when(IceCream.instance)
        .saveResource(anyString(), anyBoolean());
  }

  private static Key key(String name) {
    return Key.of("icecream_icon_" + name);
  }

  // The folder and the bundled icons

  @Test
  void aMissingFolderIsFilledWithTheBundledIcons() {
    saveBundledResources();

    MapIcons.load(squaremap, folder);

    for (String name : DEFAULTS) {
      verify(IceCream.instance).saveResource("map-icons/" + name + ".png", false);
      assertTrue(new File(folder, name + ".png").isFile(), name + ".png was not copied");
    }
    assertEquals(DEFAULTS, MapIcons.names());
  }

  @Test
  void anExistingFolderIsNotFilledAgain() throws IOException {
    saveBundledResources();
    writeIcon("custom.png", 8, 8);

    MapIcons.load(squaremap, folder);

    verify(IceCream.instance, never()).saveResource(anyString(), anyBoolean());
    assertEquals(List.of("custom"), MapIcons.names());
  }

  @Test
  void aFolderThatCannotBeListedLoadsNothing() {
    // saveResource does nothing here, so the folder still does not exist afterwards.
    MapIcons.load(squaremap, folder);

    assertTrue(MapIcons.names().isEmpty());
    verify(registry, never()).register(any(Key.class), any(BufferedImage.class));
  }

  // Reading the files

  @Test
  void everyPngIsRegisteredWithSquaremapUnderItsNamedKey() throws IOException {
    writeIcon("pin.png", 16, 24);
    writeIcon("home.png", 32, 32);

    MapIcons.load(squaremap, folder);

    verify(registry, times(2)).register(any(Key.class), any(BufferedImage.class));
    verify(registry).register(eq(key("pin")), any(BufferedImage.class));
    verify(registry).register(eq(key("home")), any(BufferedImage.class));
  }

  @Test
  void theNamesFollowTheFileNameOrderWhateverOrderTheFolderIsListedIn() throws IOException {
    writeIcon("zebra.png", 8, 8);
    writeIcon("apple.png", 8, 8);
    writeIcon("mango.png", 8, 8);
    // Listing a folder has no defined order, so make this one list its files backwards.
    File backwards =
        new File(folder.getPath()) {
          @Override
          public File[] listFiles(FilenameFilter filter) {
            File[] files = super.listFiles(filter);
            Arrays.sort(files, Comparator.reverseOrder());
            return files;
          }
        };

    MapIcons.load(squaremap, backwards);

    assertEquals(List.of("apple", "mango", "zebra"), MapIcons.names());
  }

  @Test
  void theNameIsLowercaseAndTheExtensionMatchIgnoresCase() throws IOException {
    writeIcon("Castle.PNG", 8, 8);

    MapIcons.load(squaremap, folder);

    assertEquals(List.of("castle"), MapIcons.names());
    assertTrue(MapIcons.exists("castle"));
    assertFalse(MapIcons.exists("Castle"));
    verify(registry).register(eq(key("castle")), any(BufferedImage.class));
  }

  @Test
  void filesThatAreNotPngsAreIgnored() throws IOException {
    assertTrue(folder.mkdirs());
    Files.writeString(new File(folder, "notes.txt").toPath(), "hello", StandardCharsets.UTF_8);
    Files.writeString(new File(folder, "photo.jpg").toPath(), "x", StandardCharsets.UTF_8);
    writeIcon("star.png", 8, 8);

    MapIcons.load(squaremap, folder);

    assertEquals(List.of("star"), MapIcons.names());
    assertTrue(log.records().isEmpty());
  }

  @Test
  void aNameWithOtherCharactersIsSkippedWithAWarning() throws IOException {
    writeIcon("my icon.png", 8, 8);
    writeIcon("fine_one-2.png", 8, 8);

    MapIcons.load(squaremap, folder);

    assertEquals(List.of("fine_one-2"), MapIcons.names());
    assertEquals(
        List.of("Skipping map icon my icon.png: use only a-z, 0-9, _ and - in the name."),
        log.messages(Level.WARNING));
  }

  @Test
  void aNameWithCapitalLettersIsFineBecauseItIsLowercased() throws IOException {
    writeIcon("Big-Tree.png", 8, 8);

    MapIcons.load(squaremap, folder);

    assertEquals(List.of("big-tree"), MapIcons.names());
    assertTrue(log.records().isEmpty());
  }

  @Test
  void aPngThatIsNotAnImageIsSkippedWithAWarning() throws IOException {
    assertTrue(folder.mkdirs());
    Files.writeString(
        new File(folder, "broken.png").toPath(), "not an image", StandardCharsets.UTF_8);
    writeIcon("good.png", 8, 8);

    MapIcons.load(squaremap, folder);

    assertEquals(List.of("good"), MapIcons.names());
    assertEquals(
        List.of("Skipping map icon broken.png: not a readable image"), log.messages(Level.WARNING));
    verify(registry, times(1)).register(any(Key.class), any(BufferedImage.class));
  }

  @Test
  void aSkippedFileDoesNotStopTheOthers() throws IOException {
    writeIcon("a-first.png", 8, 8);
    writeIcon("bad name.png", 8, 8);
    writeIcon("c-last.png", 8, 8);

    MapIcons.load(squaremap, folder);

    assertEquals(List.of("a-first", "c-last"), MapIcons.names());
  }

  // Loading again

  @Test
  void anIconThatSquaremapAlreadyHasIsReplaced() throws IOException {
    writeIcon("pin.png", 8, 8);
    when(registry.hasEntry(key("pin"))).thenReturn(true);

    MapIcons.load(squaremap, folder);

    InOrder order = inOrder(registry);
    order.verify(registry).unregister(key("pin"));
    order.verify(registry).register(eq(key("pin")), any(BufferedImage.class));
  }

  @Test
  void anIconSquaremapDoesNotHaveIsNotUnregisteredFirst() throws IOException {
    writeIcon("pin.png", 8, 8);
    when(registry.hasEntry(key("pin"))).thenReturn(false);

    MapIcons.load(squaremap, folder);

    verify(registry, never()).unregister(any(Key.class));
  }

  // Unloading

  @Test
  void unloadRemovesEveryIconFromSquaremapAndForgetsThem() throws IOException {
    writeIcon("a.png", 8, 8);
    writeIcon("b.png", 8, 8);
    MapIcons.load(squaremap, folder);

    MapIcons.unload(squaremap);

    verify(registry).unregister(key("a"));
    verify(registry).unregister(key("b"));
    assertTrue(MapIcons.names().isEmpty());
    assertFalse(MapIcons.exists("a"));
  }

  @Test
  void unloadWithNothingLoadedDoesNothing() {
    MapIcons.unload(squaremap);

    verify(registry, never()).unregister(any(Key.class));
  }

  // Looking icons up

  @Test
  void existsKnowsTheLoadedNamesOnly() throws IOException {
    writeIcon("pin.png", 8, 8);
    MapIcons.load(squaremap, folder);

    assertTrue(MapIcons.exists("pin"));
    assertFalse(MapIcons.exists("flag"));
    assertFalse(MapIcons.exists(null));
  }

  @Test
  void theNamesCannotBeUsedToChangeTheLoadedIcons() throws IOException {
    writeIcon("pin.png", 8, 8);
    MapIcons.load(squaremap, folder);

    List<String> names = MapIcons.names();

    assertThrows(UnsupportedOperationException.class, () -> names.add("sneaky"));
    assertEquals(List.of("pin"), MapIcons.names());
  }

  @Test
  void aMarkerShowsTheIconAtItsOwnSize() throws IOException {
    writeIcon("pin.png", 16, 24);
    MapIcons.load(squaremap, folder);

    Marker marker = MapIcons.marker("pin", Point.of(10.5, 20.5));

    Icon icon = assertInstanceOf(Icon.class, marker);
    assertEquals(Point.of(10.5, 20.5), icon.point());
    assertEquals(key("pin"), icon.image());
    assertEquals(16, icon.sizeX());
    assertEquals(24, icon.sizeZ());
  }

  @Test
  void aMarkerWithoutAnIconNameIsNull() throws IOException {
    writeIcon("pin.png", 8, 8);
    MapIcons.load(squaremap, folder);

    assertNull(MapIcons.marker(null, Point.of(0, 0)));
  }

  @Test
  void aMarkerWithAnUnknownIconNameIsNull() throws IOException {
    writeIcon("pin.png", 8, 8);
    MapIcons.load(squaremap, folder);

    assertNull(MapIcons.marker("flag", Point.of(0, 0)));
  }

  @Test
  void aMarkerAfterUnloadIsNull() throws IOException {
    writeIcon("pin.png", 8, 8);
    MapIcons.load(squaremap, folder);
    MapIcons.unload(squaremap);

    assertNull(MapIcons.marker("pin", Point.of(0, 0)));
  }
}
