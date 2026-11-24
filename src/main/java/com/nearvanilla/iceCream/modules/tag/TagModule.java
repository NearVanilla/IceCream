package com.nearvanilla.iceCream.modules.tag;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.Module;
import com.nearvanilla.iceCream.modules.tag.commands.TagCommand;
import com.nearvanilla.iceCream.modules.tag.events.TagListener;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/**
 * A game of tag: whoever is "it" hits another player with the "Tag!" name tag to pass it on.
 * Everyone plays by default and can opt out with {@code /tag optout}. Replaces PaperTweaks' Tag
 * module.
 *
 * @author 105hua
 * @version 1.0
 * @since 2026-11-24
 * @see TagGame
 * @see TagCommand
 * @see TagListener
 */
public class TagModule implements Module {
  private boolean enabled;
  private TagGame game;
  private TagListener listener;

  @Override
  public boolean shouldEnable() {
    return IceCream.config.getBoolean("modules.tag.enabled", false);
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void registerCommands() {
    IceCream.annotationParser.parse(new TagCommand(game));
  }

  @Override
  public void registerEvents() {
    listener = new TagListener(game);
    IceCream.instance.getServer().getPluginManager().registerEvents(listener, IceCream.instance);
  }

  @Override
  public void register() {
    if (enabled) return;
    if (!shouldEnable()) {
      IceCream.logger.info("Tag module is disabled.");
      return;
    }
    try {
      String namespace = IceCream.instance.namespace();
      game =
          new TagGame(
              TagConfig.load(),
              new TagStore(namespace),
              new TagItem(namespace),
              Bukkit.getScoreboardManager().getMainScoreboard(),
              Bukkit.getServer(),
              System::currentTimeMillis);
      registerCommands();
      registerEvents();
      enabled = true;
      IceCream.logger.info("Tag module has been enabled.");
    } catch (Exception exception) {
      IceCream.logger.log(Level.SEVERE, "Failed to register Tag module.", exception);
      unregister();
    }
  }

  /** Unregisters the listener and marks the module as disabled. */
  public void unregister() {
    if (listener != null) {
      HandlerList.unregisterAll(listener);
      listener = null;
    }
    enabled = false;
  }
}
