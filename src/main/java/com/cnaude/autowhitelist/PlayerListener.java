package com.cnaude.autowhitelist;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerListener implements Listener {

    private final AutoWhitelist plugin;

    public PlayerListener(AutoWhitelist instance) {
        plugin = instance;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (plugin.isWhitelistActive()) {
            String playerName = event.getPlayer().getName();
            Player player = event.getPlayer();
            if (plugin.isOnWhitelist(player)) {
                plugin.logInfo("Allowing player: " + playerName);
            } else {
                plugin.logInfo("Kicking player: " + playerName);
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                        plugin.getWLConfig().kickMessage().replace("%NAME%", playerName));
                if (!plugin.getWLConfig().kickMessageNotify().isEmpty()) {
                    plugin.getServer().broadcast(
                            plugin.getWLConfig().kickMessageNotify().replace("%NAME%", playerName),
                            "whitelist.notify");
                }
            }
        }
    }
}
