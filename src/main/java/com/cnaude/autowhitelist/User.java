package com.cnaude.autowhitelist;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 *
 * @author cnaude
 */
public class User {
    String name;
    UUID uuid;
    
    public User(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }
    
    public User(Player player) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
    }
}
