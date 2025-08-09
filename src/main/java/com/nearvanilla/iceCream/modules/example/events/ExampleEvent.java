package com.nearvanilla.iceCream.modules.example.events;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ExampleEvent implements Listener {

    protected final Component joinedComponent =
            MiniMessage.miniMessage().deserialize(
                    "<rainbow>Welcome to the server! This is an example event.</rainbow>"
            );

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(joinedComponent);
    }

}
