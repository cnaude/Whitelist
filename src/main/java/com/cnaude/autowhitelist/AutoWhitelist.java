package com.cnaude.autowhitelist;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoWhitelist extends JavaPlugin {

    private static Config config;
    private final PlayerListener playerListener = new PlayerListener(this);
    private FileWatcher fileWatcher;
    private Timer timer;
    private File dataFolder;
    private boolean whitelistActive;
    private SqlConnection sqlConn;
    public ArrayList<String> whitelist;
    private boolean configLoaded = false;
    static final Logger log = Logger.getLogger("Minecraft");
    public static final String PLUGIN_NAME = "AutoWhitelist";
    public static final String LOG_HEADER = "[" + PLUGIN_NAME + "]";

    @Override
    public void onEnable() {
        dataFolder = getDataFolder();
        whitelist = new ArrayList();
        whitelistActive = true;
        sqlConn = null;

        loadWhitelistSettings();

        getServer().getPluginManager().registerEvents(playerListener, this);

        File whitelistFile = new File(dataFolder.getAbsolutePath() + File.separator + "whitelist.txt");
        if (!whitelistFile.exists()) {
            logInfo("Whitelist.txt is missing, creating...");
            try {
                whitelistFile.createNewFile();
                logInfo("Done.");
            } catch (IOException ex) {
                logError("Failed. [" + ex.getMessage() + "]");
            }
        }

        fileWatcher = new FileWatcher(whitelistFile, config.fileCheckInterval(), this);
    }

    public void logInfo(String _message) {
        log.log(Level.INFO, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logError(String _message) {
        log.log(Level.WARNING, String.format("%s %s", LOG_HEADER, _message));
    }

    public void logDebug(String _message) {
        if (config.debugMode()) {
            log.log(Level.INFO, String.format("%s [DEBUG] %s", LOG_HEADER, _message));
        }
    }

    public Config getWLConfig() {
        return config;
    }

    @Override
    public void onDisable() {
        timer.cancel();
        timer.purge();
        timer = null;
        logDebug("Goodbye world!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Player player;
        String subCommand;
        String noPermission = ChatColor.RED + "You do not have permission to run this command!";
        if (sender instanceof Player) {
            player = (Player) sender;
            if (!isAdmin(player)) {
                player.sendMessage(noPermission);
                return true;
            }
        }
        if (args.length < 1) {
            return false;
        } else {
            subCommand = args[0];
        }
        if (subCommand.equalsIgnoreCase("help")) {
            return false;
        }
        if (subCommand.equalsIgnoreCase("reload")) {
            if (reloadSettings()) {
                sender.sendMessage(ChatColor.GREEN + "Settings and whitelist reloaded");
            } else {
                sender.sendMessage(ChatColor.RED + "Could not reload whitelist...");
            }
            return true;
        }
        if (subCommand.equalsIgnoreCase("add")) {
            if (args.length < 2) {
                return false;
            } else if (addPlayerToWhitelist(args[1])) {
                sender.sendMessage(ChatColor.GREEN + "Player \"" + args[1] + "\" added");
            } else {
                sender.sendMessage(ChatColor.RED + "Could not add player \"" + args[1] + "\"");
            }
            return true;
        }
        if (subCommand.equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Parameter missing: Player name");
            } else if (removePlayerFromWhitelist(args[1])) {
                sender.sendMessage(ChatColor.GREEN + "Player \"" + args[1] + "\" removed");
            } else {
                sender.sendMessage(ChatColor.RED + "Could not remove player \"" + args[1] + "\"");
            }
            return true;
        }
        if (subCommand.equalsIgnoreCase("on")) {
            setWhitelistActive(true);
            sender.sendMessage(ChatColor.GREEN + "Whitelist activated!");
            return true;
        }
        if (subCommand.equalsIgnoreCase("off")) {
            setWhitelistActive(false);
            sender.sendMessage(ChatColor.RED + "Whitelist deactivated!");
            return true;
        }
        if (subCommand.equalsIgnoreCase("dblist")) {
            printDBUserList(sender);
            return true;
        }
        if (subCommand.equalsIgnoreCase("dbdump")) {
            dumpDBUserList(sender);
            return true;
        }
        if (subCommand.equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.YELLOW + "Players in whitelist.txt: " + ChatColor.GRAY + getFormatedAllowList());
            return true;
        }
        return false;
    }

    public String colorSet(String finishedProduct) {
        return ChatColor.translateAlternateColorCodes('&', finishedProduct);
    }

    public boolean loadWhitelistSettings() {
        if (!configLoaded) {
            getConfig().options().copyDefaults(true);
            saveConfig();
            logInfo("Configuration loaded.");
            config = new Config(this);
        } else {
            reloadConfig();
            getConfig().options().copyDefaults(false);
            config = new Config(this);
            logInfo("Configuration reloaded.");
        }
        configLoaded = true;

        try {
            whitelist.clear();
            logDebug("Loading whitelist.txt");
            BufferedReader reader = new BufferedReader(new FileReader(dataFolder.getAbsolutePath() + File.separator + "whitelist.txt"));
            String line = reader.readLine();
            while (line != null) {
                logDebug("Adding " + line + " to allow list.");
                whitelist.add(line);
                line = reader.readLine();
            }
            reader.close();

            if (config.sqlEnabled()) {
                sqlConn = new SqlConnection(this);
            } else {
                if (sqlConn != null) {
                    sqlConn.Cleanup();
                }
                sqlConn = null;
            }
        } catch (Exception ex) {
            logDebug("Failed: " + ex.getMessage());
            return false;
        }

        return true;
    }

    public boolean saveWhitelist() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(dataFolder.getAbsolutePath() + File.separator + "whitelist.txt"));
            for (String player : whitelist) {
                writer.write(player);
                writer.newLine();
            }
            writer.close();
        } catch (IOException ex) {
            logDebug(ex.getMessage());
            return false;
        }
        return true;
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission("whitelist.admin");
    }

    public boolean isOnWhitelist(String playerName) {
        for (String player : whitelist) {
            if (player.equalsIgnoreCase(playerName)) {
                return true;
            }
        }

        if ((config.sqlEnabled()) && (sqlConn != null)) {
            return sqlConn.isOnWhitelist(playerName, true);
        }

        return false;
    }

    public boolean addPlayerToWhitelist(String playerName) {
        if ((!config.sqlQueryAdd().isEmpty()) && (sqlConn != null)) {
            if (!isOnWhitelist(playerName)) {
                return sqlConn.addPlayerToWhitelist(playerName, true);
            }

        } else if (!isOnWhitelist(playerName)) {
            whitelist.add(playerName);
            return saveWhitelist();
        }

        return false;
    }

    public boolean printDBUserList(CommandSender sender) {
        if (sqlConn != null) {
            return sqlConn.printDBUserList(sender);
        }
        return false;
    }
    
    public boolean dumpDBUserList(CommandSender sender) {
        if (sqlConn != null) {
            return sqlConn.dumpDB(sender);
        }
        return false;
    }

    public boolean removePlayerFromWhitelist(String playerName) {
        if (!config.sqlQueryRemove().isEmpty() && sqlConn != null) {
            if (isOnWhitelist(playerName)) {
                return sqlConn.removePlayerFromWhitelist(playerName, true);
            }
        } else {
            for (int i = 0; i < whitelist.size(); i++) {
                if (playerName.compareToIgnoreCase((String) whitelist.get(i)) != 0) {
                    continue;
                }
                whitelist.remove(i);
                return saveWhitelist();
            }
        }

        return false;
    }

    public boolean reloadSettings() {
        return loadWhitelistSettings();
    }

    public String getFormatedAllowList() {
        String result = "";
        for (String player : whitelist) {
            if (result.length() > 0) {
                result = result + ", ";
            }
            result = result + player;
        }
        return result;
    }

    public boolean isWhitelistActive() {
        return whitelistActive;
    }

    public void setWhitelistActive(boolean isWhitelistActive) {
        whitelistActive = isWhitelistActive;
    }

    public boolean needReloadWhitelist() {
        if (fileWatcher != null) {
            return fileWatcher.wasFileModified();
        }
        return false;
    }

    public void resetNeedReloadWhitelist() {
        if (fileWatcher != null) {
            fileWatcher.resetFileModifiedState();
        }
    }
}