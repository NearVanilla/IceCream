package com.nearvanilla.iceCream.modules.wanderful.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.wanderful.WandType;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import com.nearvanilla.iceCream.testutil.MessageTestUtils;
import com.nearvanilla.iceCream.testutil.WanderfulTestUtils;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

// The tests use WandType, which builds its persistent keys from IceCream.instance when the class
// loads, so the fixture is installed before any test touches the enum.
class WanderfulCommandTest {
  private ServerMock server;
  private PlayerMock player;
  private final WanderfulCommand command = new WanderfulCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, WanderfulTestUtils.CONFIG);
    player = server.addPlayer("Wizard");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void give(CommandSender sender, WandType wand) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.giveWandCommand(source, wand);
  }

  // What the player carries in the inventory slots, without the empty ones.
  private List<ItemStack> carried(PlayerMock target) {
    return Arrays.stream(target.getInventory().getStorageContents())
        .filter(item -> item != null && !item.isEmpty())
        .toList();
  }

  @Test
  void suggestsEveryWandId() {
    assertEquals(List.of("item_frame", "armor_stand"), command.suggestWands(null, ""));
  }

  @Test
  void suggestsEveryWandWhateverWasTyped() {
    assertEquals(List.of("item_frame", "armor_stand"), command.suggestWands(null, "arm"));
  }

  @Test
  void givesTheItemFrameWand() {
    give(player, WandType.ITEM_FRAME);

    assertEquals(List.of(WandType.ITEM_FRAME.createItem()), carried(player));
  }

  @Test
  void givesTheArmorStandWand() {
    give(player, WandType.ARMOR_STAND);

    List<ItemStack> items = carried(player);
    assertEquals(List.of(WandType.ARMOR_STAND.createItem()), items);
    assertEquals(Material.WOODEN_HOE, items.getFirst().getType());
  }

  @Test
  void keepsWhatThePlayerAlreadyCarries() {
    player.getInventory().setItem(0, new ItemStack(Material.DIRT, 3));

    give(player, WandType.ITEM_FRAME);

    assertEquals(new ItemStack(Material.DIRT, 3), player.getInventory().getItem(0));
    assertEquals(WandType.ITEM_FRAME.createItem(), player.getInventory().getItem(1));
  }

  @Test
  void givingTheSameWandTwiceGivesTwoWands() {
    give(player, WandType.ITEM_FRAME);
    give(player, WandType.ITEM_FRAME);

    assertEquals(WandType.ITEM_FRAME.createItem(), player.getInventory().getItem(0));
    assertEquals(WandType.ITEM_FRAME.createItem(), player.getInventory().getItem(1));
  }

  @Test
  void tellsThePlayerWhichWandTheyGot() {
    give(player, WandType.ITEM_FRAME);

    Component message = player.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "You have been given the <gold>item frame</gold> wand!",
        MiniMessage.miniMessage().serialize(message));
    assertNull(player.nextComponentMessage());
  }

  @Test
  void namesTheArmorStandWandWithASpace() {
    give(player, WandType.ARMOR_STAND);

    assertEquals("You have been given the armor stand wand!", MessageTestUtils.nextMessage(player));
  }

  @Test
  void consoleIsToldItMustBeAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    give(console, WandType.ITEM_FRAME);

    assertEquals(
        "You must be a player to use this command.", MessageTestUtils.nextMessage(console));
    assertNull(MessageTestUtils.nextMessage(console));
  }

  @Test
  void consoleUseGivesNobodyAWand() {
    PlayerMock bystander = server.addPlayer("Bystander");

    give(server.getConsoleSender(), WandType.ITEM_FRAME);

    assertTrue(carried(bystander).isEmpty());
    assertTrue(carried(player).isEmpty());
    assertNull(bystander.nextComponentMessage());
    assertNull(player.nextComponentMessage());
  }

  @Test
  void onlyTheCommandSenderGetsTheWand() {
    PlayerMock bystander = server.addPlayer("Bystander");

    give(player, WandType.ITEM_FRAME);

    assertTrue(carried(bystander).isEmpty());
    assertNull(bystander.nextComponentMessage());
  }
}
