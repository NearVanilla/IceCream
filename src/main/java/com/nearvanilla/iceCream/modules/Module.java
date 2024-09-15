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
  /**
   * Determines whether this module should be enabled based on the configuration. If a value cannot
   * be found, it should default to false.
   *
   * @return true if the module should be enabled, false otherwise.
   */
  boolean shouldEnable();

  /**
   * Checks if the module is currently enabled. Can be used in other places to determine if the
   * module is active.
   *
   * @return true if the module is enabled, false otherwise.
   */
  boolean isEnabled();

  /**
   * Responsible for registering all commands associated with the module. This method should be
   * called during the module's initialization phase.
   */
  void registerCommands();

  /**
   * Registers all events associated with the module. The method should be called when the module is
   * initialized.
   */
  void registerEvents();

  /** Performs the necessary registration for the module, including commands and events. */
  void register();
}
