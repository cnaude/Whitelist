package com.cnaude.autowhitelist;

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
import org.bukkit.craftbukkit.libs.com.google.gson.Gson;
import org.bukkit.craftbukkit.libs.com.google.gson.GsonBuilder;
import org.bukkit.craftbukkit.libs.com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoWhitelist extends JavaPlugin {

    private static Config config;
    private final PlayerListener playerListener = new PlayerListener(this);
    private FileWatcher fileWatcher;
    private File dataFolder;
    private SqlConnection sqlConn;
    public CaseInsensitiveList whitelist;
    public ArrayList<User> uuidWhitelist;
    private boolean configLoaded = false;
    static final Logger log = Logger.getLogger("Minecraft");
    public static final String PLUGIN_NAME = "AutoWhitelist";
    public static final String LOG_HEADER = "[" + PLUGIN_NAME + "]";

    private final String PERM_ADMIN = "whitelist.admin";
    private final String WHITELIST_FILENAME = "whitelist.txt";
    private final String UUID_FILENAME = "whitelist.json";

    private final String ADD_USAGE = ChatColor.GOLD + "Usage: " + ChatColor.WHITE + "/whitelist add <player(s)>";
    private final String REMOVE_USAGE = ChatColor.GOLD + "Usage: " + ChatColor.WHITE + "/whitelist remove <player(s)>";
    private final String INFO_USAGE = ChatColor.GOLD + "Usage: " + ChatColor.WHITE + "/whitelist info <player>";
    private final String NO_SQL_CONNECTION = ChatColor.RED + "SQL connection is not configured.";

    File whitelistFile;
    File uuidFile;

    @Override
    public void onEnable() {
        dataFolder = getDataFolder();
        whitelist = new CaseInsensitiveList();
        uuidWhitelist = new ArrayList();
        sqlConn = null;

        whitelistFile = new File(dataFolder.getAbsolutePath(), WHITELIST_FILENAME);
        uuidFile = new File(dataFolder.getAbsolutePath(), UUID_FILENAME);

        loadWhitelistSettings();

        if (!whitelistFile.exists() && !config.uuidMode()) {
            logInfo("Creating " + WHITELIST_FILENAME);
            try {
                whitelistFile.createNewFile();
                logInfo("Done.");
            } catch (IOException ex) {
                logError(ex.getMessage());
            }
        }
        if (!uuidFile.exists() && config.uuidMode()) {
            logInfo("Creating " + UUID_FILENAME);
            try {
                uuidFile.createNewFile();
                logInfo("Done.");
            } catch (IOException ex) {
                logError(ex.getMessage());
            }
        }

        createSqlConnection();

        if (whitelistFile.exists() && config.uuidMode()) {
            logInfo("Converting " + WHITELIST_FILENAME + " to " + UUID_FILENAME);
            convertTxtToJson();
        } else if (whitelistFile.exists() && !config.uuidMode()) {
            loadWhitelist();
        } else if (uuidFile.exists() && config.uuidMode()) {
            loadUUIDWhitelist();
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
                if (args.length >= 2) {
                    for (int i = 1; i < args.length; i++) {
                        addPlayerToWhitelist(args[i], sender);
                    }
                } else {
                    sender.sendMessage(ADD_USAGE);
                }
                break;
            case "info":
                if (args.length >= 2) {
                    getUserInfo(args[1], sender);
                } else {
                    sender.sendMessage(INFO_USAGE);
                }
                break;
            case "remove":
                if (args.length >= 2) {
                    for (int i = 1; i < args.length; i++) {
                        removePlayerFromWhitelist(args[i], sender);
                    }
                } else {
                    sender.sendMessage(REMOVE_USAGE);
                }
                break;
            case "on":
                setWhitelistActive(true, this);
                sender.sendMessage(ChatColor.GREEN + "Whitelist activated!");
                break;
            case "off":
                setWhitelistActive(false, this);
                sender.sendMessage(ChatColor.RED + "Whitelist deactivated!");
                break;
            case "dblist":
                printDBUserList(sender);
                break;
            case "dbdump":
                dumpDBUserList(sender);
                break;
            case "list":
                getFormatedAllowList(sender);
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

    public boolean loadWhitelist() {
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
        } catch (IOException ex) {
            logError(ex.getMessage());
            return false;
        }
        return true;
    }

    public boolean loadUUIDWhitelist() {
        try {
            uuidWhitelist.clear();
            logDebug("Loading " + UUID_FILENAME);
            try (BufferedReader reader = new BufferedReader(new FileReader(uuidFile))) {
                Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
                uuidWhitelist = gson.fromJson(reader, new TypeToken<ArrayList<User>>() {
                }.getType());
            }
            if (uuidWhitelist == null) {
                logDebug("Null uuidWhitelist detected. Initializing empty list.");
                uuidWhitelist = new ArrayList<>();
            }
        } catch (IOException ex) {
            logError(ex.getMessage());
            return false;
        }
        return true;
    }

    public boolean createSqlConnection() {
        try {
            if (config.sqlEnabled()) {
                sqlConn = new SqlConnection(this);
            } else {
                if (sqlConn != null) {
                    sqlConn.Cleanup();
                }
                sqlConn = null;
            }
        } catch (Exception ex) {
            return false;
        }
        return true;
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
                    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
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
        if (whitelist.contains(playerName)) {
            return true;
        }

        if ((config.sqlEnabled()) && (sqlConn != null)) {
            return sqlConn.isOnWhitelist(playerName, getServer().getConsoleSender());
        }

        return false;
    }

    public boolean isOnWhitelist(Player player) {
        if (config.uuidMode()) {
            return isOnWhitelist(new User(player, null));
        } else {
            return isOnWhitelist(player.getName());
        }
    }

    public boolean isOnWhitelist(User user) {
        for (User u : uuidWhitelist) {
            if (u.uuid.equals(user.uuid)) {
                logDebug(user.name + ": " + user.uuid + " = " + u.uuid);
                return true;
            }
        }
        return false;
    }

    public void asyncUserAdd(final String playerName, final CommandSender sender) {
        getServer().getScheduler().runTaskAsynchronously(this, new Runnable() {
            @Override
            public void run() {
                User user = getPlayerUser(playerName, sender);
                if (user.uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Invalid player: " + ChatColor.WHITE + playerName);
                } else {
                    if (!isOnWhitelist(user)) {
                        uuidWhitelist.add(user);
                        saveWhitelist();
                        sender.sendMessage(ChatColor.YELLOW + "Added player: "
                                + ChatColor.WHITE + playerName + " (" + user.uuid + ")");
                    }
                }
            }
        });
    }

    public void addPlayerToWhitelist(String playerName, CommandSender sender) {
        if (config.uuidMode()) {
            asyncUserAdd(playerName, sender);
        } else {
            if (!isOnWhitelist(playerName)) {
                if ((!config.sqlQueryAdd().isEmpty()) && (sqlConn != null)) {
                    sqlConn.addPlayerToWhitelist(playerName, sender);                        
                } else {
                    whitelist.add(playerName);
                    sender.sendMessage(ChatColor.YELLOW + "Added player: " + ChatColor.WHITE + playerName);
                    saveWhitelist();
                }
            }
        }
    }

    public void printDBUserList(CommandSender sender) {
        if (sqlConn != null) {
            sqlConn.printDBUserList(sender);
        } else {
            sender.sendMessage(NO_SQL_CONNECTION);
        }    
    }

    public void dumpDBUserList(CommandSender sender) {
        if (sqlConn != null) {
            sqlConn.dumpDB(sender);
        } else {
            sender.sendMessage(NO_SQL_CONNECTION);
        }
    }

    public void removePlayerFromWhitelist(String playerName, CommandSender sender) {
        if (!config.sqlQueryRemove().isEmpty() && sqlConn != null) {
            if (isOnWhitelist(playerName)) {
                sqlConn.removePlayerFromWhitelist(playerName, sender);
            } else {
                sender.sendMessage(ChatColor.RED + "No such player in whitelist: " + ChatColor.WHITE + playerName);
            }
        } else {
            if (config.uuidMode()) {
                User userToRemove = null;
                for (User u : uuidWhitelist) {
                    if (u.name.equalsIgnoreCase(playerName)) {
                        userToRemove = u;
                        break;
                    }
                }
                if (userToRemove != null) {
                    uuidWhitelist.remove(userToRemove);
                    sender.sendMessage(ChatColor.YELLOW + "Player removed: " + ChatColor.WHITE + playerName);
                } else {
                    sender.sendMessage(ChatColor.RED + "No such player in whitelist: " + ChatColor.WHITE + playerName);
                }
            } else {
                if (whitelist.contains(playerName)) {
                    whitelist.removeString(playerName);
                    sender.sendMessage(ChatColor.YELLOW + "Player removed: " + ChatColor.WHITE + playerName);
                    saveWhitelist();
                } else {
                    sender.sendMessage(ChatColor.RED + "No such player in whitelist: " + ChatColor.WHITE + playerName);
                }
            }
        }
    }

    public void getUserInfo(String playerName, CommandSender sender) {
        if (!config.sqlQueryRemove().isEmpty() && sqlConn != null) {
            if (isOnWhitelist(playerName)) {
                sender.sendMessage(ChatColor.YELLOW + "Player " + ChatColor.WHITE + playerName
                        + ChatColor.YELLOW + " is in the whitelist.");
            }
        } else {
            if (config.uuidMode()) {
                for (User u : uuidWhitelist) {
                    if (u.name.equalsIgnoreCase(playerName)) {
                        u.getUserInfo(sender);
                        break;
                    }
                }
            } else {
                if (whitelist.contains(playerName)) {
                    sender.sendMessage(ChatColor.YELLOW + "Player " + ChatColor.WHITE + playerName
                            + ChatColor.YELLOW + " is in the whitelist.");
                }
            }
        }
    }

    public boolean reloadSettings() {
        return loadWhitelistSettings();
    }

    public void getFormatedAllowList(CommandSender sender) {
        String result = "";
        if (config.uuidMode()) {
            for (User u : uuidWhitelist) {
                if (result.length() > 0) {
                    result = result + ", ";
                }
                result = result + u.name;
            }
        } else {
            for (String player : whitelist) {
                if (result.length() > 0) {
                    result = result + ", ";
                }
                result = result + player;
            }
        }
        if (result.isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Whitelist is empty.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Players in " + fileWatcher.fileName + ": " + ChatColor.WHITE + result);
        }
    }

    public boolean isWhitelistActive() {
        return config.whitelistEnabled();
    }

    public void setWhitelistActive(boolean enabled, AutoWhitelist plugin) {
        config.setWhitelistActive(enabled, this);
        
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
        loadWhitelist();
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
                    logInfo("Converting '" + pName + "' to UUID: " + uuid);
                    uuidWhitelist.add(new User(uuid, pName, "AUTOWHITELIST_CONVERTER"));
                }
            }
        }
        if (!uuidWhitelist.isEmpty()) {
            Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
            String json = gson.toJson(uuidWhitelist);
            logDebug("JSON: " + json);
            saveWhitelist();
        } else {
            logDebug("JSON: NOPE");
        }
        whitelist.clear();
        whitelistFile.renameTo(new File(this.dataFolder, WHITELIST_FILENAME + ".old"));
    }

    public User getPlayerUser(String playerName, CommandSender sender) {
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
        return new User(uuid, playerName, sender.getName());
    }
}
