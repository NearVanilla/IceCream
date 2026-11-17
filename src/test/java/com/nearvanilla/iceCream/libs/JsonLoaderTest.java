package com.nearvanilla.iceCream.libs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.wanderingTrades.HeadTradeData;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

class JsonLoaderTest {
  record Entry(String name, int count) {}

  @TempDir File folder;

  private File write(String name, String content) throws IOException {
    File file = new File(folder, name);
    Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    return file;
  }

  private InputStream stream(String content) {
    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
  }

  // load(File, Class)

  @Test
  void loadsAFileIntoAClass() throws IOException {
    File file = write("entry.json", "{\"name\":\"apples\",\"count\":3}");

    assertEquals(new Entry("apples", 3), JsonLoader.load(file, Entry.class));
  }

  @Test
  void loadsAFileWithNonAsciiText() throws IOException {
    File file = write("entry.json", "{\"name\":\"héllo ✓\",\"count\":1}");

    assertEquals("héllo ✓", JsonLoader.load(file, Entry.class).name());
  }

  @Test
  void leavesAFieldTheFileDoesNotMentionAtItsDefault() throws IOException {
    File file = write("entry.json", "{\"name\":\"apples\"}");

    assertEquals(new Entry("apples", 0), JsonLoader.load(file, Entry.class));
  }

  @Test
  void ignoresFieldsTheClassDoesNotHave() throws IOException {
    File file = write("entry.json", "{\"name\":\"apples\",\"count\":3,\"extra\":true}");

    assertEquals(new Entry("apples", 3), JsonLoader.load(file, Entry.class));
  }

  @Test
  void aMissingFileThrowsAnIoFailure() {
    File missing = new File(folder, "missing.json");

    JacksonException thrown =
        assertThrows(JacksonException.class, () -> JsonLoader.load(missing, Entry.class));

    assertInstanceOf(IOException.class, thrown.getCause());
  }

  @Test
  void malformedJsonInAFileThrows() throws IOException {
    File file = write("broken.json", "{\"name\":\"apples\",");

    assertThrows(JacksonException.class, () -> JsonLoader.load(file, Entry.class));
  }

  @Test
  void anEmptyFileThrows() throws IOException {
    File file = write("empty.json", "");

    assertThrows(JacksonException.class, () -> JsonLoader.load(file, Entry.class));
  }

  @Test
  void aValueOfTheWrongTypeThrows() throws IOException {
    File file = write("wrong.json", "{\"name\":\"apples\",\"count\":\"many\"}");

    assertThrows(JacksonException.class, () -> JsonLoader.load(file, Entry.class));
  }

  // load(File, TypeReference)

  @Test
  void loadsAFileIntoAList() throws IOException {
    File file = write("list.json", "[{\"name\":\"a\",\"count\":1},{\"name\":\"b\",\"count\":2}]");

    List<Entry> entries = JsonLoader.load(file, new TypeReference<List<Entry>>() {});

    assertEquals(List.of(new Entry("a", 1), new Entry("b", 2)), entries);
  }

  @Test
  void loadsAFileIntoAMap() throws IOException {
    File file = write("map.json", "{\"a\":1,\"b\":2}");

    Map<String, Integer> map = JsonLoader.load(file, new TypeReference<Map<String, Integer>>() {});

    assertEquals(Map.of("a", 1, "b", 2), map);
  }

  @Test
  void loadsAnEmptyListFromAFile() throws IOException {
    File file = write("list.json", "[]");

    assertEquals(List.of(), JsonLoader.load(file, new TypeReference<List<Entry>>() {}));
  }

  @Test
  void aFileThatIsNotAListThrowsWhenAListIsAsked() throws IOException {
    File file = write("entry.json", "{\"name\":\"apples\",\"count\":3}");

    assertThrows(
        JacksonException.class, () -> JsonLoader.load(file, new TypeReference<List<Entry>>() {}));
  }

