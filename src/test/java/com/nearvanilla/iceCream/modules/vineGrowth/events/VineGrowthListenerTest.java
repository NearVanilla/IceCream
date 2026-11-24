package com.nearvanilla.iceCream.modules.vineGrowth.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.utils.ActionBarUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Checks that Vine Growth's toggle message is reported to features sharing the action bar. */
class VineGrowthListenerTest {
  private ServerMock server;
  private long now;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCream plugin = mock(IceCream.class);
    when(plugin.getName()).thenReturn("IceCream");
    when(plugin.namespace()).thenReturn("icecream");
    IceCream.instance = plugin;
    IceCream.config = new YamlConfiguration();
    now = 0;
    ActionBarUtils.useClock(() -> now);
  }

  @AfterEach
  void tearDown() {
    ActionBarUtils.resetClock();
    IceCream.instance = null;
    IceCream.config = null;
    MockBukkit.unmock();
  }

  @Test
  void toggleMessageUsesActionBar() {
    WorldMock world = server.addSimpleWorld("world");
    Block vine = world.getBlockAt(0, 70, 0);
    vine.setType(Material.VINE);
    PlayerMock player = server.addPlayer();
    player.setGameMode(GameMode.CREATIVE);
    ItemStack shears = new ItemStack(Material.SHEARS);
    player.getInventory().setItemInMainHand(shears);

    new VineGrowthListener()
        .onPlayerInteract(
            new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, shears, vine, BlockFace.UP, EquipmentSlot.HAND));

    assertEquals(
        "Vine growth stopped",
        PlainTextComponentSerializer.plainText().serialize(player.nextActionBar()));
    assertTrue(ActionBarUtils.sentWithin(player, 3000));
  }
}
