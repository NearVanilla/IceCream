package com.nearvanilla.iceCream.testutil;

import com.nearvanilla.iceCream.modules.integrations.SquaremapIntegration;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonChatProvider;
import org.mockbukkit.mockbukkit.MockBukkit;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;

/**
 * Helpers for testing the code that talks to squaremap, DiscordSRV and CarbonChat.
 *
 * <p>The modules detect each plugin through the plugin manager, so a test makes a plugin present by
 * adding a stand-in plugin of that name to MockBukkit ({@link #installPlugin(String)}) and absent
 * by not adding it. The APIs are Mockito mocks handed out by the plugins' own static providers,
 * which {@link #registerSquaremap(Squaremap)} and {@link #registerCarbonChat(CarbonChat)} fill, so
 * the real provider code (including its "not loaded" failure) runs. The providers hold the API in a
 * static field, so a test clears it again with {@link #unregisterSquaremap()} and {@link
 * #registerCarbonChat(CarbonChat)} with null in its teardown.
 */
public final class IntegrationTestUtils {

  private IntegrationTestUtils() {}

  /**
   * Adds a stand-in plugin with the given name to the mock server, so that {@code
   * getPluginManager().getPlugin(name)} finds it.
   *
   * @param name the plugin name, such as {@code squaremap}
   */
  public static void installPlugin(String name) {
    MockBukkit.createMockPlugin(name);
  }

  /**
   * Makes {@code SquaremapProvider.get()} return the given API. The provider's register method is
   * package-private, so it is called through reflection.
   *
   * @param api the squaremap API, usually a Mockito mock
   */
  public static void registerSquaremap(Squaremap api) {
    callProvider("register", api);
  }

  /** Makes {@code SquaremapProvider.get()} fail again, as when squaremap has not loaded. */
  public static void unregisterSquaremap() {
    callProvider("unregister", null);
  }

  /**
   * Makes {@code CarbonChatProvider.carbonChat()} return the given API.
   *
   * @param api the CarbonChat API, or null to make it fail as when CarbonChat has not loaded
   */
  public static void registerCarbonChat(CarbonChat api) {
    CarbonChatProvider.register(api);
  }

  /**
   * Forgets the squaremap API that {@link SquaremapIntegration#init()} stored, which lives in a
   * private static field.
   */
  public static void resetSquaremapIntegration() {
    try {
      Field field = SquaremapIntegration.class.getDeclaredField("squaremapApi");
      field.setAccessible(true);
      field.set(null, null);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not reset the squaremap integration", e);
    }
  }

  private static void callProvider(String name, Squaremap api) {
    try {
      if (api == null) {
        Method unregister = SquaremapProvider.class.getDeclaredMethod(name);
        unregister.setAccessible(true);
        unregister.invoke(null);
      } else {
        Method register = SquaremapProvider.class.getDeclaredMethod(name, Squaremap.class);
        register.setAccessible(true);
        register.invoke(null, api);
      }
    } catch (InvocationTargetException e) {
      throw new IllegalStateException(e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not call SquaremapProvider." + name, e);
    }
  }
}
