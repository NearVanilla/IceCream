package com.nearvanilla.iceCream.modules.tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class TagGameTest {
  private static final long COOLDOWN_MS = 10_000;

  private final TagStore store = new TagStore("icecream");
  private final TagItem item = new TagItem("icecream");
  private final AtomicLong now = new AtomicLong(1_000_000);
  private ServerMock server;
  private Scoreboard scoreboard;
  private Audience everyone;
  private PlayerMock alex;
  private PlayerMock steve;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    scoreboard = server.getScoreboardManager().getMainScoreboard();
    everyone = mock(Audience.class);
    alex = server.addPlayer("Alex");
    steve = server.addPlayer("Steve");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private TagGame game(boolean showMessages, long cooldownMs) {
    return new TagGame(
        new TagConfig(showMessages, cooldownMs, true), store, item, scoreboard, everyone, now::get);
  }

  private TagGame game() {
    return game(true, 0);
  }

  private Team team() {
    return scoreboard.getTeam(TagGame.TEAM_NAME);
  }

  private Objective counter() {
    return scoreboard.getObjective(TagGame.OBJECTIVE_NAME);
  }

  private boolean hasTagItem(PlayerMock player) {
    return Arrays.stream(player.getInventory().getContents()).anyMatch(item::isTagItem);
  }

  private void assertIt(PlayerMock player) {
    assertTrue(store.isIt(player));
    assertTrue(team().hasEntry(player.getName()));
    assertTrue(hasTagItem(player));
    assertEquals(NamedTextColor.RED, player.displayName().color());
    assertEquals(NamedTextColor.RED, player.playerListName().color());
  }

  private void assertNotIt(PlayerMock player) {
    assertFalse(store.isIt(player));
    assertFalse(team().hasEntry(player.getName()));
    assertFalse(hasTagItem(player));
    assertFalse(isRed(player.displayName()));
    assertFalse(isRed(player.playerListName()));
  }

  /** MockBukkit keeps a name reset to null as null, where Paper falls back to the plain name. */
  private static boolean isRed(Component name) {
    return name != null && NamedTextColor.RED.equals(name.color());
  }

  @Test
  void createsARedTeamAndTheCounter() {
    game();

    assertEquals(NamedTextColor.RED, team().color());
    assertEquals(Component.text("Times Tagged"), counter().displayName());
  }

  @Test
  void reusesAnExistingTeamAndCounter() {
    Team team = scoreboard.registerNewTeam(TagGame.TEAM_NAME);
    team.addEntry("Herobrine");

    game();

    assertTrue(team().hasEntry("Herobrine"));
  }

  @Test
  void giveMakesThePlayerItAndAnnouncesIt() {
    TagGame game = game(true, COOLDOWN_MS);

    assertTrue(game.give(alex, steve));

    assertIt(steve);
    assertEquals(1, counter().getScore("Steve").getScore());
    assertEquals(now.get() + COOLDOWN_MS, store.cooldownUntil(steve));
    verify(everyone).sendMessage(TagMessages.tagged("Alex", "Steve"));
  }

  @Test
  void giveKeepsWhoeverIsAlreadyIt() {
    TagGame game = game();
    game.give(alex, alex);

    game.give(alex, steve);

    assertIt(alex);
    assertIt(steve);
  }

  @Test
  void giveRefusesSomeoneAlreadyIt() {
    TagGame game = game();
    game.give(alex, steve);

    assertFalse(game.give(alex, steve));

    assertEquals(1, counter().getScore("Steve").getScore());
    assertEquals(TagMessages.alreadyIt("Steve"), lastMessage(alex));
  }

  @Test
  void giveRefusesAnOptedOutPlayer() {
    TagGame game = game();
    store.setOptedOut(steve, true);

    assertFalse(game.give(alex, steve));

    assertNotIt(steve);
    assertEquals(TagMessages.notPlaying("Steve"), lastMessage(alex));
  }

  @Test
  void giveRefusesAPlayerPaperTweaksMarkedAfk() {
    TagGame game = game();
    steve
        .getPersistentDataContainer()
        .set(new NamespacedKey("vanillatweaks", "afk"), PersistentDataType.BOOLEAN, true);

    assertFalse(game.give(alex, steve));

    assertNotIt(steve);
    assertEquals(TagMessages.AFK, lastMessage(alex));
  }

  @Test
  void tagPassesItOn() {
    TagGame game = game();
    game.give(alex, alex);

    assertTrue(game.tag(alex, steve));

    assertNotIt(alex);
    assertIt(steve);
    assertEquals(1, counter().getScore("Alex").getScore());
    assertEquals(1, counter().getScore("Steve").getScore());
    verify(everyone).sendMessage(TagMessages.tagged("Alex", "Steve"));
  }

  @Test
  void tagDoesNothingWhenTheTaggerIsNotIt() {
    TagGame game = game();

    assertFalse(game.tag(alex, steve));

    assertNotIt(steve);
    assertNull(alex.nextComponentMessage());
  }

  @Test
  void tagWaitsForTheCooldown() {
    TagGame game = game(true, COOLDOWN_MS);
    game.give(steve, alex);
    now.addAndGet(COOLDOWN_MS - 1_500);

    assertFalse(game.tag(alex, steve));

    assertIt(alex);
    assertNotIt(steve);
    assertEquals(TagMessages.cooldown("0:00:02"), lastMessage(alex));

    now.addAndGet(1_500);
    assertTrue(game.tag(alex, steve));
    assertIt(steve);
  }

  @Test
  void optedOutPlayersCannotBeTagged() {
    TagGame game = game();
    game.give(alex, alex);
    store.setOptedOut(steve, true);

    assertFalse(game.tag(alex, steve));

    assertIt(alex);
    assertNotIt(steve);
    assertEquals(TagMessages.notPlaying("Steve"), lastMessage(alex));
  }

  @Test
  void anOptedOutTaggerCannotTag() {
    TagGame game = game();
    // A state opting out never leaves behind, as it clears "it".
    store.setIt(alex, Long.MIN_VALUE);
    store.setOptedOut(alex, true);

    assertFalse(game.tag(alex, steve));

    assertNotIt(steve);
  }

  @Test
  void tagRefusesSomeoneAlreadyIt() {
    TagGame game = game();
    game.give(alex, alex);
    game.give(alex, steve);

    assertFalse(game.tag(alex, steve));

    assertIt(alex);
    assertEquals(TagMessages.alreadyIt("Steve"), lastMessage(alex));
  }

  @Test
  void noAnnouncementsWhenMessagesAreOff() {
    TagGame game = game(false, 0);

    game.give(alex, alex);
    game.tag(alex, steve);
    game.optOut(steve);

    verify(everyone, never()).sendMessage(any(Component.class));
  }

  @Test
  void optOutWhileItClearsItAndAnnouncesIt() {
    TagGame game = game();
    game.give(alex, steve);

    assertTrue(game.optOut(steve));

    assertTrue(store.isOptedOut(steve));
    assertNotIt(steve);
    verify(everyone).sendMessage(TagMessages.stoppedWhileIt("Steve"));
    assertEquals(TagMessages.OPTED_OUT, lastMessage(steve));
  }

  @Test
  void optOutWhenNotItDoesNotAnnounce() {
    TagGame game = game();

    assertTrue(game.optOut(steve));

    assertTrue(game.isOptedOut(steve));
    verify(everyone, never()).sendMessage(any(Component.class));
  }

  @Test
  void optOutTwiceSaysSo() {
    TagGame game = game();
    game.optOut(steve);

    assertFalse(game.optOut(steve));

    assertEquals(TagMessages.ALREADY_OPTED_OUT, lastMessage(steve));
  }

  @Test
  void optInLetsThePlayerBeTaggedAgain() {
    TagGame game = game();
    game.optOut(steve);

    assertTrue(game.optIn(steve));

    assertFalse(game.isOptedOut(steve));
    assertEquals(TagMessages.OPTED_IN, lastMessage(steve));
    game.give(alex, alex);
    assertTrue(game.tag(alex, steve));
  }

  @Test
  void optInWhenPlayingSaysSo() {
    TagGame game = game();

    assertFalse(game.optIn(steve));

    assertEquals(TagMessages.ALREADY_OPTED_IN, lastMessage(steve));
  }

  @Test
  void resetRemovesOnlineAndOfflinePlayers() {
    TagGame game = game();
    game.give(alex, steve);
    team().addEntry("Notch");

    assertEquals(2, game.reset(alex));

    assertNotIt(steve);
    assertTrue(team().getEntries().isEmpty());
    assertEquals(TagMessages.resetRemoved("Steve"), alex.nextComponentMessage());
    assertEquals(TagMessages.resetRemoved("Notch"), alex.nextComponentMessage());
  }

  @Test
  void resetWithNobodyItSaysSo() {
    TagGame game = game();

    assertEquals(0, game.reset(alex));

    assertEquals(TagMessages.RESET_NONE, lastMessage(alex));
  }

  @Test
  void restoreShowsAPlayerWhoIsStillItAsIt() {
    TagGame game = game();
    game.give(alex, steve);
    steve.displayName(null);
    steve.playerListName(null);

    game.restore(steve);

    assertIt(steve);
  }

  @Test
  void restoreClearsAPlayerResetWhileOffline() {
    TagGame game = game();
    game.give(alex, steve);
    team().removeEntry("Steve");

    game.restore(steve);

    assertNotIt(steve);
  }

  @Test
  void restoreClearsAnOptedOutPlayer() {
    TagGame game = game();
    game.give(alex, steve);
    store.setOptedOut(steve, true);

    game.restore(steve);

    assertNotIt(steve);
  }

  @Test
  void restoreTakesOverPaperTweaksIt() {
    TagGame game = game();
    team().addEntry("Steve");
    steve
        .getPersistentDataContainer()
        .set(new NamespacedKey("vanillatweaks", "tag"), PersistentDataType.BOOLEAN, true);
    steve.getInventory().addItem(legacyTagItem());

    game.restore(steve);

    assertIt(steve);
    assertEquals(Long.MIN_VALUE, store.cooldownUntil(steve));
    assertFalse(steve.getPersistentDataContainer().has(new NamespacedKey("vanillatweaks", "tag")));
  }

  @Test
  void restoreLeavesOtherPlayersAlone() {
    TagGame game = game();

    game.restore(steve);

    assertFalse(store.isIt(steve));
    assertFalse(isRed(steve.displayName()));
  }

  @Test
  void showCounterMovesItBetweenSlots() {
    TagGame game = game();

    assertTrue(game.showCounter(TagCounterSlot.SIDEBAR));
    assertEquals(DisplaySlot.SIDEBAR, counter().getDisplaySlot());
    assertEquals(counter(), scoreboard.getObjective(DisplaySlot.SIDEBAR));
    assertFalse(game.showCounter(TagCounterSlot.SIDEBAR));

    assertTrue(game.showCounter(TagCounterSlot.NONE));
    assertNull(scoreboard.getObjective(DisplaySlot.SIDEBAR));
  }

  @Test
  void formatsTimeLeftLikePaperTweaks() {
    assertEquals("0:00:00", TagGame.formatTimeLeft(0));
    assertEquals("0:00:01", TagGame.formatTimeLeft(1));
    assertEquals("0:01:05", TagGame.formatTimeLeft(65_000));
    assertEquals("24:00:00", TagGame.formatTimeLeft(86_400_000));
  }

  private static ItemStack legacyTagItem() {
    ItemStack legacy = new ItemStack(org.bukkit.Material.NAME_TAG);
    legacy.editMeta(
        meta ->
            meta.displayName(
                Component.text("Tag!", NamedTextColor.YELLOW)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
    return legacy;
  }

  private static Component lastMessage(PlayerMock player) {
    Component last = null;
    Component next;
    while ((next = player.nextComponentMessage()) != null) last = next;
    return last;
  }
}
