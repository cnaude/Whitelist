package net.immortal_forces.silence.plugin.whitelist;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public class WLPlayerListener implements Listener {

    private final Whitelist m_Plugin;

    public WLPlayerListener(Whitelist instance) {
        this.m_Plugin = instance;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (this.m_Plugin.isWhitelistActive()) {
            if (this.m_Plugin.needReloadWhitelist()) {
                System.out.println("Whitelist: Executing scheduled whitelist reload.");
                this.m_Plugin.reloadSettings();
                this.m_Plugin.resetNeedReloadWhitelist();
            }

            String playerName = event.getPlayer().getName();
            System.out.print("Whitelist: Player " + playerName + " is trying to join...");
            if (this.m_Plugin.isOnWhitelist(playerName)) {
                System.out.println("allow!");
            } else {
                System.out.println("kick!");
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, this.m_Plugin.getKickMessage());
            }
        }
    }
}
