package com.nearvanilla.iceCream.modules.silenceMobs.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearvanilla.iceCream.modules.silenceMobs.SilenceMobsConfig;
import com.nearvanilla.iceCream.modules.silenceMobs.SilenceMobsModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SilenceMobsListenerTest {
  private static final Component SILENCED = Component.text("silenced");

  private final List<Runnable> nextTick = new ArrayList<>();
  private ServerMock server;
  private PlayerMock player;
  private Entity mob;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    SilenceMobsModule.registerPermission();
    player = server.addPlayer();
    mob = mock(Entity.class);
    when(mob.isValid()).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    MockBukkit.unmock();
  }

  private SilenceMobsListener listener(Component silencedName) {
    return new SilenceMobsListener(
        new SilenceMobsConfig(Set.of("silence me", "silence_me"), silencedName), nextTick::add);
  }

  private static ItemStack named(Material material, Component name) {
    ItemStack item = new ItemStack(material);
    item.editMeta(meta -> meta.customName(name));
    return item;
  }

  private static ItemStack nameTag(String name) {
    return named(Material.NAME_TAG, Component.text(name));
  }

  /** Uses the item on the mob, then lets vanilla name the mob with {@code appliedName}. */
  private void use(
      SilenceMobsListener listener, ItemStack item, EquipmentSlot hand, Component appliedName) {
    if (hand == EquipmentSlot.OFF_HAND) {
      player.getInventory().setItemInOffHand(item);
    } else {
      player.getInventory().setItemInMainHand(item);
    }
    listener.onInteractEntity(new PlayerInteractEntityEvent(player, mob, hand));
    when(mob.customName()).thenReturn(appliedName);
    nextTick.forEach(Runnable::run);
  }

  private void use(ItemStack item, Component appliedName) {
    use(listener(SILENCED), item, EquipmentSlot.HAND, appliedName);
  }

  private void assertSilencedAndRenamed() {
    verify(mob).setSilent(true);
    verify(mob).customName(SILENCED);
  }

  private void assertUntouched() {
    verify(mob, never()).setSilent(true);
    verify(mob, never()).customName(any());
  }

  @Test
  void triggerNameTagSilencesAndRenamesMob() {
    use(nameTag("silence me"), Component.text("silence me"));

    assertSilencedAndRenamed();
  }

  @Test
  void everyTriggerNameWorks() {
    use(nameTag("silence_me"), Component.text("silence_me"));

    assertSilencedAndRenamed();
  }

  @Test
  void triggerNameIgnoresCaseAndFormatting() {
    Component name =
        Component.text("Silence ", NamedTextColor.RED)
            .append(Component.text("ME").decorate(TextDecoration.BOLD));

    use(named(Material.NAME_TAG, name), name);

    assertSilencedAndRenamed();
  }

  @Test
  void offHandNameTagWorks() {
    use(
        listener(SILENCED),
        nameTag("silence me"),
        EquipmentSlot.OFF_HAND,
        Component.text("silence me"));

    assertSilencedAndRenamed();
  }

  @Test
  void emptySilencedNameKeepsNameTagName() {
    use(listener(null), nameTag("silence me"), EquipmentSlot.HAND, Component.text("silence me"));

    verify(mob).setSilent(true);
    verify(mob, never()).customName(any());
  }

  @Test
  void waitsUntilNextTick() {
    player.getInventory().setItemInMainHand(nameTag("silence me"));

    listener(SILENCED)
        .onInteractEntity(new PlayerInteractEntityEvent(player, mob, EquipmentSlot.HAND));

    assertUntouched();
    assertEquals(1, nextTick.size());
  }

  @Test
  void otherNamesAreIgnored() {
    use(nameTag("Bob"), Component.text("Bob"));

    assertUntouched();
    assertTrue(nextTick.isEmpty());
  }

  @Test
  void unnamedNameTagIsIgnored() {
    use(new ItemStack(Material.NAME_TAG), null);

    assertUntouched();
    assertTrue(nextTick.isEmpty());
  }

  @Test
  void otherItemsAreIgnored() {
    use(named(Material.PAPER, Component.text("silence me")), Component.text("silence me"));

    assertUntouched();
    assertTrue(nextTick.isEmpty());
  }

  @Test
  void entityTheNameTagDidNotNameIsIgnored() {
    // Such as players, boats and item frames, which name tags do not name.
    use(nameTag("silence me"), null);

    assertUntouched();
  }

  @Test
  void entityGoneByNextTickIsIgnored() {
    when(mob.isValid()).thenReturn(false);

    use(nameTag("silence me"), Component.text("silence me"));

    assertUntouched();
  }

  @Test
  void playersWithoutPermissionAreIgnored() {
    player.addAttachment(MockBukkit.createMockPlugin(), SilenceMobsModule.PERMISSION, false);

    use(nameTag("silence me"), Component.text("silence me"));

    assertUntouched();
    assertTrue(nextTick.isEmpty());
  }

  @Test
  void permissionIsGrantedToEveryoneByDefault() {
    assertTrue(!player.isOp() && player.hasPermission(SilenceMobsModule.PERMISSION));
  }
}
