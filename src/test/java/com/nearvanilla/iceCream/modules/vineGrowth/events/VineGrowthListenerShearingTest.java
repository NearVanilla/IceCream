package com.nearvanilla.iceCream.modules.vineGrowth.events;

import static com.nearvanilla.iceCream.testutil.MessageTestUtils.plainText;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.vineGrowth.VineGrowthUtils;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
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
import org.mockbukkit.mockbukkit.sound.AudioExperience;
import org.mockbukkit.mockbukkit.util.SpawnedParticle;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * What the listener does when a player right clicks a vine with shears, fired through the server.
 */
class VineGrowthListenerShearingTest {
  private static final String STOPPED = "Vine growth stopped";
  private static final String RESUMED = "Vine growth resumed";

  private ServerMock server;
  private WorldMock world;
  private GardenerMock player;
  private Block vine;

  // MockBukkit does not implement ItemStack.damage, so this records the damage instead.
  private static final class Shears extends ItemStack {
    private int damageTaken;

    private Shears() {
      super(Material.SHEARS);
    }

    @Override
    public ItemStack damage(int amount, LivingEntity livingEntity) {
      damageTaken += amount;
      return this;
    }
  }

  // Remembers which hands the player swung, which MockBukkit does not.
  private static final class GardenerMock extends PlayerMock {
    private final List<EquipmentSlot> swings = new ArrayList<>();

    private GardenerMock(ServerMock server) {
      super(server, "Gardener", UUID.randomUUID());
    }

