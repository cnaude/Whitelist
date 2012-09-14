package me.cnaude.plugin.AutoWhitelist;

import java.io.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class Whitelist extends JavaPlugin {

    private static WLConfig config;    
    private final WLPlayerListener m_PlayerListner = new WLPlayerListener(this);
    
    private FileWatcher m_Watcher;
    private Timer m_Timer;
    private File m_Folder;
    private boolean m_bWhitelistActive;
    private SQLConnection m_SqlConnection;    
    private ArrayList<String> m_SettingsWhitelistAllow;
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
            logInfo("Whitelist: Whitelist is missing, creating...");
            try {
                fWhitelist.createNewFile();
                logDebug("Done.");
            } catch (IOException ex) {
                logDebug("Failed. [" + ex.getMessage() + "]");
            }
        }

        this.m_Watcher = new FileWatcher(fWhitelist);
        this.m_Timer = new Timer(true);
        this.m_Timer.schedule(this.m_Watcher, 0L, 1000L);
        
        

        PluginDescriptionFile pdfFile = getDescription();
        logDebug(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!");
    }
    
        public void logInfo(String _message) {
        log.log(Level.INFO, String.format("%s %s", LOG_HEADER, _message));
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
            player = (Player)sender;
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
                sender.sendMessage(ChatColor.RED + "Parameter missing: Player name");
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
        if (args[0].compareToIgnoreCase("list") == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Players on whitelist: " + ChatColor.GRAY + getFormatedAllowList());
            return true;
        }
        return false;
    }   
    
    public String colorSet(String finishedProduct) {
        Pattern blackColor = Pattern.compile("[^&]&0", Pattern.CASE_INSENSITIVE);
        Pattern blueColor = Pattern.compile("&1", Pattern.CASE_INSENSITIVE);
        Pattern greenColor = Pattern.compile("&2", Pattern.CASE_INSENSITIVE);
        Pattern darkAquaColor = Pattern.compile("&3", Pattern.CASE_INSENSITIVE);
        Pattern darkRedColor = Pattern.compile("&4", Pattern.CASE_INSENSITIVE);
        Pattern purpleColor = Pattern.compile("&5", Pattern.CASE_INSENSITIVE);
        Pattern orangeColor = Pattern.compile("&6", Pattern.CASE_INSENSITIVE);
        Pattern greyColor = Pattern.compile("&7", Pattern.CASE_INSENSITIVE);
        Pattern darkGrayColor = Pattern.compile("&8", Pattern.CASE_INSENSITIVE);
        Pattern lightBlueColor = Pattern.compile("&9", Pattern.CASE_INSENSITIVE);
        Pattern lightGreenColor = Pattern.compile("&a", Pattern.CASE_INSENSITIVE);
        Pattern lightAquaColor = Pattern.compile("&b", Pattern.CASE_INSENSITIVE);
        Pattern redColor = Pattern.compile("&c", Pattern.CASE_INSENSITIVE);
        Pattern lightPurpleColor = Pattern.compile("&d", Pattern.CASE_INSENSITIVE);
        Pattern yellowColor = Pattern.compile("&e", Pattern.CASE_INSENSITIVE);
        Pattern whiteColor = Pattern.compile("&f", Pattern.CASE_INSENSITIVE);

        Pattern magicColor = Pattern.compile("&k", Pattern.CASE_INSENSITIVE);
        Pattern boldColor = Pattern.compile("&l", Pattern.CASE_INSENSITIVE);
        Pattern strikeThroughColor = Pattern.compile("&m", Pattern.CASE_INSENSITIVE);
        Pattern underlineColor = Pattern.compile("&n", Pattern.CASE_INSENSITIVE);
        Pattern italicColor = Pattern.compile("&o", Pattern.CASE_INSENSITIVE);
        Pattern resetColor = Pattern.compile("&r", Pattern.CASE_INSENSITIVE);

        Pattern escapeCode = Pattern.compile("&§|&&");

        finishedProduct = blackColor.matcher(finishedProduct).replaceAll(ChatColor.BLACK.toString());
        finishedProduct = blueColor.matcher(finishedProduct).replaceAll(ChatColor.DARK_BLUE.toString());
        finishedProduct = greenColor.matcher(finishedProduct).replaceAll(ChatColor.DARK_GREEN.toString());
        finishedProduct = darkAquaColor.matcher(finishedProduct).replaceAll(ChatColor.DARK_AQUA.toString());
        finishedProduct = darkRedColor.matcher(finishedProduct).replaceAll(ChatColor.DARK_RED.toString());
        finishedProduct = purpleColor.matcher(finishedProduct).replaceAll(ChatColor.DARK_PURPLE.toString());
        finishedProduct = orangeColor.matcher(finishedProduct).replaceAll(ChatColor.GOLD.toString());
        finishedProduct = greyColor.matcher(finishedProduct).replaceAll(ChatColor.GRAY.toString());
        finishedProduct = darkGrayColor.matcher(finishedProduct).replaceAll(ChatColor.DARK_GRAY.toString());
        finishedProduct = lightBlueColor.matcher(finishedProduct).replaceAll(ChatColor.BLUE.toString());
        finishedProduct = lightGreenColor.matcher(finishedProduct).replaceAll(ChatColor.GREEN.toString());
        finishedProduct = lightAquaColor.matcher(finishedProduct).replaceAll(ChatColor.AQUA.toString());
        finishedProduct = redColor.matcher(finishedProduct).replaceAll(ChatColor.RED.toString());
        finishedProduct = lightPurpleColor.matcher(finishedProduct).replaceAll(ChatColor.LIGHT_PURPLE.toString());
        finishedProduct = yellowColor.matcher(finishedProduct).replaceAll(ChatColor.YELLOW.toString());
        finishedProduct = whiteColor.matcher(finishedProduct).replaceAll(ChatColor.WHITE.toString());

        finishedProduct = magicColor.matcher(finishedProduct).replaceAll(ChatColor.MAGIC.toString());
        finishedProduct = boldColor.matcher(finishedProduct).replaceAll(ChatColor.BOLD.toString());
        finishedProduct = strikeThroughColor.matcher(finishedProduct).replaceAll(ChatColor.STRIKETHROUGH.toString());
        finishedProduct = underlineColor.matcher(finishedProduct).replaceAll(ChatColor.UNDERLINE.toString());
        finishedProduct = italicColor.matcher(finishedProduct).replaceAll(ChatColor.ITALIC.toString());
        finishedProduct = resetColor.matcher(finishedProduct).replaceAll(ChatColor.RESET.toString());

        finishedProduct = escapeCode.matcher(finishedProduct).replaceAll("&");

        return finishedProduct;
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
                this.m_SqlConnection = new SQLConnection(this);                        
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

    public boolean removePlayerFromWhitelist(String playerName) {
        if (!config.sqlQueryRemove().isEmpty() &&  this.m_SqlConnection != null) {
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