package com.nearvanilla.iceCream.modules.vineGrowth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.testutil.IceCreamFixture;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

class VineGrowthUtilsTest {
  private ServerMock server;
  private WorldMock world;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    IceCreamFixture.install(server, "");
    world = server.addSimpleWorld("world");
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

  private static NamespacedKey key(String name) {
    return new NamespacedKey("icecream", name);
  }

  private static PersistentDataContainer data(Block block) {
    return block.getChunk().getPersistentDataContainer();
  }

  private static Set<NamespacedKey> markers(Chunk chunk) {
    return chunk.getPersistentDataContainer().getKeys();
  }

  @Test
  void aNewVineIsNotSheared() {
    assertFalse(VineGrowthUtils.isSheared(vine(0, 64, 0)));
  }

  @Test
  void aVineMarkedAsShearedIsSheared() {
    Block vine = vine(0, 64, 0);

    VineGrowthUtils.setSheared(vine, true);

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void aVineCanBeMarkedAsShearedTwice() {
    Block vine = vine(0, 64, 0);

    VineGrowthUtils.setSheared(vine, true);
    VineGrowthUtils.setSheared(vine, true);

    assertTrue(VineGrowthUtils.isSheared(vine));
    assertEquals(1, markers(vine.getChunk()).size());
  }

  @Test
  void clearingTheMarkerLetsTheVineGrowAgain() {
    Block vine = vine(0, 64, 0);
    VineGrowthUtils.setSheared(vine, true);

    VineGrowthUtils.setSheared(vine, false);

    assertFalse(VineGrowthUtils.isSheared(vine));
    assertTrue(markers(vine.getChunk()).isEmpty());
  }

  @Test
  void clearingAMarkerThatIsNotThereChangesNothing() {
    Block vine = vine(0, 64, 0);

    VineGrowthUtils.setSheared(vine, false);

    assertFalse(VineGrowthUtils.isSheared(vine));
    assertTrue(markers(vine.getChunk()).isEmpty());
  }

  @Test
  void aBlockThatIsNotAVineIsNeverSheared() {
    Block stone = world.getBlockAt(0, 64, 0);
    stone.setType(Material.STONE);
    VineGrowthUtils.setSheared(stone, true);

    assertFalse(VineGrowthUtils.isSheared(stone));
  }

  @Test
  void airIsNeverSheared() {
    Block air = world.getBlockAt(0, 64, 0);
    VineGrowthUtils.setSheared(air, true);

    assertFalse(VineGrowthUtils.isSheared(air));
  }

  @Test
  void aMarkerLeftAtAPositionAppliesToAVineThatAppearsThereUntilItIsCleared() {
    Block block = world.getBlockAt(0, 64, 0);
    VineGrowthUtils.setSheared(block, true);
    block.setType(Material.VINE);

    // The marker is still on the chunk, which is why placing a vine clears it first.
    assertTrue(VineGrowthUtils.isSheared(block));
    VineGrowthUtils.clearStaleMarker(block);
    assertFalse(VineGrowthUtils.isSheared(block));
  }

  @Test
  void shearingOneVineLeavesItsNeighboursAlone() {
    Block vine = vine(0, 64, 0);
    Block east = vine(1, 64, 0);
    Block above = vine(0, 65, 0);
    Block south = vine(0, 64, 1);

    VineGrowthUtils.setSheared(vine, true);

    assertTrue(VineGrowthUtils.isSheared(vine));
    assertFalse(VineGrowthUtils.isSheared(east));
    assertFalse(VineGrowthUtils.isSheared(above));
    assertFalse(VineGrowthUtils.isSheared(south));
  }

  @Test
  void vinesAtTheSamePlaceInDifferentChunksAreIndependent() {
    Block here = vine(0, 64, 0);
    Block east = vine(16, 64, 0);
    Block west = vine(-16, 64, 0);
    Block north = vine(0, 64, -16);

    VineGrowthUtils.setSheared(here, true);

    assertTrue(VineGrowthUtils.isSheared(here));
    assertFalse(VineGrowthUtils.isSheared(east));
    assertFalse(VineGrowthUtils.isSheared(west));
    assertFalse(VineGrowthUtils.isSheared(north));
  }

  @Test
  void aMarkerIsKeptOnTheChunkUnderTheBlocksPositionInIt() {
    Block vine = vine(19, 70, -11);

    VineGrowthUtils.setSheared(vine, true);

    assertEquals(Set.of(key("vinegrowth.sheared.3_70_5")), markers(vine.getChunk()));
    assertEquals(
        Boolean.TRUE, data(vine).get(key("vinegrowth.sheared.3_70_5"), PersistentDataType.BOOLEAN));
  }

  @Test
  void aMarkerIsKeptForNegativeCoordinates() {
    Block vine = vine(-1, 64, -1);

    VineGrowthUtils.setSheared(vine, true);

    assertEquals(Set.of(key("vinegrowth.sheared.15_64_15")), markers(vine.getChunk()));
    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  // MockBukkit's default world starts at height 0, like the worlds before 1.18.
  private WorldMock deepWorld() {
    WorldMock deep = new WorldMock(Material.DIRT, -64, 320, 4);
    deep.setName("deep");
    server.addWorld(deep);
    return deep;
  }

  @Test
  void aMarkerIsKeptBelowSeaLevel() {
    Block vine = deepWorld().getBlockAt(2, -60, 3);
    vine.setType(Material.VINE);

    VineGrowthUtils.setSheared(vine, true);

    assertEquals(Set.of(key("vinegrowth.sheared.2_-60_3")), markers(vine.getChunk()));
    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void markersUseThePluginsNamespace() {
    Block vine = vine(0, 64, 0);

    VineGrowthUtils.setSheared(vine, true);

    assertEquals(
        IceCream.instance.namespace(), markers(vine.getChunk()).iterator().next().namespace());
  }

  @Test
  void clearStaleMarkerRemovesTheMarker() {
    Block block = world.getBlockAt(0, 64, 0);
    VineGrowthUtils.setSheared(block, true);

    VineGrowthUtils.clearStaleMarker(block);

    assertTrue(markers(block.getChunk()).isEmpty());
  }

  @Test
  void clearStaleMarkerKeepsOtherMarkers() {
    Block block = vine(0, 64, 0);
    Block other = vine(1, 64, 0);
    VineGrowthUtils.setSheared(block, true);
    VineGrowthUtils.setSheared(other, true);

    VineGrowthUtils.clearStaleMarker(block);

    assertFalse(VineGrowthUtils.isSheared(block));
    assertTrue(VineGrowthUtils.isSheared(other));
  }

  @Test
  void clearStaleMarkerWithoutAMarkerChangesNothing() {
    Block block = vine(0, 64, 0);
    Block other = vine(1, 64, 0);
    VineGrowthUtils.setSheared(other, true);

    VineGrowthUtils.clearStaleMarker(block);

    assertEquals(Set.of(key("vinegrowth.sheared.1_64_0")), markers(block.getChunk()));
  }

  @Test
  void pruneChunkRemovesMarkersWhoseBlockIsNoLongerAVine() {
    Block gone = vine(0, 64, 0);
    VineGrowthUtils.setSheared(gone, true);
    gone.setType(Material.AIR);

    VineGrowthUtils.pruneChunk(gone.getChunk());

    assertTrue(markers(gone.getChunk()).isEmpty());
  }

  @Test
  void pruneChunkRemovesMarkersWhoseBlockBecameSomethingElse() {
    Block replaced = vine(0, 64, 0);
    VineGrowthUtils.setSheared(replaced, true);
    replaced.setType(Material.STONE);

    VineGrowthUtils.pruneChunk(replaced.getChunk());

    assertTrue(markers(replaced.getChunk()).isEmpty());
  }

  @Test
  void pruneChunkKeepsMarkersOfVinesThatAreStillThere() {
    Block vine = vine(5, 70, 9);
    VineGrowthUtils.setSheared(vine, true);

    VineGrowthUtils.pruneChunk(vine.getChunk());

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void pruneChunkKeepsMarkersOfVinesInNegativeChunks() {
    Block vine = vine(-1, 64, -1);
    VineGrowthUtils.setSheared(vine, true);

    VineGrowthUtils.pruneChunk(vine.getChunk());

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void pruneChunkKeepsMarkersBelowSeaLevel() {
    Block vine = deepWorld().getBlockAt(2, -60, 3);
    vine.setType(Material.VINE);
    VineGrowthUtils.setSheared(vine, true);

    VineGrowthUtils.pruneChunk(vine.getChunk());

    assertTrue(VineGrowthUtils.isSheared(vine));
  }

  @Test
  void pruneChunkSortsGoneMarkersFromKeptOnes() {
    Block kept = vine(1, 64, 1);
    Block gone = vine(2, 64, 2);
    VineGrowthUtils.setSheared(kept, true);
    VineGrowthUtils.setSheared(gone, true);
    gone.setType(Material.AIR);

    VineGrowthUtils.pruneChunk(kept.getChunk());

    assertEquals(Set.of(key("vinegrowth.sheared.1_64_1")), markers(kept.getChunk()));
  }

  @Test
  void pruneChunkLeavesOtherChunksAlone() {
    Block here = vine(0, 64, 0);
    Block there = vine(16, 64, 0);
    VineGrowthUtils.setSheared(there, true);
    there.setType(Material.AIR);

    VineGrowthUtils.pruneChunk(here.getChunk());

    assertEquals(1, markers(there.getChunk()).size());
  }

  @Test
  void pruneChunkOfAChunkWithNoDataDoesNothing() {
    Block block = world.getBlockAt(0, 64, 0);

    VineGrowthUtils.pruneChunk(block.getChunk());

    assertTrue(markers(block.getChunk()).isEmpty());
  }

  @Test
  void pruneChunkLeavesDataThatBelongsToSomethingElse() {
    Block block = world.getBlockAt(0, 64, 0);
    data(block).set(key("something.else"), PersistentDataType.STRING, "kept");
    data(block).set(new NamespacedKey("other", "thing"), PersistentDataType.BOOLEAN, true);

    VineGrowthUtils.pruneChunk(block.getChunk());

    assertEquals(
        Set.of(key("something.else"), new NamespacedKey("other", "thing")),
        markers(block.getChunk()));
  }

  @Test
  void pruneChunkLeavesKeysItDoesNotUnderstand() {
    Block block = world.getBlockAt(0, 64, 0);
    for (String name :
        new String[] {
          "vinegrowth.sheared.1_2", "vinegrowth.sheared.1_2_3_4", "vinegrowth.sheared.a_b_c"
        }) {
      data(block).set(key(name), PersistentDataType.BOOLEAN, true);
    }

    VineGrowthUtils.pruneChunk(block.getChunk());

    assertEquals(3, markers(block.getChunk()).size());
  }
}
