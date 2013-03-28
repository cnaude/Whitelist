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
    private final WLPlayerListener m_PlayerListner = new WLPlayerListener(this);
    private WLFileWatcher m_Watcher;
    private Timer m_Timer;
    private File m_Folder;
    private boolean m_bWhitelistActive;
    private WLSQLConnection m_SqlConnection;
    public ArrayList<String> m_SettingsWhitelistAllow;
    private boolean configLoaded = false;
    static final Logger log = Logger.getLogger("Minecraft");
    public static final String PLUGIN_NAME = "AutoWhitelist";
    public static final String LOG_HEADER = "[" + PLUGIN_NAME + "]";

    @Override
    public void onEnable() {
        this.m_Folder = getDataFolder();
        this.m_SettingsWhitelistAllow = new ArrayList();
        this.m_bWhitelistActive = true;
        this.m_SqlConnection = null;

        loadWhitelistSettings();

        getServer().getPluginManager().registerEvents(m_PlayerListner, this);

        File fWhitelist = new File(this.m_Folder.getAbsolutePath() + File.separator + "whitelist.txt");
        if (!fWhitelist.exists()) {
            logInfo("Whitelist.txt is missing, creating...");
            try {
                fWhitelist.createNewFile();
                logInfo("Done.");
            } catch (IOException ex) {
                logError("Failed. [" + ex.getMessage() + "]");
            }
        }

        this.m_Watcher = new WLFileWatcher(fWhitelist, this);
        this.m_Timer = new Timer(true);
        this.m_Timer.schedule(this.m_Watcher, 0L, config.fileCheckInterval());

        PluginDescriptionFile pdfFile = getDescription();
        logDebug(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!");
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
        this.m_Timer.cancel();
        this.m_Timer.purge();
        this.m_Timer = null;
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
            this.m_SettingsWhitelistAllow.clear();
            logDebug("Reading whitelist.txt");
            BufferedReader reader = new BufferedReader(new FileReader(this.m_Folder.getAbsolutePath() + File.separator + "whitelist.txt"));
            String line = reader.readLine();
            while (line != null) {
                logDebug("Adding " + line + " to allow list.");
                this.m_SettingsWhitelistAllow.add(line);
                line = reader.readLine();
            }
            reader.close();

            if (config.sqlEnabled()) {
                this.m_SqlConnection = new WLSQLConnection(this);
            } else {
                if (this.m_SqlConnection != null) {
                    this.m_SqlConnection.Cleanup();
                }
                this.m_SqlConnection = null;
            }
        } catch (Exception ex) {
            logDebug("Failed: " + ex.getMessage());
            return false;
        }

        return true;
    }

    public boolean saveWhitelist() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(this.m_Folder.getAbsolutePath() + File.separator + "whitelist.txt"));
            for (String player : this.m_SettingsWhitelistAllow) {
                writer.write(player);
                writer.newLine();
            }
            writer.close();
        } catch (Exception ex) {
            logDebug(ex.getMessage());
            return false;
        }
        return true;
    }

    public boolean isAdmin(Player player) {
        return player.hasPermission("whitelist.admin");
    }

    public boolean isOnWhitelist(String playerName) {
        for (String player : this.m_SettingsWhitelistAllow) {
            if (player.compareToIgnoreCase(playerName) == 0) {
                return true;
            }
        }

        if ((config.sqlEnabled()) && (this.m_SqlConnection != null)) {
            return this.m_SqlConnection.isOnWhitelist(playerName, true);
        }

        return false;
    }

    public boolean addPlayerToWhitelist(String playerName) {
        if ((!config.sqlQueryAdd().isEmpty()) && (this.m_SqlConnection != null)) {
            if (!isOnWhitelist(playerName)) {
                return this.m_SqlConnection.addPlayerToWhitelist(playerName, true);
            }

        } else if (!isOnWhitelist(playerName)) {
            this.m_SettingsWhitelistAllow.add(playerName);
            return saveWhitelist();
        }

        return false;
    }

    public boolean printDBUserList(CommandSender sender) {
        if (this.m_SqlConnection != null) {
            return this.m_SqlConnection.printDBUserList(sender);
        }
        return false;
    }
    
    public boolean dumpDBUserList(CommandSender sender) {
        if (this.m_SqlConnection != null) {
            return this.m_SqlConnection.dumpDB(sender);
        }
        return false;
    }

    public boolean removePlayerFromWhitelist(String playerName) {
        if (!config.sqlQueryRemove().isEmpty() && this.m_SqlConnection != null) {
            if (isOnWhitelist(playerName)) {
                return this.m_SqlConnection.removePlayerFromWhitelist(playerName, true);
            }
        } else {
            for (int i = 0; i < this.m_SettingsWhitelistAllow.size(); i++) {
                if (playerName.compareToIgnoreCase((String) this.m_SettingsWhitelistAllow.get(i)) != 0) {
                    continue;
                }
                this.m_SettingsWhitelistAllow.remove(i);
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
        for (String player : this.m_SettingsWhitelistAllow) {
            if (result.length() > 0) {
                result = result + ", ";
            }
            result = result + player;
        }
        return result;
    }

    public boolean isWhitelistActive() {
        return this.m_bWhitelistActive;
    }

    public void setWhitelistActive(boolean isWhitelistActive) {
        this.m_bWhitelistActive = isWhitelistActive;
    }

    public boolean needReloadWhitelist() {
        if (this.m_Watcher != null) {
            return this.m_Watcher.wasFileModified();
        }
        return false;
    }

    public void resetNeedReloadWhitelist() {
        if (this.m_Watcher != null) {
            this.m_Watcher.resetFileModifiedState();
        }
    }
}