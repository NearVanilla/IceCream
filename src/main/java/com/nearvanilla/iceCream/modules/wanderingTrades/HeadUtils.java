package com.nearvanilla.iceCream.modules.wanderingTrades;

import static com.nearvanilla.iceCream.modules.wanderingTrades.WanderingTradesModule.headTradePool;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.nearvanilla.iceCream.libs.JsonLoader;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.SkullMeta;

public final class HeadUtils {
  private HeadUtils() {}

  public static void loadHeadTrades(InputStream input) throws Exception {
    List<HeadTradeData> trades = JsonLoader.load(input, new TypeReference<>() {});
    headTradePool.clear();
    for (HeadTradeData data : trades) {
      ItemStack head = createCustomHead(data.name, data.texture, data.headCount);
      MerchantRecipe recipe = new MerchantRecipe(head, data.maxUses);
      recipe.addIngredient(new ItemStack(Material.EMERALD, 1));
      Material secondary = Material.matchMaterial(data.secondaryCost);
      if (secondary != null) recipe.addIngredient(new ItemStack(secondary, 1));
      headTradePool.add(recipe);
    }
  }

  public static List<MerchantRecipe> getHeadTradePool() {
    return headTradePool;
  }

  public static ItemStack createCustomHead(String displayName, String textureBase64, int amount) {
    ItemStack head = new ItemStack(Material.PLAYER_HEAD, amount);
    SkullMeta meta = (SkullMeta) head.getItemMeta();
    TextComponent headName =
        Component.text(displayName.replace("\"", "")).decoration(TextDecoration.ITALIC, false);
    meta.customName(headName);
    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
    profile.setProperty(new ProfileProperty("textures", textureBase64));
    meta.setPlayerProfile(profile);
    head.setItemMeta(meta);
    return head;
  }
}
