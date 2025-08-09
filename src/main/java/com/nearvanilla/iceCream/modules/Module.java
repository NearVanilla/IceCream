package com.nearvanilla.iceCream.modules;

/**
 * Represents the base structure for a module. All modules should implement this interface to ensure
 * they can register their commands and events.
 *
 * @author 105hua
 * @version 1.0
 * @since 2025-08-08
 */
public interface Module {
  boolean shouldEnable();

  boolean isEnabled();

  void registerCommands();

  void registerEvents();

  void register();
}
