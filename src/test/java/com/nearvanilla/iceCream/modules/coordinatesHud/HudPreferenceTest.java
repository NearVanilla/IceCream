package com.nearvanilla.iceCream.modules.coordinatesHud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class HudPreferenceTest {
  private static final NamespacedKey KEY = new NamespacedKey("icecream", "coordinateshud.enabled");
  private static final NamespacedKey LEGACY = new NamespacedKey("vanillatweaks", "coordinateshud");

  private final HudPreference preference = new HudPreference("icecream");
  private PersistentDataContainer pdc;

  @BeforeEach
  void setUp() {
    ServerMock server = MockBukkit.mock();
    pdc = server.addPlayer().getPersistentDataContainer();
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  @Test
  void newPlayerGetsDefaultOff() {
    assertFalse(preference.resolve(pdc, false));
  }

  @Test
  void newPlayerGetsDefaultOn() {
    assertTrue(preference.resolve(pdc, true));
  }

  @Test
  void storesStartingValueSoDefaultChangesOnlyAffectNewPlayers() {
    preference.resolve(pdc, false);

    assertEquals(false, pdc.get(KEY, PersistentDataType.BOOLEAN));
    assertFalse(preference.resolve(pdc, true));
  }

  @Test
  void storedChoiceWinsOverDefault() {
    preference.set(pdc, true);

    assertTrue(preference.resolve(pdc, false));
  }

  @Test
  void savesUnderPluginNamespace() {
    preference.set(pdc, true);

    assertEquals(true, pdc.get(KEY, PersistentDataType.BOOLEAN));
  }

  @Test
  void takesPaperTweaksChoice() {
    // PaperTweaks stores 1 or 0 as a byte, which is how Bukkit's BOOLEAN type stores it too.
    pdc.set(LEGACY, PersistentDataType.BYTE, (byte) 1);

    assertTrue(preference.resolve(pdc, false));
    assertEquals(true, pdc.get(KEY, PersistentDataType.BOOLEAN));
  }

  @Test
  void takesPaperTweaksChoiceToHide() {
    pdc.set(LEGACY, PersistentDataType.BYTE, (byte) 0);

    assertFalse(preference.resolve(pdc, true));
  }

  @Test
  void ownChoiceWinsOverPaperTweaks() {
    pdc.set(LEGACY, PersistentDataType.BYTE, (byte) 1);
    preference.set(pdc, false);

    assertFalse(preference.resolve(pdc, true));
  }

  @Test
  void leavesPaperTweaksEntryInPlace() {
    pdc.set(LEGACY, PersistentDataType.BYTE, (byte) 1);

    preference.resolve(pdc, false);

    assertTrue(pdc.has(LEGACY));
  }

  @Test
  void ignoresUnreadablePaperTweaksEntry() {
    pdc.set(LEGACY, PersistentDataType.STRING, "yes");

    assertTrue(preference.resolve(pdc, true));
  }
}
