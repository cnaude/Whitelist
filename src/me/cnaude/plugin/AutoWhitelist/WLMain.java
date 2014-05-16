package me.cnaude.plugin.AutoWhitelist;

import java.io.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class WLMain extends JavaPlugin {

    private static WLConfig config;
    private final WLPlayerListener playerListener = new WLPlayerListener(this);
    private WLFileWatcher fileWatcher;
    private Timer timer;
    private File dataFolder;
    private boolean whitelistActive;
    private WLSQLConnection sqlConn;
    public ArrayList<String> whitelist;
    private boolean configLoaded = false;
    static final Logger log = Logger.getLogger("Minecraft");
    public static final String PLUGIN_NAME = "AutoWhitelist";
    public static final String LOG_HEADER = "[" + PLUGIN_NAME + "]";

    @Override
    public void onEnable() {
        this.dataFolder = getDataFolder();
        this.whitelist = new ArrayList();
        this.whitelistActive = true;
        this.sqlConn = null;

        loadWhitelistSettings();

        getServer().getPluginManager().registerEvents(playerListener, this);

        File whitelistFile = new File(this.dataFolder.getAbsolutePath() + File.separator + "whitelist.txt");
        if (!whitelistFile.exists()) {
            logInfo("Whitelist.txt is missing, creating...");
            try {
                whitelistFile.createNewFile();
                logInfo("Done.");
            } catch (IOException ex) {
                logError("Failed. [" + ex.getMessage() + "]");
            }
        }

        this.fileWatcher = new WLFileWatcher(whitelistFile, this);
        this.timer = new Timer(true);
        this.timer.schedule(this.fileWatcher, 0L, config.fileCheckInterval());
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

    public WLConfig getWLConfig() {
        return config;
    }

    @Override
    public void onDisable() {
        this.timer.cancel();
        this.timer.purge();
        this.timer = null;
        logDebug("Goodbye world!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Player player;
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
        }
        if (args[0].compareToIgnoreCase("help") == 0) {
            return false;
        }
        if (args[0].compareToIgnoreCase("reload") == 0) {
            if (reloadSettings()) {
                sender.sendMessage(ChatColor.GREEN + "Settings and whitelist reloaded");
            } else {
                sender.sendMessage(ChatColor.RED + "Could not reload whitelist...");
            }
            return true;
        }
        if (args[0].compareToIgnoreCase("add") == 0) {
            if (args.length < 2) {
                return false;
            } else if (addPlayerToWhitelist(args[1])) {
                sender.sendMessage(ChatColor.GREEN + "Player \"" + args[1] + "\" added");
            } else {
                sender.sendMessage(ChatColor.RED + "Could not add player \"" + args[1] + "\"");
            }
            return true;
        }
        if (args[0].compareToIgnoreCase("remove") == 0) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Parameter missing: Player name");
            } else if (removePlayerFromWhitelist(args[1])) {
                sender.sendMessage(ChatColor.GREEN + "Player \"" + args[1] + "\" removed");
            } else {
                sender.sendMessage(ChatColor.RED + "Could not remove player \"" + args[1] + "\"");
            }
            return true;
        }
        if (args[0].compareToIgnoreCase("on") == 0) {
            setWhitelistActive(true);
            sender.sendMessage(ChatColor.GREEN + "Whitelist activated!");
            return true;
        }
        if (args[0].compareToIgnoreCase("off") == 0) {
            setWhitelistActive(false);
            sender.sendMessage(ChatColor.RED + "Whitelist deactivated!");
            return true;
        }
        if (args[0].compareToIgnoreCase("dblist") == 0) {
            printDBUserList(sender);
            return true;
        }
        if (args[0].compareToIgnoreCase("dbdump") == 0) {
            dumpDBUserList(sender);
            return true;
        }
        if (args[0].compareToIgnoreCase("list") == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Players in whitelist.txt: " + ChatColor.GRAY + getFormatedAllowList());
            return true;
        }
        return false;
    }

    public String colorSet(String finishedProduct) {
        return ChatColor.translateAlternateColorCodes('&', finishedProduct);
    }

    public boolean loadWhitelistSettings() {
        logInfo("Trying to load whitelist and configuration...");

        if (!this.configLoaded) {
            getConfig().options().copyDefaults(true);
            saveConfig();
            logInfo("Configuration loaded.");
            config = new WLConfig(this);
        } else {
            reloadConfig();
            getConfig().options().copyDefaults(false);
            config = new WLConfig(this);
            logInfo("Configuration reloaded.");
        }
        configLoaded = true;

        try {
            this.whitelist.clear();
            logDebug("Reading whitelist.txt");
            BufferedReader reader = new BufferedReader(new FileReader(this.dataFolder.getAbsolutePath() + File.separator + "whitelist.txt"));
            String line = reader.readLine();
            while (line != null) {
                logDebug("Adding " + line + " to allow list.");
                this.whitelist.add(line);
                line = reader.readLine();
            }
            reader.close();

            if (config.sqlEnabled()) {
                this.sqlConn = new WLSQLConnection(this);
            } else {
                if (this.sqlConn != null) {
                    this.sqlConn.Cleanup();
                }
                this.sqlConn = null;
            }
        } catch (Exception ex) {
            logDebug("Failed: " + ex.getMessage());
            return false;
        }

        return true;
    }

    public boolean saveWhitelist() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(this.dataFolder.getAbsolutePath() + File.separator + "whitelist.txt"));
            for (String player : this.whitelist) {
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
        for (String player : this.whitelist) {
            if (player.compareToIgnoreCase(playerName) == 0) {
                return true;
            }
        }

        if ((config.sqlEnabled()) && (this.sqlConn != null)) {
            return this.sqlConn.isOnWhitelist(playerName, true);
        }

        return false;
    }

    public boolean addPlayerToWhitelist(String playerName) {
        if ((!config.sqlQueryAdd().isEmpty()) && (this.sqlConn != null)) {
            if (!isOnWhitelist(playerName)) {
                return this.sqlConn.addPlayerToWhitelist(playerName, true);
            }

        } else if (!isOnWhitelist(playerName)) {
            this.whitelist.add(playerName);
            return saveWhitelist();
        }

        return false;
    }

    public boolean printDBUserList(CommandSender sender) {
        if (this.sqlConn != null) {
            return this.sqlConn.printDBUserList(sender);
        }
        return false;
    }
    
    public boolean dumpDBUserList(CommandSender sender) {
        if (this.sqlConn != null) {
            return this.sqlConn.dumpDB(sender);
        }
        return false;
    }

    public boolean removePlayerFromWhitelist(String playerName) {
        if (!config.sqlQueryRemove().isEmpty() && this.sqlConn != null) {
            if (isOnWhitelist(playerName)) {
                return this.sqlConn.removePlayerFromWhitelist(playerName, true);
            }
        } else {
            for (int i = 0; i < this.whitelist.size(); i++) {
                if (playerName.compareToIgnoreCase((String) this.whitelist.get(i)) != 0) {
                    continue;
                }
                this.whitelist.remove(i);
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
        for (String player : this.whitelist) {
            if (result.length() > 0) {
                result = result + ", ";
            }
            result = result + player;
        }
        return result;
    }

    public boolean isWhitelistActive() {
        return this.whitelistActive;
    }

    public void setWhitelistActive(boolean isWhitelistActive) {
        this.whitelistActive = isWhitelistActive;
    }

    public boolean needReloadWhitelist() {
        if (this.fileWatcher != null) {
            return this.fileWatcher.wasFileModified();
        }
        return false;
    }

    public void resetNeedReloadWhitelist() {
        if (this.fileWatcher != null) {
            this.fileWatcher.resetFileModifiedState();
        }
    }
}