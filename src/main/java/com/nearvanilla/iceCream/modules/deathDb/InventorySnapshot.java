package com.nearvanilla.iceCream.modules.deathDb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Fixed layout snapshot of a player's inventory and its byte format.
 *
 * <p>Slot order: 0-35 storage (0-8 hotbar, 9-35 main inventory), 36-39 armor (boots, leggings,
 * chestplate, helmet), 40 offhand. Explicit Bukkit accessors are used instead of {@code
 * getContents()} so the layout never depends on server internals.
 *
 * <p>Bytes: version byte (1), int slot count (41), then per slot an int length (0 means empty)
 * followed by {@link ItemStack#serializeAsBytes()}.
 */
public final class InventorySnapshot {
  public static final int STORAGE_SLOTS = 36;
  public static final int ARMOR_SLOTS = 4;
  public static final int ARMOR_START = STORAGE_SLOTS;
  public static final int OFFHAND_SLOT = ARMOR_START + ARMOR_SLOTS;
  public static final int SIZE = OFFHAND_SLOT + 1;

  private static final int FORMAT_VERSION = 1;

  private InventorySnapshot() {}

  /** Returns true for a null item or an air stack. */
  public static boolean isEmpty(ItemStack item) {
    return item == null || item.getType().isAir();
  }

  /** Copies the inventory into a 41 slot array of cloned stacks. */
  public static ItemStack[] capture(PlayerInventory inventory) {
    ItemStack[] slots = new ItemStack[SIZE];
    copyInto(inventory.getStorageContents(), slots, 0, STORAGE_SLOTS);
    copyInto(inventory.getArmorContents(), slots, ARMOR_START, ARMOR_SLOTS);
    ItemStack offhand = inventory.getItemInOffHand();
    slots[OFFHAND_SLOT] = isEmpty(offhand) ? null : offhand.clone();
    return slots;
  }

  private static void copyInto(ItemStack[] source, ItemStack[] target, int start, int max) {
    for (int i = 0; i < Math.min(source.length, max); i++) {
      target[start + i] = isEmpty(source[i]) ? null : source[i].clone();
    }
  }

  /** Writes all 41 slots into the inventory, replacing whatever was there. */
  public static void apply(PlayerInventory inventory, ItemStack[] slots) {
    inventory.setStorageContents(Arrays.copyOfRange(slots, 0, STORAGE_SLOTS));
    inventory.setArmorContents(Arrays.copyOfRange(slots, ARMOR_START, ARMOR_START + ARMOR_SLOTS));
    inventory.setItemInOffHand(slots[OFFHAND_SLOT]);
  }

  /** Serializes a 41 slot array. Must run on the server thread. */
  public static byte[] serialize(ItemStack[] slots) {
    if (slots.length != SIZE) {
      throw new IllegalArgumentException("Expected " + SIZE + " slots but got " + slots.length);
    }
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes)) {
      out.writeByte(FORMAT_VERSION);
      out.writeInt(SIZE);
      for (ItemStack item : slots) {
        if (isEmpty(item)) {
          out.writeInt(0);
        } else {
          byte[] data = item.serializeAsBytes();
          out.writeInt(data.length);
          out.write(data);
        }
      }
      out.flush();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Reads a snapshot produced by {@link #serialize(ItemStack[])}. Any malformed or unreadable slot
   * fails the whole snapshot so callers never apply a partial inventory.
   *
   * @throws IOException if the bytes are corrupt or from an unsupported version
   */
  public static ItemStack[] deserialize(byte[] bytes) throws IOException {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
      int version = in.readUnsignedByte();
      if (version != FORMAT_VERSION) {
        throw new IOException("Unsupported inventory snapshot version " + version);
      }
      int size = in.readInt();
      if (size != SIZE) {
        throw new IOException("Expected " + SIZE + " slots but snapshot has " + size);
      }
      ItemStack[] slots = new ItemStack[SIZE];
      for (int i = 0; i < SIZE; i++) {
        int length = in.readInt();
        if (length < 0 || length > in.available()) {
          throw new IOException("Corrupt item length " + length + " in slot " + i);
        }
        if (length == 0) {
          continue;
        }
        byte[] data = new byte[length];
        in.readFully(data);
        slots[i] = ItemStack.deserializeBytes(data);
      }
      return slots;
    }
  }
}
