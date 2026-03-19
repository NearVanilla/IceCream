package com.nearvanilla.iceCream.modules.integrations;

import com.nearvanilla.iceCream.IceCream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.event.CarbonEventSubscription;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Shared integration with CarbonChat to restrict hidden players (spectating or vanished) to allowed
 * chat channels only. Each module holds its own instance configured with the appropriate config
 * prefix, hidden-state check, and display name for error messages.
 *
 * <p>Originally implemented by Dynant as part of {@link
 * com.nearvanilla.iceCream.modules.vanish.integrations}, generalized here for reuse across modules.
 *
 * @author Dynant
 * @author 105hua
 * @version 1.0
 * @since 2025-01-27
 */
public class CarbonChatIntegration {

  private CarbonEventSubscription<CarbonChatEvent> subscription = null;
  private CarbonChat carbonChat = null;
  private final Set<String> allowedChannels = new HashSet<>();
  private Component chatBlocked;
  private Predicate<Player> isHiddenCheck;

  /**
   * Initializes the CarbonChat integration if CarbonChat is present.
   *
   * @param configPrefix the config path prefix, e.g. {@code "modules.spectator"}
   * @param isHiddenCheck predicate that returns true when a player is in the hidden state
   * @param stateName human-readable name of the state used in error messages, e.g. {@code "in
   *     spectator mode"} or {@code "vanished"}
   */
  public void init(String configPrefix, Predicate<Player> isHiddenCheck, String stateName) {
    if (Bukkit.getPluginManager().getPlugin("CarbonChat") == null) {
      IceCream.logger.info("CarbonChat not found, skipping integration.");
      return;
    }

    try {
      this.isHiddenCheck = isHiddenCheck;
      loadAllowedChannels(configPrefix, stateName);
      carbonChat = CarbonChatProvider.carbonChat();
      subscription = carbonChat.eventHandler().subscribe(CarbonChatEvent.class, this::onCarbonChat);
      IceCream.logger.info("CarbonChat integration enabled.");
    } catch (Exception e) {
      IceCream.logger.warning("Failed to hook into CarbonChat: " + e.getMessage());
    }
  }

  /** Unregisters the CarbonChat event subscription. */
  public void cleanup() {
    if (subscription != null) {
      subscription.dispose();
      subscription = null;
    }
    carbonChat = null;
  }

  private void loadAllowedChannels(String configPrefix, String stateName) {
    allowedChannels.clear();
    List<String> channels =
        IceCream.config.getStringList(configPrefix + ".carbon-chat.allowed-channels");
    for (String channel : channels) {
      allowedChannels.add(channel.toLowerCase());
    }

    if (allowedChannels.isEmpty()) {
      chatBlocked =
          Component.text("You cannot use chat while " + stateName + ".", NamedTextColor.RED);
    } else {
      chatBlocked =
          Component.text(
              "You can only use "
                  + String.join(", ", allowedChannels)
                  + " while "
                  + stateName
                  + ".",
              NamedTextColor.RED);
    }
  }

  private void onCarbonChat(CarbonChatEvent event) {
    UUID senderUuid = event.sender().uuid();
    Player player = Bukkit.getPlayer(senderUuid);

    if (player == null) return;

    if (!isHiddenCheck.test(player)) return;

    Key channelKey = event.chatChannel().key();
    String channelName = channelKey.value().toLowerCase();

    if (!allowedChannels.contains(channelName)) {
      event.cancelled(true);
      player.sendMessage(chatBlocked);
    }
  }
}
