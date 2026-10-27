package com.nearvanilla.iceCream.modules.integrations;

import com.nearvanilla.iceCream.IceCream;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.event.CarbonEventSubscription;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;

/**
 * Cancels CarbonChat messages that a module wants to keep out of chat. The module decides through a
 * predicate that receives the sender and the plain text of the message as they typed it.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-10-27
 */
public class CarbonChatInterceptor {

  private CarbonEventSubscription<CarbonChatEvent> subscription = null;

  /**
   * Subscribes to CarbonChat messages if CarbonChat is present.
   *
   * @param shouldCancel returns true when the message from the given player should be cancelled
   */
  public void init(BiPredicate<UUID, String> shouldCancel) {
    if (Bukkit.getPluginManager().getPlugin("CarbonChat") == null) {
      IceCream.logger.info("CarbonChat not found, skipping chat interception.");
      return;
    }

    try {
      subscription =
          CarbonChatProvider.carbonChat()
              .eventHandler()
              .subscribe(
                  CarbonChatEvent.class,
                  event -> {
                    String text =
                        PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
                    if (shouldCancel.test(event.sender().uuid(), text)) {
                      event.cancelled(true);
                    }
                  });
      IceCream.logger.info("CarbonChat chat interception enabled.");
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
  }
}
