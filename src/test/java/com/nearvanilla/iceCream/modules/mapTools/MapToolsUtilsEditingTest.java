package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;

/** Putting players in and out of edit mode, and the checks before they leave or take over. */
class MapToolsUtilsEditingTest {
  private static final String WORLD = MapToolsTestUtils.OVERWORLD;

  private Scenario scenario;
  private DialogPlayer alex;
  private DialogPlayer bob;

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
    bob = scenario.editor("Bob");
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private MapArea stored(MapArea area) {
    scenario.store().putArea(area);
    return area;
  }

  private EditSession session(DialogPlayer player) {
    return MapToolsModule.sessions.get(player.getUniqueId());
  }

  /** An edit session of a stored rectangle with a change that was not saved yet. */
  private EditSession withUnsavedChanges(MapArea area) {
    EditSession session = EditSession.edit(area);
    session.moveRectCorner(0, scenario.overworldBlock(-5, 63, -5));
    session.setChanged(true);
    return session;
  }

  @Test
  void startEditingPutsThePlayerInEditMode() {
    EditSession session = EditSession.view();

    MapToolsUtils.startEditing(alex, session);

    assertSame(session, session(alex));
    assertTrue(MapToolsUtils.isEditing(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void aPlayerWithoutASessionIsNotEditing() {
    assertFalse(MapToolsUtils.isEditing(alex));
  }

  @Test
  void aSessionOnlyCountsForItsOwnPlayer() {
    MapToolsUtils.startEditing(alex, EditSession.view());

    assertTrue(MapToolsUtils.isEditing(alex));
    assertFalse(MapToolsUtils.isEditing(bob));
  }

  @Test
  void startEditingReplacesTheSessionOfThePlayer() {
    MapToolsUtils.startEditing(alex, EditSession.view());
    EditSession next = EditSession.create(EditSession.Mode.RECT);

    MapToolsUtils.startEditing(alex, next);

    assertSame(next, session(alex));
  }

  @Test
  void replacingASessionWithUnsavedChangesTellsThePlayer() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));

    MapToolsUtils.startEditing(alex, EditSession.view());

    assertEquals("Discarded the unsaved changes to home.", MessageTestUtils.nextMessage(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void replacingASessionWithoutChangesIsSilent() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, EditSession.edit(home));

    MapToolsUtils.startEditing(alex, EditSession.view());

    assertNull(alex.nextComponentMessage());
  }

  @Test
  void replacingASessionOfAnotherPlayerIsSilent() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));

    MapToolsUtils.startEditing(bob, EditSession.view());

    assertNull(alex.nextComponentMessage());
    assertNull(bob.nextComponentMessage());
  }

  @Test
  void aPlayerWhoLeftIsNotToldAboutDiscardedChanges() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));
    alex.disconnect();

    MapToolsUtils.startEditing(alex, EditSession.view());

    assertNull(alex.nextComponentMessage());
  }

  @Test
  void stopEditingTakesThePlayerOutOfEditMode() {
    MapToolsUtils.startEditing(alex, EditSession.view());

    MapToolsUtils.stopEditing(alex);

    assertFalse(MapToolsUtils.isEditing(alex));
    assertNull(session(alex));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void stopEditingDiscardsUnsavedChangesAndSaysSo() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));

    MapToolsUtils.stopEditing(alex);

    assertFalse(MapToolsUtils.isEditing(alex));
    assertEquals("Discarded the unsaved changes to home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void stopEditingWithoutASessionDoesNothing() {
    MapToolsUtils.startEditing(bob, EditSession.view());

    MapToolsUtils.stopEditing(alex);

    assertFalse(MapToolsUtils.isEditing(alex));
    assertTrue(MapToolsUtils.isEditing(bob));
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void confirmLeaveAllowsLeavingWithoutASession() {
    AtomicInteger ran = new AtomicInteger();

    assertTrue(MapToolsUtils.confirmLeave(alex, ran::incrementAndGet));

    assertNull(alex.lastDialog());
    assertEquals(0, ran.get());
  }

  @Test
  void confirmLeaveAllowsLeavingAViewSessionAndANewShape() {
    MapToolsUtils.startEditing(alex, EditSession.view());
    assertTrue(MapToolsUtils.confirmLeave(alex, () -> {}));

    MapToolsUtils.startEditing(alex, EditSession.create(EditSession.Mode.CIRCLE));
    assertTrue(MapToolsUtils.confirmLeave(alex, () -> {}));

    assertNull(alex.lastDialog());
  }

  @Test
  void confirmLeaveAllowsLeavingAnAreaWithoutChanges() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, EditSession.edit(home));

    assertTrue(MapToolsUtils.confirmLeave(alex, () -> {}));

    assertNull(alex.lastDialog());
  }

  @Test
  void confirmLeaveAsksWhatToDoWithUnsavedChanges() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));

    assertFalse(MapToolsUtils.confirmLeave(alex, () -> {}));

    ShownDialog dialog = alex.lastDialog();
    assertNotNull(dialog);
    assertEquals("Unsaved changes to home", dialog.title());
    assertEquals(List.of("Save or discard your shape changes?"), dialog.body());
    assertEquals(List.of("Save", "Discard"), dialog.buttons());
    assertEquals("Cancel", dialog.exitLabel());
    assertEquals(2, dialog.columns());
  }

  @Test
  void discardingDropsTheChangesAndContinues() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    EditSession session = withUnsavedChanges(home);
    MapToolsUtils.startEditing(alex, session);
    AtomicInteger ran = new AtomicInteger();
    MapToolsUtils.confirmLeave(alex, ran::incrementAndGet);

    alex.lastDialog().click("Discard", new Response(), alex);

    assertEquals(1, ran.get());
    assertFalse(session.hasChanges());
    assertEquals(home, scenario.store().getArea("home"));
  }

  @Test
  void savingStoresTheChangesAndContinues() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));
    AtomicInteger ran = new AtomicInteger();
    MapToolsUtils.confirmLeave(alex, ran::incrementAndGet);

    alex.lastDialog().click("Save", new Response(), alex);

    assertEquals(1, ran.get());
    assertEquals(MapToolsUtils.rectangle(-5, -5, 9, 9), scenario.store().getArea("home").points());
    assertTrue(session(alex).isViewing());
    assertEquals("Saved the changes to home.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void cancellingKeepsTheChangesAndDoesNotContinue() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    EditSession session = withUnsavedChanges(home);
    MapToolsUtils.startEditing(alex, session);
    AtomicInteger ran = new AtomicInteger();
    MapToolsUtils.confirmLeave(alex, ran::incrementAndGet);

    alex.lastDialog().clickExit(new Response(), alex);

    assertEquals(0, ran.get());
    assertSame(session, session(alex));
    assertTrue(session.hasChanges());
  }

  @Test
  void tryEditingStartsEditingTheArea() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));

    assertTrue(MapToolsUtils.tryEditing(alex, home));

    assertEquals("home", session(alex).getAreaId());
    assertEquals(EditSession.Mode.RECT, session(alex).getMode());
    assertFalse(session(alex).hasChanges());
  }

  @Test
  void tryEditingAnAreaThePlayerAlreadyEditsStartsOver() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);
    EditSession first = session(alex);

    assertTrue(MapToolsUtils.tryEditing(alex, home));

    assertNotSame(first, session(alex));
    assertEquals("home", session(alex).getAreaId());
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void tryEditingAnotherAreaWhileNothingIsUnsavedSwitchesAtOnce() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.tryEditing(alex, home);

    assertTrue(MapToolsUtils.tryEditing(alex, shop));

    assertEquals("shop", session(alex).getAreaId());
    assertNull(alex.lastDialog());
  }

  @Test
  void tryEditingAnAreaSomeoneElseEditsIsRefused() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(bob, home);
    EditSession bobs = session(bob);

    assertFalse(MapToolsUtils.tryEditing(alex, home));

    assertEquals("Bob is already editing home.", MessageTestUtils.nextMessage(alex));
    assertNull(session(alex));
    assertSame(bobs, session(bob));
  }

  @Test
  void aRefusedPlayerKeepsTheirCurrentSession() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.tryEditing(bob, home);
    MapToolsUtils.startEditing(alex, EditSession.view());
    EditSession alexs = session(alex);

    assertFalse(MapToolsUtils.tryEditing(alex, home));

    assertSame(alexs, session(alex));
  }

  @Test
  void tryEditingAsksFirstWhenTheCurrentSessionHasUnsavedChanges() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    EditSession unsaved = withUnsavedChanges(home);
    MapToolsUtils.startEditing(alex, unsaved);

    assertFalse(MapToolsUtils.tryEditing(alex, shop));

    assertEquals("Unsaved changes to home", alex.lastDialog().title());
    assertSame(unsaved, session(alex));
  }

  @Test
  void discardingThenStartsEditingTheOtherArea() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));
    MapToolsUtils.tryEditing(alex, shop);

    alex.lastDialog().click("Discard", new Response(), alex);

    assertEquals("shop", session(alex).getAreaId());
    assertFalse(session(alex).hasChanges());
    assertEquals(home, scenario.store().getArea("home"));
  }

  @Test
  void savingThenStartsEditingTheOtherArea() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));
    MapToolsUtils.tryEditing(alex, shop);

    alex.lastDialog().click("Save", new Response(), alex);

    assertEquals("shop", session(alex).getAreaId());
    assertNotNull(scenario.store().getArea("home"));
  }

  @Test
  void theAreaIsLookedUpAgainAfterTheAnswer() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    EditSession unsaved = withUnsavedChanges(home);
    MapToolsUtils.startEditing(alex, unsaved);
    MapToolsUtils.tryEditing(alex, shop);
    scenario.store().removeArea("shop");

    alex.lastDialog().click("Discard", new Response(), alex);

    assertSame(unsaved, session(alex));
  }

  @Test
  void theAreaChosenAfterTheAnswerIsTheOneAsItIsNow() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));
    MapToolsUtils.tryEditing(alex, shop);
    scenario.store().putArea(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 59, 9));

    alex.lastDialog().click("Discard", new Response(), alex);

    assertEquals(MapToolsUtils.rectangle(20, 0, 59, 9), session(alex).toGeometry().points());
  }

  @Test
  void nobodyIsBlockedWhenNobodyIsEditing() {
    assertFalse(MapToolsUtils.blockedByEditor(alex, "home"));

    assertNull(alex.nextComponentMessage());
  }

  @Test
  void anotherPlayerEditingBlocksTheChange() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(bob, home);

    assertTrue(MapToolsUtils.blockedByEditor(alex, "home"));

    assertEquals("Bob is editing this claim right now.", MessageTestUtils.nextMessage(alex));
    assertNull(bob.nextComponentMessage());
  }

  @Test
  void theConsoleIsBlockedByAPlayerEditing() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(bob, home);
    ConsoleCommandSenderMock console = scenario.server().getConsoleSender();

    assertTrue(MapToolsUtils.blockedByEditor(console, "home"));

    assertEquals("Bob is editing this claim right now.", MessageTestUtils.nextMessage(console));
  }

  @Test
  void theOwnEditWithoutChangesDoesNotBlock() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.tryEditing(alex, home);

    assertFalse(MapToolsUtils.blockedByEditor(alex, "home"));

    assertNull(alex.nextComponentMessage());
  }

  @Test
  void theOwnEditWithUnsavedChangesBlocks() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsUtils.startEditing(alex, withUnsavedChanges(home));

    assertTrue(MapToolsUtils.blockedByEditor(alex, "home"));

    assertEquals(
        "Save or stop your own changes to this claim first.", MessageTestUtils.nextMessage(alex));
  }

  @Test
  void someoneEditingAnotherAreaDoesNotBlock() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapArea shop = stored(MapToolsTestUtils.rect("shop", WORLD, 20, 0, 29, 9));
    MapToolsUtils.tryEditing(bob, shop);

    assertFalse(MapToolsUtils.blockedByEditor(alex, home.id()));
  }

  @Test
  void someoneOnlyViewingDoesNotBlock() {
    MapToolsUtils.startEditing(bob, EditSession.view());

    assertFalse(MapToolsUtils.blockedByEditor(alex, "home"));
  }

  @Test
  void aSessionOfAPlayerWhoIsNotOnlineDoesNotBlock() {
    MapArea home = stored(MapToolsTestUtils.rect("home", WORLD, 0, 0, 9, 9));
    MapToolsModule.sessions.put(UUID.randomUUID(), EditSession.edit(home));

    assertFalse(MapToolsUtils.blockedByEditor(alex, "home"));
  }
}
