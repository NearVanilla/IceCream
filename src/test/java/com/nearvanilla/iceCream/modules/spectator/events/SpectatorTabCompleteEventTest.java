package com.nearvanilla.iceCream.modules.spectator.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.nearvanilla.iceCream.modules.spectator.SpectatorModule;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SpectatorTabCompleteEventTest {
  private ServerMock server;
  private Plugin plugin;
  private final SpectatorTabCompleteEvent listener = new SpectatorTabCompleteEvent();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    plugin = MockBukkit.createMockPlugin();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = new NamespacedKey("icecream", "spectator.enabled");
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
    SpectatorModule.SPECTATOR_TOGGLE_KEY = null;
  }

  private PlayerMock player(String name, boolean spectating) {
    PlayerMock player = server.addPlayer(name);
    if (spectating) {
      player
          .getPersistentDataContainer()
          .set(SpectatorModule.SPECTATOR_TOGGLE_KEY, PersistentDataType.BOOLEAN, true);
    }
    return player;
  }

  private AsyncPlayerSendSuggestionsEvent suggest(PlayerMock viewer, String... names) {
    StringRange range = StringRange.at(5);
    List<Suggestion> list =
        java.util.Arrays.stream(names).map(n -> new Suggestion(range, n)).toList();
    return new AsyncPlayerSendSuggestionsEvent(viewer, new Suggestions(range, list), "/msg ");
  }

  private List<String> texts(AsyncPlayerSendSuggestionsEvent event) {
    return event.getSuggestions().getList().stream().map(Suggestion::getText).toList();
  }

  @Test
  void removesSpectatorNameForNormalViewer() {
    PlayerMock viewer = player("Viewer", false);
    player("Hidden", true);
    AsyncPlayerSendSuggestionsEvent event = suggest(viewer, "Hidden", "Viewer", "Other");

    listener.onSendSuggestions(event);

    assertEquals(List.of("Viewer", "Other"), texts(event));
  }

  @Test
  void matchesSpectatorNameCaseInsensitively() {
    PlayerMock viewer = player("Viewer", false);
    player("Hidden", true);
    AsyncPlayerSendSuggestionsEvent event = suggest(viewer, "hidden", "Other");

    listener.onSendSuggestions(event);

    assertEquals(List.of("Other"), texts(event));
  }

  @Test
  void keepsSpectatorNameForViewerWithBypassPermission() {
    PlayerMock viewer = player("Staff", false);
    viewer.addAttachment(plugin, "icecream.modules.spectator", true);
    player("Hidden", true);
    AsyncPlayerSendSuggestionsEvent event = suggest(viewer, "Hidden", "Other");

    listener.onSendSuggestions(event);

    assertEquals(List.of("Hidden", "Other"), texts(event));
  }

  @Test
  void keepsViewersOwnNameWhenSpectating() {
    PlayerMock viewer = player("Hidden", true);
    AsyncPlayerSendSuggestionsEvent event = suggest(viewer, "Hidden", "Other");

    listener.onSendSuggestions(event);

    assertEquals(List.of("Hidden", "Other"), texts(event));
  }

  @Test
  void leavesSuggestionsUntouchedWhenNobodySpectates() {
    PlayerMock viewer = player("Viewer", false);
    player("Other", false);
    AsyncPlayerSendSuggestionsEvent event = suggest(viewer, "Viewer", "Other");
    Suggestions original = event.getSuggestions();

    listener.onSendSuggestions(event);

    assertSame(original, event.getSuggestions());
  }

  @Test
  void keepsNonPlayerSuggestions() {
    PlayerMock viewer = player("Viewer", false);
    player("Hidden", true);
    AsyncPlayerSendSuggestionsEvent event = suggest(viewer, "Hidden", "@a", "~");

    listener.onSendSuggestions(event);

    assertEquals(List.of("@a", "~"), texts(event));
  }
}
