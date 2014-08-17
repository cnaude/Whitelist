package com.cnaude.autowhitelist;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author cnaude
 */
public class User {

    String name;
    String oper;
    UUID uuid;
    Long time;

    public User(UUID uuid, String name, String oper) {
        this.uuid = uuid;
        this.name = name;
        this.oper = oper;
        time = new Date().getTime();
    }

    public User(Player player, String oper) {
        this.uuid = player.getUniqueId();
        this.name = player.getName();
        this.oper = oper;
        time = new Date().getTime();
    }

    public void getUserInfo(CommandSender sender) {
        String timestamp;
        try {
            timestamp = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(time);
        } catch (IllegalArgumentException ex) {
            timestamp = "";
        }
        sender.sendMessage(ChatColor.YELLOW + "+===[Whitelist]================================");
        sender.sendMessage(ChatColor.YELLOW + "|Name    : " + ChatColor.WHITE + name);
        sender.sendMessage(ChatColor.YELLOW + "|UUID    : " + ChatColor.WHITE + uuid);
        sender.sendMessage(ChatColor.YELLOW + "|Added by: " + ChatColor.WHITE + oper);
        sender.sendMessage(ChatColor.YELLOW + "|Date    : " + ChatColor.WHITE + timestamp);
    }
}
