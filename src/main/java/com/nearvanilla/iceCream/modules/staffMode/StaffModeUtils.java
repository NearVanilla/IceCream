package com.nearvanilla.iceCream.modules.staffMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * StaffModeUtils is a utility class for serializing and deserializing a players inventory and
 * players location. It provides methods to serialize and deserialize the inventory. It converts the
 * inventory to a byte array and saves it into the players PDC, and converts it back to an inventory
 * when needed. It also provides methods to serialize and deserialize the location, saving the world
 * name, x, y, z, yaw and pitch.
 *
 * @author Demonstrations
 * @version 1.0
 * @since 2025-08-23
 */
public class StaffModeUtils {

  /**
   * Serializes a players inventory to a byte array and saves it into the players PDC.
   *
   * @param inventory The inventory to be serialized.
   * @return byte array representing the serialized inventory.
   */
  public static byte[] serializeInventory(ItemStack[] inventory) {
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
      dataOutputStream.writeInt(inventory.length);

      for (ItemStack item : inventory) {
        if (item == null) {
          dataOutputStream.writeInt(0);
        } else {
          byte[] itemBytes = item.serializeAsBytes();
          dataOutputStream.writeInt(itemBytes.length);
          dataOutputStream.write(itemBytes);
        }
      }

      dataOutputStream.close();
      return byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize inventory", e);
    }
  }

  /**
   * Deserializes a players inventory from a byte array stored in the players PDC.
   *
   * @param player The player whose inventory is to be deserialized.
   * @param namespacedKey The NamespacedKey used to retrieve the serialized inventory from the
   *     player's PDC.
   * @return The deserialized inventory as an array of ItemStacks.
   */
  public static ItemStack[] deserializeInventory(Player player, NamespacedKey namespacedKey) {
    try {
      PersistentDataContainer persistentDataContainer = player.getPersistentDataContainer();
      byte[] playerInventoryBytes =
          persistentDataContainer.get(namespacedKey, PersistentDataType.BYTE_ARRAY);
      if (playerInventoryBytes == null) return new ItemStack[0];

      ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(playerInventoryBytes);
      DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);

      int size = dataInputStream.readInt();
      ItemStack[] inventory = new ItemStack[size];

      for (int i = 0; i < size; i++) {
        int itemLength = dataInputStream.readInt();
        if (itemLength == 0) {
          inventory[i] = null;
          continue;
        }
        byte[] itemBytes = new byte[itemLength];
        dataInputStream.readFully(itemBytes);
        inventory[i] = ItemStack.deserializeBytes(itemBytes);
      }

      return inventory;
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize inventory", e);
    }
  }

  /**
   * Serializes a players location to a byte array and saves it into the players PDC. This includes
   * the world name, x, y, z coordinates, yaw, and pitch.
   *
   * @param player The player whose location is to be serialized.
   * @return byte array representing the serialized location.
   */
  public static byte[] serializeLocation(Player player) {
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
      // Write location data (world, x, y, z, yaw, pitch)
      dataOutputStream.writeUTF(player.getWorld().getName());
      dataOutputStream.writeDouble(player.getLocation().getX());
      dataOutputStream.writeDouble(player.getLocation().getY());
      dataOutputStream.writeDouble(player.getLocation().getZ());
      dataOutputStream.writeFloat(player.getLocation().getYaw());
      dataOutputStream.writeFloat(player.getLocation().getPitch());
      dataOutputStream.close();
      return byteArrayOutputStream.toByteArray();
    } catch (IOException e) {
      throw new RuntimeException("Failed to serialize location", e);
    }
  }

  /**
   * Deserializes a players location from a byte array stored in the players PDC.
   *
   * @param data The byte array representing the serialized location.
   * @param server The server instance to retrieve the world.
   * @return The deserialized Location object.
   */
  public static Location deserializeLocation(byte[] data, Server server) {
    try {
      DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
      String world = dis.readUTF();
      double x = dis.readDouble();
      double y = dis.readDouble();
      double z = dis.readDouble();
      float yaw = dis.readFloat();
      float pitch = dis.readFloat();
      dis.close();
      return new Location(server.getWorld(world), x, y, z, yaw, pitch);
    } catch (IOException e) {
      throw new RuntimeException("Failed to deserialize location", e);
    }
  }
}
