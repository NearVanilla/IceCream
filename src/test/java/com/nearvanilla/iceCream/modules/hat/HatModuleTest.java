package com.nearvanilla.iceCream.modules.hat;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.nextMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.hat.commands.HatCommand;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.InventoryTestUtils;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HatModuleTest {
  private static final String ENABLED = "modules:\n  hat:\n    enabled: true\n";

  private ServerMock server;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private HatModule module(String configYaml) {
    IceCreamFixture.install(server, configYaml);
    return new HatModule();
  }

  // Clicks the helmet slot with a non-equippable item on the cursor, which only the module's
  // listener turns into a hat.
  private InventoryClickEvent clickHelmetSlotWithDirt(PlayerMock player) {
    player.addAttachment(IceCream.instance, HatCommand.PERMISSION, true);
    InventoryView view = InventoryTestUtils.craftingView(player);
    view.setCursor(new ItemStack(Material.DIRT));
    InventoryClickEvent event =
        new InventoryClickEvent(
            view,
            InventoryType.SlotType.ARMOR,
            39,
            ClickType.LEFT,
            InventoryAction.SWAP_WITH_CURSOR);
    server.getPluginManager().callEvent(event);
    return event;
  }

  @Test
  void enabledWhenConfigTrue() {
    assertTrue(module(ENABLED).shouldEnable());
  }

  @Test
  void disabledWhenConfigFalse() {
    assertFalse(module("modules:\n  hat:\n    enabled: false\n").shouldEnable());
  }

  @Test
  void disabledWhenKeyMissing() {
    assertFalse(module("").shouldEnable());
  }

  @Test
  void registerWhenDisabledDoesNothing() {
    HatModule module = module("");
    PlayerMock player = server.addPlayer();

    module.register();
    InventoryClickEvent event = clickHelmetSlotWithDirt(player);

    assertFalse(module.isEnabled());
    verify(IceCream.annotationParser, never()).parse(any(Object.class));
    assertFalse(event.isCancelled());
  }

  @Test
  void registerWhenEnabledMarksEnabled() {
    HatModule module = module(ENABLED);

    module.register();

    assertTrue(module.isEnabled());
  }

  @Test
  void registerWhenEnabledRegistersTheCommand() {
    module(ENABLED).register();

    verify(IceCream.annotationParser).parse(any(HatCommand.class));
  }

  @Test
  void registerWhenEnabledRegistersTheListener() {
    module(ENABLED).register();
    PlayerMock player = server.addPlayer();

    InventoryClickEvent event = clickHelmetSlotWithDirt(player);

    assertTrue(event.isCancelled());
    assertEquals("[Hat] Item set as hat.", nextMessage(player));
    assertNull(player.nextComponentMessage());
  }

  @Test
  void registerWhenCommandRegistrationFailsStaysDisabled() {
    HatModule module = module(ENABLED);
    doThrow(new IllegalStateException("no parser"))
        .when(IceCream.annotationParser)
        .parse(any(Object.class));

    module.register();

    assertFalse(module.isEnabled());
  }

  @Test
  void registerWhenListenerRegistrationFailsStaysDisabled() {
    HatModule module = module(ENABLED);
    when(IceCream.instance.getServer()).thenThrow(new IllegalStateException("no server"));

    module.register();

    assertFalse(module.isEnabled());
  }
}
