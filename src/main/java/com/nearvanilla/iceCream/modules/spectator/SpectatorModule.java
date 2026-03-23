package com.nearvanilla.iceCream.modules.spectator;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.integrations.CarbonChatIntegration;
import com.nearvanilla.iceCream.modules.integrations.DiscordSRVIntegration;
import com.nearvanilla.iceCream.modules.integrations.DynmapIntegration;
import com.nearvanilla.iceCream.modules.spectator.commands.SpectatorCommand;
import com.nearvanilla.iceCream.modules.spectator.events.SpectatorPlayerAdvancementDoneEvent;
import com.nearvanilla.iceCream.modules.spectator.events.SpectatorPlayerJoinEvent;
import com.nearvanilla.iceCream.modules.spectator.events.SpectatorPlayerQuitEvent;
import org.bukkit.NamespacedKey;

/**
 * SpectatorModule provides spectator mode functionality for staff members. When in spectator mode,
 * players are set to SPECTATOR gamemode, hidden from other players, removed from Dynmap, and
 * DiscordSRV broadcasts fake leave/join messages.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-03-18
 * @see Module
 */
public class SpectatorModule implements Module {
  protected boolean isEnabled = false;
  public static NamespacedKey SPECTATOR_TOGGLE_KEY;
  public static NamespacedKey PREVIOUS_GAMEMODE_KEY;
  public static NamespacedKey DYNMAP_WAS_HIDDEN_KEY;
  public static NamespacedKey PREVIOUS_LOCATION_KEY;

  private DiscordSRVIntegration discordSRV;
  private CarbonChatIntegration carbonChat;

  private static void initKeys() {
    if (SPECTATOR_TOGGLE_KEY != null) return;

    SPECTATOR_TOGGLE_KEY = new NamespacedKey(IceCream.instance, "spectator.enabled");
    PREVIOUS_GAMEMODE_KEY = new NamespacedKey(IceCream.instance, "spectator.previous_gamemode");
    DYNMAP_WAS_HIDDEN_KEY =
        new NamespacedKey(IceCream.instance, "spectator.dynmap_hidden_before_spectator");
    PREVIOUS_LOCATION_KEY = new NamespacedKey(IceCream.instance, "spectator.previous_location");
  }

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.spectator.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return isEnabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new SpectatorCommand());
  }

  @Override
  public void registerEvents() {
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new SpectatorPlayerJoinEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new SpectatorPlayerQuitEvent(), IceCream.instance);
    IceCream.instance
        .getServer()
        .getPluginManager()
        .registerEvents(new SpectatorPlayerAdvancementDoneEvent(), IceCream.instance);
  }

  @Override
  public void register() {
    initKeys();
    if (shouldEnable()) {
      try {
        registerCommands();
        registerEvents();

        DynmapIntegration.init();

        discordSRV = new DiscordSRVIntegration();
        discordSRV.init("modules.spectator.messages");

        carbonChat = new CarbonChatIntegration();
        carbonChat.init(
            "modules.spectator", SpectatorUtils::isInSpectatorMode, "in spectator mode");

        SpectatorUtils.init(discordSRV, carbonChat);

        isEnabled = true;
      } catch (Exception e) {
        IceCream.logger.severe("Failed to register Spectator module: " + e.getMessage());
        return;
      }
      IceCream.logger.info("Spectator module has been enabled.");
    } else {
      IceCream.logger.info("Spectator module is disabled.");
    }
  }

  /** Cleans up the spectator module. Should be called during plugin shutdown. */
  public void unregister() {
    if (!isEnabled) return;

    SpectatorUtils.cleanup();
  }
}
