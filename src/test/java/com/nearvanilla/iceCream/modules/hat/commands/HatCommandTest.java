package com.nearvanilla.iceCream.modules.hat.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class HatCommandTest {
  private ServerMock server;
  private PlayerMock player;
  private final HatCommand command = new HatCommand();

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    player = server.addPlayer("Hatter");
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private void allowHats(PlayerMock target) {
    target.addAttachment(IceCream.instance, HatCommand.PERMISSION, true);
  }

  private void hat(CommandSender sender) {
    CommandSourceStack source = mock(CommandSourceStack.class);
    when(source.getSender()).thenReturn(sender);
    command.hat(source);
  }

  private String nextMessage(PlayerMock target) {
    Component message = target.nextComponentMessage();
    return message == null ? null : PlainTextComponentSerializer.plainText().serialize(message);
  }

  private ItemStack helmet() {
    return player.getInventory().getItem(EquipmentSlot.HEAD);
  }

  @Test
  void permissionIsTheHatNode() {
    assertEquals("icecream.modules.hat", HatCommand.PERMISSION);
  }

  @Test
  void putsTheHeldItemOnTheHead() {
    allowHats(player);
    player.getInventory().setItemInMainHand(new ItemStack(Material.PUMPKIN));

    hat(player);

    assertEquals(Material.PUMPKIN, helmet().getType());
    assertTrue(player.getInventory().getItemInMainHand().isEmpty());
  }

  @Test
  void keepsTheWholeHeldStackOnTheHead() {
    allowHats(player);
    player.getInventory().setItemInMainHand(new ItemStack(Material.DIRT, 12));

    hat(player);

    assertEquals(new ItemStack(Material.DIRT, 12), helmet());
  }

  @Test
  void tellsThePlayerTheItemWasSetAsAHat() {
    allowHats(player);
    player.getInventory().setItemInMainHand(new ItemStack(Material.PUMPKIN));

    hat(player);

    assertEquals("[Hat] Item set as hat.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void swapsTheHeldItemWithTheItemOnTheHead() {
    allowHats(player);
    player.getInventory().setItemInMainHand(new ItemStack(Material.PUMPKIN));
    player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));

    hat(player);

    assertEquals(Material.PUMPKIN, helmet().getType());
    assertEquals(Material.DIAMOND_HELMET, player.getInventory().getItemInMainHand().getType());
  }

  @Test
  void swappingTwiceRestoresTheOriginalItems() {
    allowHats(player);
    player.getInventory().setItemInMainHand(new ItemStack(Material.PUMPKIN));
    player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));

    hat(player);
    hat(player);

    assertEquals(Material.DIAMOND_HELMET, helmet().getType());
    assertEquals(Material.PUMPKIN, player.getInventory().getItemInMainHand().getType());
  }

  @Test
  void anEmptyHandTakesTheHelmetOffWithoutPermission() {
    player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));

    hat(player);

    assertEquals(Material.DIAMOND_HELMET, player.getInventory().getItemInMainHand().getType());
    assertTrue(helmet().isEmpty());
    assertEquals("[Hat] Item set as hat.", nextMessage(player));
  }

  @Test
  void refusesAHeldItemWithoutPermission() {
    player.getInventory().setItemInMainHand(new ItemStack(Material.PUMPKIN));

    hat(player);

    assertEquals("[Hat] You do not have permission to use hats.", nextMessage(player));
    assertNull(nextMessage(player));
  }

  @Test
  void leavesBothItemsAloneWhenRefusing() {
    player.getInventory().setItemInMainHand(new ItemStack(Material.PUMPKIN));
    player.getInventory().setItem(EquipmentSlot.HEAD, new ItemStack(Material.DIAMOND_HELMET));

    hat(player);

    assertEquals(Material.PUMPKIN, player.getInventory().getItemInMainHand().getType());
    assertEquals(Material.DIAMOND_HELMET, helmet().getType());
  }

  @Test
  void rejectsASenderThatIsNotAPlayer() {
    ConsoleCommandSenderMock console = server.getConsoleSender();

    hat(console);

    Component message = console.nextComponentMessage();
    assertNotNull(message);
    assertEquals(
        "[Hat] This command can only be used by players.",
        PlainTextComponentSerializer.plainText().serialize(message));
    assertNull(console.nextComponentMessage());
    assertNull(nextMessage(player));
  }
}