  @Test
  void aMissingFileThrowsWhenLoadedIntoAList() {
    File missing = new File(folder, "missing.json");

    assertThrows(
        JacksonException.class,
        () -> JsonLoader.load(missing, new TypeReference<List<Entry>>() {}));
  }

  // load(InputStream, Class)

  @Test
  void loadsAStreamIntoAClass() throws IOException {
    assertEquals(
        new Entry("apples", 3),
        JsonLoader.load(stream("{\"name\":\"apples\",\"count\":3}"), Entry.class));
  }

  @Test
  void loadsAStreamWithNonAsciiText() throws IOException {
    assertEquals("héllo ✓", JsonLoader.load(stream("{\"name\":\"héllo ✓\"}"), Entry.class).name());
  }

  @Test
  void malformedJsonInAStreamThrows() {
    assertThrows(JacksonException.class, () -> JsonLoader.load(stream("{\"name\":"), Entry.class));
  }

  @Test
  void anEmptyStreamThrows() {
    assertThrows(JacksonException.class, () -> JsonLoader.load(stream(""), Entry.class));
  }

  // load(InputStream, TypeReference)

  @Test
  void loadsAStreamIntoAList() throws IOException {
    List<Entry> entries =
        JsonLoader.load(
            stream("[{\"name\":\"a\",\"count\":1}]"), new TypeReference<List<Entry>>() {});

    assertEquals(List.of(new Entry("a", 1)), entries);
  }

  @Test
  void malformedJsonInAStreamThrowsWhenLoadedIntoAList() {
    assertThrows(
        JacksonException.class,
        () -> JsonLoader.load(stream("[{\"name\":"), new TypeReference<List<Entry>>() {}));
  }

  @Test
  void loadsABundledResourceThroughAStream() throws IOException {
    try (InputStream in =
        JsonLoader.class.getClassLoader().getResourceAsStream("data/wandering_trades.json")) {
      assertNotNull(in);

      List<HeadTradeData> trades = JsonLoader.load(in, new TypeReference<List<HeadTradeData>>() {});

      assertFalse(trades.isEmpty());
      assertNotNull(trades.getFirst().name);
      assertTrue(trades.getFirst().headCount > 0);
    }
  }

  // save

  @Test
  void savedJsonLoadsBack() throws IOException {
    File file = new File(folder, "saved.json");

    JsonLoader.save(file, new Entry("apples", 3));

    assertEquals(new Entry("apples", 3), JsonLoader.load(file, Entry.class));
  }

  @Test
  void savedListsLoadBack() throws IOException {
    File file = new File(folder, "saved.json");
    List<Entry> entries = List.of(new Entry("a", 1), new Entry("b", 2));

    JsonLoader.save(file, entries);

    assertEquals(entries, JsonLoader.load(file, new TypeReference<List<Entry>>() {}));
  }

  @Test
  void savedNonAsciiTextLoadsBack() throws IOException {
    File file = new File(folder, "saved.json");

    JsonLoader.save(file, new Entry("héllo ✓", 1));

    assertEquals("héllo ✓", JsonLoader.load(file, Entry.class).name());
  }

  @Test
  void savesPrettyPrintedJson() throws IOException {
    File file = new File(folder, "saved.json");

    JsonLoader.save(file, new Entry("apples", 3));

    String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
    assertTrue(text.lines().count() > 1, text);
    assertTrue(text.contains("\n  \"name\""), text);
  }

  @Test
  void saveReplacesTheWholeContentOfAnExistingFile() throws IOException {
    File file = write("saved.json", "{\"name\":\"a very long name indeed\",\"count\":123456789}");

    JsonLoader.save(file, new Entry("a", 1));

    assertEquals(new Entry("a", 1), JsonLoader.load(file, Entry.class));
  }

  @Test
  void saveIntoAFolderThatDoesNotExistThrows() {
    File file = new File(new File(folder, "no-such-folder"), "saved.json");

    JacksonException thrown =
        assertThrows(JacksonException.class, () -> JsonLoader.save(file, new Entry("a", 1)));

    assertInstanceOf(IOException.class, thrown.getCause());
    assertFalse(file.exists());
  }
}
