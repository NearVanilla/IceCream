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
import net.draycia.carbon.api.event.events.CarbonPrivateChatEvent;
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
  private CarbonEventSubscription<CarbonPrivateChatEvent> privateMessageSubscription = null;
  private CarbonChat carbonChat = null;
  private final Set<String> allowedChannels = new HashSet<>();
  private Component chatBlocked;
  private Component privateMessageBlocked;
  private Predicate<Player> isHiddenCheck;
  private Predicate<Player> canBypassPrivateMessage;

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

  /** Unregisters the CarbonChat event subscriptions. */
  public void cleanup() {
    if (subscription != null) {
      subscription.dispose();
      subscription = null;
    }
    if (privateMessageSubscription != null) {
      privateMessageSubscription.dispose();
      privateMessageSubscription = null;
    }
    canBypassPrivateMessage = null;
    privateMessageBlocked = null;
    carbonChat = null;
  }

  /**
   * Subscribes to {@link CarbonPrivateChatEvent} so that private messages involving a hidden player
   * (per {@code isHiddenCheck}) are cancelled unless the other party holds the {@code canBypass}
   * permission. This mirrors the vanilla {@code /msg} interception performed by the spectator
   * module's command listener.
   *
   * <p>Caller must have already invoked {@link #init(String, Predicate, String)} successfully (or
   * otherwise ensured that CarbonChat is present). The subscription is disposed by {@link
   * #cleanup()}.
   *
   * @param isHiddenCheck predicate returning true for players in the hidden state
   * @param canBypass predicate returning true for players allowed to interact with hidden players
   * @param blockedMessage component sent to the would-be sender when the message is blocked
   */
  public void initPrivateMessage(
      Predicate<Player> isHiddenCheck, Predicate<Player> canBypass, Component blockedMessage) {
    if (Bukkit.getPluginManager().getPlugin("CarbonChat") == null) {
      return;
    }
    if (carbonChat == null) {
      IceCream.logger.warning(
          "CarbonChat private message interception skipped: integration not initialised.");
      return;
    }
    if (privateMessageSubscription != null) {
      return;
    }

    try {
      this.isHiddenCheck = isHiddenCheck;
      this.canBypassPrivateMessage = canBypass;
      this.privateMessageBlocked = blockedMessage;
      privateMessageSubscription =
          carbonChat
              .eventHandler()
              .subscribe(CarbonPrivateChatEvent.class, this::onCarbonPrivateChat);
    } catch (Exception e) {
      IceCream.logger.warning("Failed to hook into CarbonChat private messages: " + e.getMessage());
    }
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

  private void onCarbonPrivateChat(CarbonPrivateChatEvent event) {
    if (isHiddenCheck == null || canBypassPrivateMessage == null || privateMessageBlocked == null) {
      return;
    }

    Player sender = Bukkit.getPlayer(event.sender().uuid());
    Player recipient = Bukkit.getPlayer(event.recipient().uuid());

    if (sender == null) {
      return;
    }

    boolean senderIsHidden = isHiddenCheck.test(sender);
    boolean recipientIsHidden = recipient != null && isHiddenCheck.test(recipient);

    boolean senderBypasses = canBypassPrivateMessage.test(sender);
    boolean recipientBypasses = recipient != null && canBypassPrivateMessage.test(recipient);

    if (recipientIsHidden && !senderBypasses) {
      event.cancelled(true);
      sender.sendMessage(privateMessageBlocked);
      return;
    }

    if (senderIsHidden && !recipientBypasses) {
      event.cancelled(true);
      sender.sendMessage(privateMessageBlocked);
    }
  }
}