    @Override
    public void swingHand(EquipmentSlot hand) {
      swings.add(hand);
    }
  }

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    server.getPluginManager().registerEvents(new VineGrowthListener(), IceCream.instance);
    world = server.addSimpleWorld("world");
    player = new GardenerMock(server);
    server.addPlayer(player);
    vine = vine(0, 64, 0);
  }

  @AfterEach
  void tearDown() {
    IceCreamFixture.uninstall();
    MockBukkit.unmock();
  }

  private Block vine(int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    block.setType(Material.VINE);
    return block;
  }

  private Block block(Material type, int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    block.setType(type);
    return block;
  }

  // MockBukkit's Block.getLocation() hands out the block's own Location, where Paper builds a new
  // one on every call. The listener moves the location it is given to the middle of the block,
  // which would walk the mock block along, so it is given a block that hands out copies.
  private static Block unmoved(Block block) {
    if (block == null) {
      return null;
    }
    Block copy = spy(block);
    doAnswer(invocation -> ((Location) invocation.callRealMethod()).clone())
        .when(copy)
        .getLocation();
    return copy;
  }

  private PlayerInteractEvent click(
      Action action, Block clicked, ItemStack item, EquipmentSlot hand) {
    PlayerInteractEvent event =
        new PlayerInteractEvent(player, action, item, unmoved(clicked), BlockFace.NORTH, hand);
    return call(event);
  }

  private PlayerInteractEvent call(PlayerInteractEvent event) {
    server.getPluginManager().callEvent(event);
    return event;
  }

  // A click with the shears in the main hand, where a player holds them.
  private PlayerInteractEvent shear(Block clicked, Shears shears) {
    player.getInventory().setItemInMainHand(new ItemStack(Material.SHEARS));
    return click(Action.RIGHT_CLICK_BLOCK, clicked, shears, EquipmentSlot.HAND);
  }

  private SpawnedParticle onlyParticle() {
    List<SpawnedParticle> particles = world.getSpawnedParticles();
    assertEquals(1, particles.size());
    return particles.getFirst();
  }

  private AudioExperience onlySound() {
    List<AudioExperience> sounds = player.getHeardSounds();
    assertEquals(1, sounds.size());
    return sounds.getFirst();
  }

  @Test
  void shearingAVineStopsItGrowing() {
    shear(vine, new Shears());

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void shearingAShearedVineLetsItGrowAgain() {
    shear(vine, new Shears());

    shear(vine, new Shears());

    assertFalse(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void shearingThreeTimesStopsItAgain() {
    shear(vine, new Shears());
    shear(vine, new Shears());

    shear(vine, new Shears());

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void shearingOnlyAffectsTheClickedVine() {
    Block neighbour = vine(0, 65, 0);

    shear(vine, new Shears());

    assertFalse(VineGrowthUtils.isSheared(neighbour));
  }

  @Test
  void shearingKeepsTheShearsFromBeingUsed() {
    PlayerInteractEvent event = shear(vine, new Shears());

    assertEquals(Event.Result.DENY, event.useItemInHand());
  }

  @Test
  void stoppingTellsThePlayerOnTheActionBar() {
    shear(vine, new Shears());

    assertEquals(STOPPED, plainText(player.nextActionBar()));
    assertNull(player.nextActionBar());
  }

  @Test
  void resumingTellsThePlayerOnTheActionBar() {
    shear(vine, new Shears());
    player.nextActionBar();

    shear(vine, new Shears());

    assertEquals(RESUMED, plainText(player.nextActionBar()));
    assertNull(player.nextActionBar());
  }

  @Test
  void theActionBarMessagesCanBeConfigured() {
    IceCream.config.set("modules.vinegrowth.messages.stopped", "<red>Frozen</red>");
    IceCream.config.set("modules.vinegrowth.messages.growing", "<green>Thawed</green>");

    shear(vine, new Shears());
    assertEquals("Frozen", plainText(player.nextActionBar()));
    shear(vine, new Shears());
    assertEquals("Thawed", plainText(player.nextActionBar()));
  }

  @Test
  void shearingDamagesTheShearsByOne() {
    Shears shears = new Shears();

    shear(vine, shears);

    assertEquals(1, shears.damageTaken);
  }

  @Test
  void shearingDamagesTheShearsEveryTime() {
    Shears shears = new Shears();

    shear(vine, shears);
    shear(vine, shears);

    assertEquals(2, shears.damageTaken);
  }

  @Test
  void shearingInCreativeDoesNotDamageTheShears() {
    player.setGameMode(GameMode.CREATIVE);
    Shears shears = new Shears();

    shear(vine, shears);

    assertEquals(0, shears.damageTaken);
    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void stoppingShowsRedDustOnTheVine() {
    shear(vine, new Shears());

    SpawnedParticle particle = onlyParticle();
    assertEquals(Particle.DUST, particle.particle());
    assertEquals(4, particle.count());
    assertEquals(0.5, particle.x());
    assertEquals(64.5, particle.y());
    assertEquals(0.5, particle.z());
    assertEquals(0.12, particle.offsetX());
    assertEquals(0.12, particle.offsetY());
    assertEquals(0.12, particle.offsetZ());
    assertEquals(0.0, particle.speedX());
    Particle.DustOptions dust = assertInstanceOf(Particle.DustOptions.class, particle.data());
    assertEquals(Color.fromRGB(176, 74, 66), dust.getColor());
    assertEquals(0.8f, dust.getSize());
  }

  @Test
  void resumingShowsGreenDustOnTheVine() {
    shear(vine, new Shears());
    world.clearSpawnedParticles();

    shear(vine, new Shears());

    Particle.DustOptions dust = assertInstanceOf(Particle.DustOptions.class, onlyParticle().data());
    assertEquals(Color.fromRGB(112, 186, 106), dust.getColor());
  }

  @Test
  void stoppingPlaysAGrowingSoundAtTheVine() {
    shear(vine, new Shears());

    AudioExperience sound = onlySound();
    assertEquals("block.growing_plant.crop", sound.getSound());
    assertEquals(1.0f, sound.getVolume());
    assertEquals(1.4f, sound.getPitch());
    assertEquals(0.5, sound.getLocation().getX());
    assertEquals(64.5, sound.getLocation().getY());
    assertEquals(0.5, sound.getLocation().getZ());
  }

  @Test
  void resumingPlaysAShearsSound() {
    shear(vine, new Shears());
    player.getHeardSounds().clear();

    shear(vine, new Shears());

    AudioExperience sound = onlySound();
    assertEquals("item.shears.snip", sound.getSound());
    assertEquals(1.0f, sound.getPitch());
  }

  @Test
  void aShearedVineKeepsItsMarkerWhenAnotherVineIsSheared() {
    Block other = vine(3, 64, 3);
    shear(vine, new Shears());

    shear(other, new Shears());

    assertTrue(VineGrowthUtils.isSheared(vine));
    assertTrue(VineGrowthUtils.isSheared(other));
  }

  private void assertNothingHappened(PlayerInteractEvent event, Shears shears, Block clicked) {
    assertFalse(VineGrowthUtils.isSheared(clicked));
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertNull(player.nextActionBar());
    assertTrue(world.getSpawnedParticles().isEmpty());
    assertTrue(player.getHeardSounds().isEmpty());
    assertEquals(0, shears.damageTaken);
  }

  @Test
  void ignoresALeftClick() {
    Shears shears = new Shears();

    PlayerInteractEvent event = click(Action.LEFT_CLICK_BLOCK, vine, shears, EquipmentSlot.HAND);

    assertNothingHappened(event, shears, vine);
  }

  @Test
  void ignoresAClickInTheAir() {
    Shears shears = new Shears();

    PlayerInteractEvent event = click(Action.RIGHT_CLICK_AIR, null, shears, EquipmentSlot.HAND);

    assertNothingHappened(event, shears, vine);
  }

  @Test
  void ignoresAClickOnAnotherBlock() {
    Block stone = block(Material.STONE, 3, 64, 0);
    Shears shears = new Shears();

    PlayerInteractEvent event = shear(stone, shears);

    assertNothingHappened(event, shears, stone);
  }

  @Test
  void ignoresAClickWithAnEmptyHand() {
    PlayerInteractEvent event = click(Action.RIGHT_CLICK_BLOCK, vine, null, EquipmentSlot.HAND);

    assertFalse(VineGrowthUtils.isSheared(vine));
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertNull(player.nextActionBar());
  }

  @Test
  void ignoresAClickWithAnotherItem() {
    PlayerInteractEvent event =
        click(Action.RIGHT_CLICK_BLOCK, vine, new ItemStack(Material.STICK), EquipmentSlot.HAND);

    assertFalse(VineGrowthUtils.isSheared(vine));
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertNull(player.nextActionBar());
  }

  @Test
  void ignoresAClickThatWasAlreadyCancelled() {
    Shears shears = new Shears();
    PlayerInteractEvent cancelled =
        new PlayerInteractEvent(
            player,
            Action.RIGHT_CLICK_BLOCK,
            shears,
            unmoved(vine),
            BlockFace.NORTH,
            EquipmentSlot.HAND);
    cancelled.setCancelled(true);

    call(cancelled);

    assertFalse(VineGrowthUtils.isSheared(vine));
    assertEquals(0, shears.damageTaken);
    assertNull(player.nextActionBar());
  }

  @Test
  void shearsInTheOffHandWorkWhenTheMainHandIsEmpty() {
    Shears shears = new Shears();

    click(Action.RIGHT_CLICK_BLOCK, vine, shears, EquipmentSlot.OFF_HAND);

    assertTrue(VineGrowthUtils.isSheared(vine));
    assertEquals(1, shears.damageTaken);
  }

  @Test
  void shearsInTheOffHandWorkWhenTheMainHoldsSomethingElse() {
    player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));

    click(Action.RIGHT_CLICK_BLOCK, vine, new Shears(), EquipmentSlot.OFF_HAND);

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void shearsInBothHandsToggleTheVineOnlyOnce() {
    player.getInventory().setItemInMainHand(new ItemStack(Material.SHEARS));
    Shears offHand = new Shears();

    PlayerInteractEvent event =
        click(Action.RIGHT_CLICK_BLOCK, vine, offHand, EquipmentSlot.OFF_HAND);

    assertFalse(VineGrowthUtils.isSheared(vine));
    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertEquals(0, offHand.damageTaken);
    assertNull(player.nextActionBar());
  }

  @Test
  void ignoresARightClickOnABlockThatIsNotThere() {
    Shears shears = new Shears();
    PlayerInteractEvent event =
        new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, shears, null, BlockFace.NORTH, EquipmentSlot.HAND);

    // Called directly, because the server catches and logs an exception from a handler.
    new VineGrowthListener().onPlayerInteract(event);

    assertEquals(Event.Result.DEFAULT, event.useItemInHand());
    assertEquals(0, shears.damageTaken);
    assertNull(player.nextActionBar());
  }

  @Test
  void shearingSwingsTheHandThatHoldsTheShears() {
    shear(vine, new Shears());

    assertEquals(List.of(EquipmentSlot.HAND), player.swings);
  }

  @Test
  void shearingWithTheOffHandSwingsTheOffHand() {
    click(Action.RIGHT_CLICK_BLOCK, vine, new Shears(), EquipmentSlot.OFF_HAND);

    assertEquals(List.of(EquipmentSlot.OFF_HAND), player.swings);
  }

  @Test
  void ignoredClicksSwingNothing() {
    click(Action.LEFT_CLICK_BLOCK, vine, new Shears(), EquipmentSlot.HAND);

    assertTrue(player.swings.isEmpty());
  }

  @Test
  void shearsInTheMainHandToggleTheVine() {
    shear(vine, new Shears());

    assertTrue(VineGrowthUtils.isSheared(vine));
  }
}
