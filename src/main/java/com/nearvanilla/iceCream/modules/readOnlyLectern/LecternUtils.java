package com.nearvanilla.iceCream.modules.readOnlyLectern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

public class LecternUtils {

  public static boolean isReadOnly(Block block) {
    if (block.getType() != Material.LECTERN) {
      return false;
    }
    Lectern lectern = (Lectern) block.getState();
    return Boolean.TRUE.equals(
        lectern
            .getPersistentDataContainer()
            .get(ReadOnlyLecternModule.READ_ONLY_KEY, PersistentDataType.BOOLEAN));
  }

  public static void setReadOnly(Block block, boolean readOnly) {
    Lectern lectern = (Lectern) block.getState();
    lectern
        .getPersistentDataContainer()
        .set(ReadOnlyLecternModule.READ_ONLY_KEY, PersistentDataType.BOOLEAN, readOnly);
    lectern.update();
  }

  public static boolean hasPlayerAuthoredBook(Block block) {
    if (block.getType() != Material.LECTERN) {
      return false;
    }
    Lectern lectern = (Lectern) block.getState();
    ItemStack book = lectern.getInventory().getItem(0);
    if (book == null || book.getType() != Material.WRITTEN_BOOK) {
      return false;
    }
    BookMeta meta = (BookMeta) book.getItemMeta();
    return meta != null && meta.hasAuthor();
  }

  public static boolean isEligibleForToggle(Block block) {
    if (block.getType() != Material.LECTERN) {
      return false;
    }
    return hasPlayerAuthoredBook(block);
  }

  public static Block getTargetLectern(Player player, int maxDistance) {
    Block block = player.getTargetBlockExact(maxDistance);
    if (block == null || block.getType() != Material.LECTERN) {
      return null;
    }
    return block;
  }

  public static Component buildMessage(String miniMessage) {
    return MiniMessage.miniMessage().deserialize(miniMessage);
  }
}
