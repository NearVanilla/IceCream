package com.nearvanilla.iceCream.modules.mapTools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.modules.mapTools.EditSession.Mode;
import com.nearvanilla.iceCream.testutil.DialogTestUtils;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.DialogPlayer;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.Response;
import com.nearvanilla.iceCream.testutil.DialogTestUtils.ShownDialog;
import com.nearvanilla.iceCream.testutil.MapToolsTestUtils.Scenario;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The color picker that the claim forms open. */
class AreaFormsPickColorTest {
  private Scenario scenario;
  private DialogPlayer alex;

  @BeforeEach
  void setUp() {
    scenario = Scenario.open("");
    alex = scenario.editor("Alex");
  }

  @AfterEach
  void tearDown() {
    scenario.close();
  }

  private void click(int x, int z, boolean sneaking) {
    EditActions.click(alex, scenario.overworldBlock(x, 64, z), sneaking);
  }

  /** Draws a rectangle of 10 by 10 blocks at the origin and opens the form. */
  private ShownDialog drawnRectangle() {
    MapToolsUtils.startEditing(alex, EditSession.create(Mode.RECT));
    click(0, 0, false);
    click(9, 9, true);
    alex.nextComponentMessage();
    AreaForms.openCreate(alex);
    return alex.lastDialog();
  }

  private static Response filled(ShownDialog dialog, String id, String label) {
    return dialog.responseWithInitialValues().text("area_id", id).text("label", label);
  }

  private void create(ShownDialog dialog, Response response) {
    dialog.click("Create", response, alex);
  }

  @Test
  void pickingAColorShowsEverySquareColorAndAHexField() {
    ShownDialog dialog = drawnRectangle();

    dialog.click("Pick color", dialog.responseWithInitialValues(), alex);

    ShownDialog picker = alex.lastDialog();
    assertEquals("Pick a color", picker.title());
    assertEquals(17, picker.buttons().size());
    for (String name : NamedTextColor.NAMES.keys()) {
      assertTrue(picker.buttons().contains("■ " + name), name);
    }
    assertEquals(16, NamedTextColor.NAMES.keys().size());
    assertEquals("■ Use hex", picker.buttons().getLast());
    assertEquals("Back", picker.exitLabel());
    assertEquals(4, picker.columns());
    assertEquals(List.of("hex"), picker.inputKeys());
    assertEquals("#FFAA00", picker.text("hex").initial());
    assertEquals(7, picker.text("hex").maxLength());
    assertEquals(List.of("Current: ■ #FFAA00"), picker.body());
  }

  @Test
  void pickingANamedColorGoesBackToTheFormWithItAndWhatWasTyped() {
    ShownDialog dialog = drawnRectangle();
    dialog.click(
        "Pick color",
        filled(dialog, "home", "My label").text("owner", "Alex").number("opacity", 70f),
        alex);

    alex.lastDialog().click("■ red", new Response(), alex);

    ShownDialog form = alex.lastDialog();
    assertEquals("New claim", form.title());
    assertEquals(List.of("Color: ■ red"), form.body());
    assertEquals("home", form.text("area_id").initial());
    assertEquals("My label", form.text("label").initial());
    assertEquals("Alex", form.text("owner").initial());
    assertEquals(70f, form.number("opacity").initial());
  }

  @Test
  void aPickedColorIsTheColorTheClaimIsCreatedWith() {
    ShownDialog dialog = drawnRectangle();
    dialog.click("Pick color", filled(dialog, "home", "Home"), alex);
    alex.lastDialog().click("■ dark_aqua", new Response(), alex);

    ShownDialog form = alex.lastDialog();
    create(form, form.responseWithInitialValues());

    assertEquals("dark_aqua", scenario.store().getArea("home").color());
  }

  @Test
  void aHexCodeCanBePickedWithOrWithoutTheHash() {
    ShownDialog dialog = drawnRectangle();
    dialog.click("Pick color", dialog.responseWithInitialValues(), alex);

    alex.lastDialog().click("■ Use hex", new Response().text("hex", " FF8800 "), alex);

    assertEquals(List.of("Color: ■ #ff8800"), alex.lastDialog().body());
    assertEquals("New claim", alex.lastDialog().title());
  }

  @Test
  void anInvalidHexCodeTellsThePlayerAndShowsThePickerAgain() {
    ShownDialog dialog = drawnRectangle();
    dialog.click("Pick color", dialog.responseWithInitialValues(), alex);

    alex.lastDialog().click("■ Use hex", new Response().text("hex", "banana"), alex);

    assertEquals("Use a hex code such as #ff8800.", MessageTestUtils.nextMessage(alex));
    assertEquals("Pick a color", alex.lastDialog().title());
    assertEquals(3, alex.dialogs().size());
  }

  @Test
  void anEmptyHexCodeIsInvalidToo() {
    ShownDialog dialog = drawnRectangle();
    dialog.click("Pick color", dialog.responseWithInitialValues(), alex);

    alex.lastDialog().click("■ Use hex", new Response().text("hex", "  "), alex);

    assertEquals("Use a hex code such as #ff8800.", MessageTestUtils.nextMessage(alex));
    assertEquals("Pick a color", alex.lastDialog().title());
  }

  @Test
  void goingBackFromThePickerKeepsTheCurrentColor() {
    scenario.configure("modules:\n  maptools:\n    claim-color: aqua\n");
    ShownDialog dialog = drawnRectangle();
    dialog.click("Pick color", filled(dialog, "home", "Home"), alex);

    alex.lastDialog().clickExit(new Response(), alex);

    assertEquals("New claim", alex.lastDialog().title());
    assertEquals(List.of("Color: ■ aqua"), alex.lastDialog().body());
    assertEquals("home", alex.lastDialog().text("area_id").initial());
  }

  @Test
  void aStrangerCannotPickAColorEither() {
    ShownDialog dialog = drawnRectangle();
    DialogPlayer stranger = DialogTestUtils.addPlayer(scenario.server(), "Stranger");

    dialog.click("Pick color", dialog.responseWithInitialValues(), stranger);

    assertEquals(0, stranger.dialogs().size());
  }
}
