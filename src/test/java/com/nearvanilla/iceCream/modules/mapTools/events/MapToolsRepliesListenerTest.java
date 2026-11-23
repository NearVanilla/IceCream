package com.nearvanilla.iceCream.modules.mapTools.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nearvanilla.iceCream.IceCream;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.AreaGeometry;
import com.nearvanilla.iceCream.modules.mapTools.MapAreasStore.MapArea;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsModule;
import com.nearvanilla.iceCream.modules.mapTools.MapToolsUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** What the mod is told over its channel, and who is told. */
class MapToolsRepliesListenerTest {

  /** A player whose client listens on the map tools channel, recording what it is sent. */
  private static class ModPlayer extends PlayerMock {
    final List<String> received = new ArrayList<>();

    ModPlayer(ServerMock server, String name) {
      super(server, name, UUID.randomUUID());
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
      return Set.of(MapToolsChannel.CHANNEL);
    }

    @Override
    public void sendPluginMessage(
        @NotNull Plugin source, @NotNull String channel, byte @NotNull [] message) {
      if (channel.equals(MapToolsChannel.CHANNEL)) {
        received.add(new String(message, StandardCharsets.UTF_8));
      }
    }

    /** The messages received since the last call. */
    List<String> take() {
      List<String> taken = List.copyOf(received);
      received.clear();
      return taken;
    }
  }

  @TempDir Path folder;
  private ServerMock server;
  private Plugin plugin;
  private ModPlayer staff;
  private ModPlayer player;
  private PlayerMock vanillaStaff;
  private MapAreasStore previousStore;
  private FileConfiguration previousConfig;
  private Logger previousLogger;

  @BeforeEach
  void setUp() {
    server = MockBukkit.mock();
    server.addSimpleWorld("world");
    plugin = MockBukkit.createMockPlugin();
    staff = new ModPlayer(server, "Staff");
    server.addPlayer(staff);
    staff.addAttachment(plugin, MapToolsUtils.PERMISSION, true);
    player = new ModPlayer(server, "Player");
    server.addPlayer(player);
    vanillaStaff = server.addPlayer("VanillaStaff");
    vanillaStaff.addAttachment(plugin, MapToolsUtils.PERMISSION, true);
    server.getPluginManager().registerEvents(new MapToolsReplies(), plugin);

    previousStore = MapToolsModule.store;
    previousConfig = IceCream.config;
    previousLogger = IceCream.logger;
    MapToolsModule.store = new MapAreasStore(folder.resolve("map-areas.json").toFile());
    IceCream.config = new YamlConfiguration();
    IceCream.logger = Logger.getLogger("MapToolsRepliesListenerTest");
  }

  @AfterEach
  void tearDown() {
    MapToolsChannel.forget(staff.getUniqueId());
    MockBukkit.unmock();
    MapToolsModule.store = previousStore;
    IceCream.config = previousConfig;
    IceCream.logger = previousLogger;
  }

  private void create(String id) {
    MapToolsUtils.createArea(
        staff,
        id,
        new AreaGeometry(
            MapToolsUtils.overworld(),
            MapToolsUtils.rectangle(0, 0, 9, 9),
            MapArea.RECT,
            null,
            null),
        "gold",
        "Spawn",
        List.of(),
        null,
        null,
        null,
        false);
  }

  @Test
  void helloWhenTheModStartsListening() {
    server
        .getPluginManager()
        .callEvent(new PlayerRegisterChannelEvent(player, MapToolsChannel.CHANNEL));

    assertEquals(List.of("{\"v\":1,\"type\":\"hello\"}"), player.take());
  }

  @Test
  void helloForOtherChannelsIsNotSent() {
    server.getPluginManager().callEvent(new PlayerRegisterChannelEvent(player, "other:channel"));
    assertTrue(player.take().isEmpty());
  }

  @Test
  void helloAllReachesEveryModUser() {
    MapToolsReplies.helloAll();

    assertEquals(1, staff.take().size());
    assertEquals(1, player.take().size());
  }

  @Test
  void changesGoToStaffWithTheModOnly() {
    create("spawn");

    assertEquals(
        List.of(
            "{\"v\":1,\"type\":\"changed\",\"change\":\"created\",\"kind\":\"claim\","
                + "\"id\":\"spawn\",\"previousId\":null}"),
        staff.take());
    assertTrue(player.take().isEmpty());
  }

  @Test
  void renamesCarryTheOldId() {
    create("spawn");
    staff.take();

    MapToolsUtils.renameArea(MapToolsModule.store.getArea("spawn"), "town");

    assertEquals(
        List.of(
            "{\"v\":1,\"type\":\"changed\",\"change\":\"renamed\",\"kind\":\"claim\","
                + "\"id\":\"town\",\"previousId\":\"spawn\"}"),
        staff.take());
  }

  @Test
  void errorsFromTypedCommandsAndFormsAreNotSent() {
    MapToolsUtils.error(staff, Component.text("There is no claim called x."));
    assertTrue(staff.take().isEmpty());
  }

  /** A command from the mod that fails answers the mod, here because of the rate limit. */
  @Test
  void errorsFromModCommandsAreSent() {
    for (int i = 0; i < 16; i++) MapToolsChannel.allow(staff.getUniqueId());

    new MapToolsChannel()
        .onPluginMessageReceived(
            MapToolsChannel.CHANNEL,
            staff,
            "maptools list claims".getBytes(StandardCharsets.UTF_8));

    assertEquals(
        List.of(
            "{\"v\":1,\"type\":\"error\",\"message\":"
                + "\"Too many map tools commands at once. Wait a moment and try again.\"}"),
        staff.take());
    assertTrue(!MapToolsChannel.isRunning(staff.getUniqueId()));
  }

  @Test
  void playersWithoutPermissionAreIgnoredByTheChannel() {
    for (int i = 0; i < 16; i++) MapToolsChannel.allow(player.getUniqueId());

    new MapToolsChannel()
        .onPluginMessageReceived(
            MapToolsChannel.CHANNEL,
            player,
            "maptools list claims".getBytes(StandardCharsets.UTF_8));

    assertTrue(player.take().isEmpty());
    MapToolsChannel.forget(player.getUniqueId());
  }
}
