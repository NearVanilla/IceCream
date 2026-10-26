package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapPoint;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapAreasStoreTest {
  private static final int THREADS = 8;
  private static final int AREAS_PER_THREAD = 25;

  @TempDir Path tempDir;

  private static MapArea rect(String id) {
    List<MapPoint> points =
        List.of(new MapPoint(0, 0), new MapPoint(10, 0), new MapPoint(10, 10), new MapPoint(0, 10));
    return new MapArea(
        id,
        "minecraft:overworld",
        points,
        id,
        null,
        List.of(),
        null,
        MapArea.RECT,
        null,
        null,
        null,
        null,
        null);
  }

  /** On Folia, players in different regions save claims at the same time. */
  @Test
  void concurrentPutsAreAllSaved() throws Exception {
    File file = tempDir.resolve("map-areas.json").toFile();
    MapAreasStore store = new MapAreasStore(file);
    store.load();

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(THREADS);
    try {
      List<Future<?>> writers = new ArrayList<>();
      for (int thread = 0; thread < THREADS; thread++) {
        int writer = thread;
        writers.add(
            pool.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < AREAS_PER_THREAD; i++) {
                    store.putArea(rect("area-" + writer + "-" + i));
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> writer : writers) writer.get();
    } finally {
      pool.shutdownNow();
    }

    MapAreasStore reloaded = new MapAreasStore(file);
    reloaded.load();
    assertEquals(THREADS * AREAS_PER_THREAD, reloaded.getAreas().size());
    assertFalse(new File(file.getParentFile(), file.getName() + ".tmp").exists());
  }

  /** Readers on other threads iterate the areas while they change. */
  @Test
  void getAreasIsASnapshot() throws Exception {
    MapAreasStore store = new MapAreasStore(tempDir.resolve("map-areas.json").toFile());
    store.load();
    store.putArea(rect("first"));

    Collection<MapArea> before = store.getAreas();
    store.putArea(rect("second"));

    assertEquals(1, before.size());
    assertEquals(2, store.getAreas().size());
  }
}
