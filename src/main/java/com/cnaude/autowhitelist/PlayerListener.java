package com.cnaude.autowhitelist;

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
            if (plugin.needReloadWhitelist()) {
                plugin.logInfo("Executing scheduled whitelist reload.");
                plugin.reloadSettings();
                plugin.resetNeedReloadWhitelist();
            }

            String playerName = event.getPlayer().getName();            
            if (plugin.isOnWhitelist(event.getPlayer())) {
                plugin.logInfo("Allowing player: " + playerName);
            } else {
                plugin.logInfo("Kicking player: " + playerName);                
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, 
                        plugin.getWLConfig().kickMessage().replace("%NAME%", playerName));                
            }
        }
    }
}