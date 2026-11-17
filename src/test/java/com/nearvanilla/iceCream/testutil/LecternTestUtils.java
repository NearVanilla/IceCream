package com.nearvanilla.iceCream.testutil;

import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Helpers for testing code that works with lecterns, books and the block a player looks at, which
 * MockBukkit only partly supports.
 */
public final class LecternTestUtils {

  private LecternTestUtils() {}

  /**
   * Places an empty lectern in the world.
   *
   * @param world the world to place the lectern in
   * @param x the block x coordinate
   * @param y the block y coordinate
   * @param z the block z coordinate
   * @return the block the lectern was placed at
   */
  public static Block lectern(World world, int x, int y, int z) {
    Block block = world.getBlockAt(x, y, z);
    block.setType(Material.LECTERN);
    return block;
  }

  /**
   * Places a lectern holding a written book that has an author, the kind of book a player signed.
   *
   * @param world the world to place the lectern in
   * @param x the block x coordinate
   * @param y the block y coordinate
   * @param z the block z coordinate
   * @return the block the lectern was placed at
   */
  public static Block lecternWithBook(World world, int x, int y, int z) {
    Block block = lectern(world, x, y, z);
    placeBook(block, writtenBook("Steve"));
    return block;
  }

  /**
   * Puts a book on a lectern. MockBukkit's {@code Lectern.update()} replaces the live inventory
   * with the snapshot inventory, which would erase a book put only in the live one, so the book
   * goes into both.
   *
   * @param lecternBlock a block holding a lectern
   * @param book the book to place in the lectern's first slot
   */
  public static void placeBook(Block lecternBlock, ItemStack book) {
    Lectern lectern = (Lectern) lecternBlock.getState();
    lectern.getInventory().setItem(0, book);
    lectern.getSnapshotInventory().setItem(0, book);
  }

  /**
   * Creates a written book.
   *
   * @param author the author, or null for a book with no author
   * @return a written book with that author
   */
  public static ItemStack writtenBook(String author) {
    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) book.getItemMeta();
    meta.setTitle("Rules");
    meta.setAuthor(author);
    book.setItemMeta(meta);
    return book;
  }

  /**
   * Adds a player whose line of sight ends at a fixed block, since MockBukkit does not implement
   * {@code getTargetBlockExact}.
   *
   * @param server the mock server
   * @param name the player name
   * @param target the block the player looks at, or null for a player looking at nothing
   * @return the added player
   */
  public static LookingPlayer addLookingPlayer(ServerMock server, String name, Block target) {
    LookingPlayer player = new LookingPlayer(server, name, target);
    server.addPlayer(player);
    return player;
  }

  /** A player that looks at a fixed block and remembers how far it was asked to look. */
  public static final class LookingPlayer extends PlayerMock {
    private Block target;
    private int requestedDistance = -1;

    private LookingPlayer(ServerMock server, String name, Block target) {
      super(server, name, UUID.randomUUID());
      this.target = target;
    }

    /**
     * Changes the block the player looks at.
     *
     * @param target the block, or null for nothing
     */
    public void lookAt(Block target) {
      this.target = target;
    }

    /**
     * Returns the maximum distance the last {@code getTargetBlockExact} call asked for.
     *
     * @return the distance, or -1 if the player was never asked
     */
    public int requestedDistance() {
      return requestedDistance;
    }

    @Override
    public Block getTargetBlockExact(int maxDistance) {
      requestedDistance = maxDistance;
      return target;
    }
  }
}
