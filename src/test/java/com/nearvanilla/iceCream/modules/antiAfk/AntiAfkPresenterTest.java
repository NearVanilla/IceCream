package com.nearvanilla.iceCream.modules.antiAfk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.utils.ActionBarUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Checks that the captcha's action bar is reported to features sharing the action bar. */
class AntiAfkPresenterTest {
  private ServerMock server;
  private AntiAfkPresenter presenter;
  private long now;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCream.config = new YamlConfiguration();
    presenter = new AntiAfkPresenter(null);
    now = 0;
    ActionBarUtils.useClock(() -> now);
  }

  @AfterEach
  void tearDown() {
    ActionBarUtils.resetClock();
    IceCream.config = null;
    MockBukkit.unmock();
  }

  @Test
  void countdownUsesActionBar() {
    PlayerMock player = server.addPlayer();

    presenter.countdown(player, "ABC123", 42);

    assertEquals(
        "Type ABC123 in chat (42s)",
        PlainTextComponentSerializer.plainText().serialize(player.nextActionBar()));
    assertTrue(ActionBarUtils.sentWithin(player, 3000));
  }

  @Test
  void issuingShowsCountdown() {
    PlayerMock player = server.addPlayer();

    presenter.issue(player, "ABC123", 60);

    assertTrue(ActionBarUtils.sentWithin(player, 3000));
  }

  @Test
  void clearGivesActionBarBack() {
    PlayerMock player = server.addPlayer();
    presenter.countdown(player, "ABC123", 42);
    player.nextActionBar();

    presenter.clear(player);

    assertEquals(Component.empty(), player.nextActionBar());
    assertFalse(ActionBarUtils.sentWithin(player, 3000));
  }

  @Test
  void successGivesActionBarBack() {
    PlayerMock player = server.addPlayer();
    presenter.countdown(player, "ABC123", 42);

    presenter.success(player);

    assertFalse(ActionBarUtils.sentWithin(player, 3000));
  }
}
