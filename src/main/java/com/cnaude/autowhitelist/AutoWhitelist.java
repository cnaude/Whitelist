package com.cnaude.autowhitelist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
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
    private File dataFolder;
    private boolean whitelistActive;
    private SqlConnection sqlConn;
    public ArrayList<String> whitelist;
    public ArrayList<User> uuidWhitelist;
    private boolean configLoaded = false;
    static final Logger log = Logger.getLogger("Minecraft");
    public static final String PLUGIN_NAME = "AutoWhitelist";
    public static final String LOG_HEADER = "[" + PLUGIN_NAME + "]";

    private final String PERM_ADMIN = "whitelist.admin";
    private final String WHITELIST_FILENAME = "whitelist.txt";
    private final String UUID_FILENAME = "whitelist.json";

    File whitelistFile;
    File uuidFile;

    @Override
    public void onEnable() {
        dataFolder = getDataFolder();
        whitelist = new ArrayList();
        uuidWhitelist = new ArrayList();
        whitelistActive = true;
        sqlConn = null;

        whitelistFile = new File(dataFolder.getAbsolutePath(), WHITELIST_FILENAME);
        uuidFile = new File(dataFolder.getAbsolutePath(), UUID_FILENAME);
        if (!whitelistFile.exists() && !config.uuidMode()) {
            logInfo("Creating " + WHITELIST_FILENAME);
            try {
                whitelistFile.createNewFile();
                logInfo("Done.");
            } catch (IOException ex) {
                logError("Failed. [" + ex.getMessage() + "]");
            }
        }
        if (!uuidFile.exists() && config.uuidMode()) {
            logInfo("Creating " + UUID_FILENAME);
            try {
                uuidFile.createNewFile();
                logInfo("Done.");
            } catch (IOException ex) {
                logError("Failed. [" + ex.getMessage() + "]");
            }
        }

        loadWhitelistSettings();

        if (whitelistFile.exists() && config.uuidMode()) {
            logInfo("Converting " + WHITELIST_FILENAME + " to " + UUID_FILENAME);
            convertTxtToJson();
        }

        if (config.uuidMode()) {
            fileWatcher = new FileWatcher(uuidFile, config.fileCheckInterval(), this);
        } else {
            fileWatcher = new FileWatcher(whitelistFile, config.fileCheckInterval(), this);
        }

        getServer().getPluginManager().registerEvents(playerListener, this);
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
        fileWatcher.cancel();
        saveWhitelist();
    }

    public boolean chkPermissions(CommandSender sender, String cmd) {
        return sender.hasPermission(PERM_ADMIN) || sender.hasPermission("whitelist." + cmd);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        String subCommand;
        if (args.length < 1) {
            return false;
        } else {
            subCommand = args[0].toLowerCase();
        }
        if (!chkPermissions(sender, subCommand)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to run this command!");
            return true;
        }
        switch (subCommand) {
            case "reload":
                if (reloadSettings()) {
                    sender.sendMessage(ChatColor.GREEN + "Settings and whitelist reloaded");
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not reload whitelist...");
                }
                break;
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Parameter missing: Player name");
                } else if (addPlayerToWhitelist(args[1])) {
                    sender.sendMessage(ChatColor.GREEN + "Player \"" + args[1] + "\" added");
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not add player \"" + args[1] + "\"");
                }
                break;
            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Parameter missing: Player name");
                } else if (removePlayerFromWhitelist(args[1])) {
                    sender.sendMessage(ChatColor.GREEN + "Player \"" + args[1] + "\" removed");
                } else {
                    sender.sendMessage(ChatColor.RED + "Could not remove player \"" + args[1] + "\"");
                }
                break;
            case "on":
                setWhitelistActive(true);
                sender.sendMessage(ChatColor.GREEN + "Whitelist activated!");
                break;
            case "off":
                setWhitelistActive(false);
                sender.sendMessage(ChatColor.RED + "Whitelist deactivated!");
                break;
            case "dblist":
                printDBUserList(sender);
                break;
            case "dbdump":
                dumpDBUserList(sender);
                break;
            case "list":
                sender.sendMessage(ChatColor.YELLOW + "Players in whitelist.txt: " + ChatColor.GRAY + getFormatedAllowList());
                break;
            case "help":
            default:
                return false;
        }
        return true;
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
            logDebug("Loading " + WHITELIST_FILENAME);
            try (BufferedReader reader = new BufferedReader(new FileReader(whitelistFile))) {
                String line = reader.readLine();
                while (line != null) {
                    logDebug("Adding " + line + " to allow list.");
                    whitelist.add(line);
                    line = reader.readLine();
                }
            }

            if (config.sqlEnabled()) {
                sqlConn = new SqlConnection(this);
            } else {
                if (sqlConn != null) {
                    sqlConn.Cleanup();
                }
                sqlConn = null;
            }
        } catch (Exception ex) {
            logError("Failed: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    public boolean saveWhitelist() {
        try {
            if (config.uuidMode()) {
                int count = uuidWhitelist.size();
                String ent = " entries to ";
                if (count == 1) {
                    ent = " entry to ";
                }
                logInfo("Saving " + count + ent + uuidFile.getName());
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(uuidFile))) {
                    Gson gson = new GsonBuilder().serializeNulls().create();
                    writer.write(gson.toJson(uuidWhitelist));
                    writer.close();
                }
            } else {
                int count = whitelist.size();
                String ent = " entries to ";
                if (count == 1) {
                    ent = " entry to ";
                }
                logInfo("Saving " + count + ent + whitelistFile.getName());
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(whitelistFile))) {
                    for (String player : whitelist) {
                        writer.write(player);
                        writer.newLine();
                    }
                    writer.close();
                }
            }
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
        for (String pName : whitelist) {
            if (pName.equalsIgnoreCase(playerName)) {
                return true;
            }
        }

        if ((config.sqlEnabled()) && (sqlConn != null)) {
            return sqlConn.isOnWhitelist(playerName, true);
        }

        return false;
    }
    
    public boolean isOnWhitelist(Player player) {
        return isOnWhitelist(new User(player));
    }

    public boolean isOnWhitelist(User user) {
        for (User u : uuidWhitelist) {
            if (u.uuid.equals(user.uuid)) {
                logDebug(user.name + ": " + user.uuid + " = " + u.uuid);
                return true;
            }
        }

        //if ((config.sqlEnabled()) && (sqlConn != null)) {
        //    return sqlConn.isOnWhitelist(user, true);
        //}
        return false;
    }

    public boolean addPlayerToWhitelist(String playerName) {
        if (config.uuidMode()) {
            User user = getPlayerUser(playerName);
            if (!isOnWhitelist(user)) {
                uuidWhitelist.add(user);
                return saveWhitelist();
            }
        } else {
            if (!isOnWhitelist(playerName)) {
                if ((!config.sqlQueryAdd().isEmpty()) && (sqlConn != null)) {
                    return sqlConn.addPlayerToWhitelist(playerName, true);
                } else {
                    whitelist.add(playerName);
                    return saveWhitelist();
                }
            }
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

    public void convertTxtToJson() {
        UUIDFetcher fetcher = new UUIDFetcher(whitelist);
        Map<String, UUID> response = null;
        try {
            response = fetcher.call();
        } catch (Exception e) {
            logError("Exception while running UUIDFetcher!");
            logError(e.getMessage());
        }
        if (response != null) {
            for (String pName : response.keySet()) {
                if (whitelist.contains(pName)) {
                    UUID uuid = response.get(pName);
                    logInfo("Converting player name '" + pName + "' to UUID: " + uuid);
                    uuidWhitelist.add(new User(uuid, pName));
                }
            }
        }
        if (!uuidWhitelist.isEmpty()) {
            Gson gson = new GsonBuilder().serializeNulls().create();
            String json = gson.toJson(uuidWhitelist);
            logDebug("JSON: " + json);
            saveWhitelist();
        } else {
            logDebug("JSON: NOPE");
        }
        //whitelistFile.renameTo(new File(this.dataFolder, WHITELIST_FILENAME + ".old"));
    }

    public User getPlayerUser(String playerName) {
        ArrayList<String> tmpList = new ArrayList<>();
        tmpList.add(playerName);
        UUIDFetcher fetcher = new UUIDFetcher(tmpList);
        UUID uuid = null;
        Map<String, UUID> response = null;
        try {
            response = fetcher.call();
        } catch (Exception e) {
            logError("Exception while running UUIDFetcher!");
            logError(e.getMessage());
        }
        if (response != null) {
            for (String pName : response.keySet()) {
                if (pName.equalsIgnoreCase(playerName)) {
                    uuid = response.get(pName);
                }
            }
        }
        return new User(uuid, playerName);
    }
}
