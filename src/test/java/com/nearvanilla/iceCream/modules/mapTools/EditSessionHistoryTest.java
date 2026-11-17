package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.modules.mapTools.EditSession.Snapshot;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.KeyedWorld;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Undo and redo of the changes made to a shape. */
class EditSessionHistoryTest {
  private KeyedWorld overworld;
  private KeyedWorld nether;

  @BeforeEach
  void setUp() {
    var server = MockBukkit.mock();
    overworld = MapToolsTestUtils.addWorld(server, MapToolsTestUtils.OVERWORLD);
    nether = MapToolsTestUtils.addWorld(server, MapToolsTestUtils.NETHER);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private Location at(int x, int z) {
    return new Location(overworld, x, 64, z);
  }

  /** Adds a polygon point the way a click does: take a snapshot, change, then remember. */
  private static void addPoint(EditSession session, Location block) {
    Snapshot before = session.snapshot();
    session.addPoint(block);
    session.remember(before);
  }

  private static void setRadius(EditSession session, int radius) {
    Snapshot before = session.snapshot();
    session.setRadius(radius);
    session.remember(before);
  }

  @Test
  void aNewSessionHasNothingToUndoOrRedo() {
    EditSession session = EditSession.create(Mode.POLYGON);

    assertFalse(session.undo());
    assertFalse(session.redo());
  }

  @Test
  void aSnapshotKeepsTheShapeAsItWas() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 0));
    session.setChanged(true);

    Snapshot snapshot = session.snapshot();
    session.addPoint(at(5, 5));

    assertEquals(List.of(at(0, 0)), snapshot.points());
    assertEquals(MapToolsTestUtils.OVERWORLD, snapshot.world());
    assertTrue(snapshot.changed());
  }

  @Test
  void aSnapshotOfAnUnchangedSessionEqualsTheNextOne() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 0));

    assertEquals(session.snapshot(), session.snapshot());
  }

  @Test
  void aClickThatChangedNothingIsNotRemembered() {
    EditSession session = EditSession.create(Mode.POLYGON);
    session.addPoint(at(0, 0));

    session.remember(session.snapshot());

    assertFalse(session.undo());
  }

  @Test
  void undoPutsTheShapeBackToBeforeTheLastChange() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));

    assertTrue(session.undo());

    assertEquals(List.of(at(0, 0)), session.definingBlocks());
  }

  @Test
  void undoWorksBackToTheEmptyShape() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));

    assertTrue(session.undo());

    assertEquals(List.of(), session.definingBlocks());
    assertNull(session.getWorld());
    assertFalse(session.undo());
  }

  @Test
  void undoStepsBackOneChangeAtATime() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    addPoint(session, at(10, 10));

    assertTrue(session.undo());
    assertEquals(2, session.getPointCount());
    assertTrue(session.undo());
    assertEquals(1, session.getPointCount());
    assertTrue(session.undo());
    assertEquals(0, session.getPointCount());
    assertFalse(session.undo());
  }

  @Test
  void redoAppliesTheUndoneChangeAgain() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    session.undo();

    assertTrue(session.redo());

    assertEquals(List.of(at(0, 0), at(10, 0)), session.definingBlocks());
    assertFalse(session.redo());
  }

  @Test
  void redoStepsForwardThroughEveryUndoneChange() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    addPoint(session, at(10, 10));
    session.undo();
    session.undo();
    session.undo();

    assertTrue(session.redo());
    assertEquals(1, session.getPointCount());
    assertTrue(session.redo());
    assertEquals(2, session.getPointCount());
    assertTrue(session.redo());
    assertEquals(3, session.getPointCount());
    assertFalse(session.redo());
  }

  @Test
  void undoAfterRedoGoesBackAgain() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    session.undo();
    session.redo();

    assertTrue(session.undo());

    assertEquals(List.of(at(0, 0)), session.definingBlocks());
  }

  @Test
  void aNewChangeAfterAnUndoClearsWhatCouldBeRedone() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    session.undo();

    addPoint(session, at(7, 7));

    assertFalse(session.redo());
    assertEquals(List.of(at(0, 0), at(7, 7)), session.definingBlocks());
  }

  @Test
  void aClickThatChangedNothingKeepsWhatCouldBeRedone() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    session.undo();

    session.remember(session.snapshot());

    assertTrue(session.redo());
  }

  @Test
  void undoBringsBackTheUnsavedChangesFlagOfThatMoment() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    Snapshot before = session.snapshot();
    session.addPoint(at(10, 0));
    session.setChanged(true);
    session.remember(before);

    session.undo();
    assertFalse(session.hasChanges());

    session.redo();
    assertTrue(session.hasChanges());
  }

  @Test
  void aChangeThatOnlySetsTheChangedFlagCanBeUndone() {
    EditSession session = EditSession.create(Mode.POLYGON);
    Snapshot before = session.snapshot();
    session.setChanged(true);
    session.remember(before);

    assertTrue(session.undo());

    assertFalse(session.hasChanges());
  }

  @Test
  void undoAndRedoPutDownThePickedCorner() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    session.setPicked(0);

    session.undo();
    assertNull(session.getPicked());

    session.setPicked(0);
    session.redo();
    assertNull(session.getPicked());
  }

  @Test
  void undoRestoresTheCircleCenterAndRadius() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    Snapshot empty = session.snapshot();
    session.setCenter(at(5, 5));
    session.remember(empty);
    setRadius(session, 10);
    setRadius(session, 20);

    session.undo();
    assertEquals(10, session.getRadius());
    session.undo();
    assertNull(session.getRadius());
    assertEquals(at(5, 5), session.getCenter());
    session.undo();
    assertNull(session.getCenter());
  }

  @Test
  void undoRestoresTheCornersOfARectangle() {
    EditSession session = EditSession.create(Mode.RECT);
    Snapshot empty = session.snapshot();
    session.setCorner1(at(0, 0));
    session.remember(empty);
    Snapshot oneCorner = session.snapshot();
    session.setCorner2(at(10, 10));
    session.remember(oneCorner);

    session.undo();
    assertEquals(List.of(at(0, 0)), session.definingBlocks());
    session.redo();
    assertEquals(MapToolsUtils.rectangle(0, 0, 10, 10), session.toGeometry().points());
  }

  @Test
  void undoGoesBackToTheWorldOfTheEarlierShape() {
    EditSession session = EditSession.create(Mode.POLYGON);
    addPoint(session, at(0, 0));
    addPoint(session, at(10, 0));
    addPoint(session, new Location(nether, 3, 64, 3));
    assertEquals(MapToolsTestUtils.NETHER, session.getWorld());

    session.undo();

    assertEquals(MapToolsTestUtils.OVERWORLD, session.getWorld());
    assertEquals(List.of(at(0, 0), at(10, 0)), session.definingBlocks());
  }

  @Test
  void theLastFiftyChangesCanBeUndone() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    for (int radius = 1; radius <= 50; radius++) setRadius(session, radius);

    for (int undone = 0; undone < 50; undone++) {
      assertTrue(session.undo(), "undo number " + (undone + 1));
    }

    assertNull(session.getRadius());
    assertFalse(session.undo());
  }

  @Test
  void aFiftyFirstChangeForgetsTheOldest() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    for (int radius = 1; radius <= 51; radius++) setRadius(session, radius);

    for (int undone = 0; undone < 50; undone++) {
      assertTrue(session.undo(), "undo number " + (undone + 1));
    }

    // The change from no radius to radius 1 was forgotten, so the oldest state left is radius 1.
    assertEquals(1, session.getRadius());
    assertFalse(session.undo());
  }

  @Test
  void manyMoreChangesStillKeepTheLastFifty() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    for (int radius = 1; radius <= 120; radius++) setRadius(session, radius);

    int undone = 0;
    while (session.undo()) undone++;

    assertEquals(50, undone);
    assertEquals(70, session.getRadius());
  }

  @Test
  void everyUndoneChangeCanBeRedoneAgain() {
    EditSession session = EditSession.create(Mode.CIRCLE);
    for (int radius = 1; radius <= 80; radius++) setRadius(session, radius);
    while (session.undo()) {
      // Undo as far as the history goes.
    }

    int redone = 0;
    while (session.redo()) redone++;

    assertEquals(50, redone);
    assertEquals(80, session.getRadius());
  }
}
