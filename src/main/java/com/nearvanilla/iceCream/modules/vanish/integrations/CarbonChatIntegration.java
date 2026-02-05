package com.nearvanilla.iceCream.modules.vanish.integrations;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.vanish.VanishUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * Integration with CarbonChat to restrict vanished players to staff chat only.
 *
 * @author Dynant
 * @version 1.0
 * @since 2025-01-27
 */
public class CarbonChatIntegration {

  private static CarbonEventSubscription<CarbonChatEvent> subscription = null;
  private static CarbonChat carbonChat = null;

  /** The channel keys that vanished players are allowed to use. */
  private static final Set<String> ALLOWED_CHANNELS = new HashSet<>();

  private static Component vanishChatBlocked;

  /** Loads allowed channels from config and builds the blocked message. */
  private static void loadAllowedChannels() {
    ALLOWED_CHANNELS.clear();
    List<String> channels =
        IceCream.config.getStringList("modules.vanish.carbon-chat.allowed-channels");
    for (String channel : channels) {
      ALLOWED_CHANNELS.add(channel.toLowerCase());
    }

    // Build blocked message
    if (ALLOWED_CHANNELS.isEmpty()) {
      vanishChatBlocked = Component.text("You cannot use chat while vanished.", NamedTextColor.RED);
    } else {
      vanishChatBlocked =
          Component.text(
              "You can only use " + String.join(", ", ALLOWED_CHANNELS) + " while vanished.",
              NamedTextColor.RED);
    }
  }

  /** Initializes the CarbonChat integration if CarbonChat is present. */
  public static void init() {
    if (Bukkit.getPluginManager().getPlugin("CarbonChat") == null) {
      IceCream.logger.info("CarbonChat not found, skipping integration.");
      return;
    }

    try {
      loadAllowedChannels();
      carbonChat = CarbonChatProvider.carbonChat();
      subscription =
          carbonChat
              .eventHandler()
              .subscribe(CarbonChatEvent.class, CarbonChatIntegration::onCarbonChat);
      IceCream.logger.info("CarbonChat integration enabled.");
    } catch (Exception e) {
      IceCream.logger.warning("Failed to hook into CarbonChat: " + e.getMessage());
    }
  }

  /** Unregisters the CarbonChat event subscription. */
  public static void cleanup() {
    if (subscription != null) {
      subscription.dispose();
      subscription = null;
    }
    carbonChat = null;
  }

  /**
   * Handles CarbonChat events. Blocks messages from vanished players in non-staff channels.
   *
   * @param event the chat event
   */
  private static void onCarbonChat(CarbonChatEvent event) {
    UUID senderUuid = event.sender().uuid();
    Player player = Bukkit.getPlayer(senderUuid);

    if (player == null) return;

    // Check if player is vanished
    if (!VanishUtils.isVanished(player)) return;

    // Check if the channel is an allowed channel
    Key channelKey = event.chatChannel().key();
    String channelName = channelKey.value().toLowerCase();

    if (!ALLOWED_CHANNELS.contains(channelName)) {
      event.cancelled(true);
      player.sendMessage(vanishChatBlocked);
    }
  }
}
