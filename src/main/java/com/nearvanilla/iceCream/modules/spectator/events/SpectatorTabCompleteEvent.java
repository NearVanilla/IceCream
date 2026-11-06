package com.nearvanilla.iceCream.modules.spectator.events;

import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.nearvanilla.iceCream.modules.spectator.SpectatorUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Removes the names of players in spectator mode from command tab completion (e.g. /msg, /tp,
 * /seen) so that they do not leak their online state. Players holding the {@code
 * icecream.modules.spectator} permission still see every name.
 *
 * @author Dynant
 * @version 1.0
 * @since 2026-11-06
 */
public class SpectatorTabCompleteEvent implements Listener {

  /**
   * Filters spectating players out of the suggestions sent to a player.
   *
   * @param event the suggestions event
   */
  @EventHandler
  public void onSendSuggestions(AsyncPlayerSendSuggestionsEvent event) {
    if (SpectatorUtils.canBypassPrivateMessage(event.getPlayer())) {
      return;
    }

    Set<String> hiddenNames = new HashSet<>();
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (SpectatorUtils.isInSpectatorMode(online) && !online.equals(event.getPlayer())) {
        hiddenNames.add(online.getName().toLowerCase(Locale.ROOT));
      }
    }
    if (hiddenNames.isEmpty()) {
      return;
    }

    Suggestions original = event.getSuggestions();
    List<Suggestion> kept =
        original.getList().stream()
            .filter(s -> !hiddenNames.contains(s.getText().toLowerCase(Locale.ROOT)))
            .toList();
    if (kept.size() == original.getList().size()) {
      return;
    }

    StringRange range = original.getRange();
    event.setSuggestions(new Suggestions(range, kept));
  }
}
