package net.immortal_forces.silence.plugin.whitelist;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Timer;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Whitelist extends JavaPlugin {

    private final String PROP_KICKMESSAGE = "kick-message";
    private final String PROP_WHITELIST_ADMINS = "whitelist-admins";
    private final String PROP_DISABLE_LIST = "disable-list-command";
    private final String PROP_USE_SQL = "sql-enable";
    private final String PROP_SQL_DRIVER_JAR = "sql-driver-jar";
    private final String PROP_SQL_DRIVER = "sql-driver";
    private final String PROP_SQL_CONNECTION = "sql-driver-connection";
    private final String PROP_SQL_QUERY = "sql-query";
    private final String PROP_SQL_QUERY_ADD = "sql-query-add";
    private final String PROP_SQL_QUERY_REMOVE = "sql-query-remove";
    private final String FILE_WHITELIST = "whitelist.txt";
    private final String FILE_CONFIG = "whitelist.properties";
    private final WLPlayerListener m_PlayerListner = new WLPlayerListener(this);
    private FileWatcher m_Watcher;
    private Timer m_Timer;
    private File m_Folder;
    private boolean m_bWhitelistActive;
    private SQLConnection m_SqlConnection;
    private ArrayList<String> m_SettingsWhitelistAdmins;
    private ArrayList<String> m_SettingsWhitelistAllow;
    private String m_strSettingsKickMessage;
    private boolean m_bSettingsListCommandDisabled;
    private boolean m_bSettingsSqlEnabled;
    private String m_strSettingsSqlDriverJar;
    private String m_strSettingsSqlDriver;
    private String m_strSettingsSqlConnection;
    private String m_strSettingsSqlQuery;
    private String m_strSettingsSqlQueryAdd;
    private String m_strSettingsSqlQueryRemove;

    @Override
    public void onEnable() {
        this.m_Folder = getDataFolder();
        this.m_strSettingsKickMessage = "";
        this.m_SettingsWhitelistAdmins = new ArrayList();
        this.m_SettingsWhitelistAllow = new ArrayList();
        this.m_bWhitelistActive = true;
        this.m_bSettingsListCommandDisabled = false;
        this.m_bSettingsSqlEnabled = false;
        this.m_strSettingsSqlDriverJar = "";
        this.m_strSettingsSqlDriver = "";
        this.m_strSettingsSqlConnection = "";
        this.m_strSettingsSqlQuery = "";
        this.m_strSettingsSqlQueryAdd = "";
        this.m_strSettingsSqlQueryRemove = "";
        this.m_SqlConnection = null;

        PluginManager pm = getServer().getPluginManager();

        getServer().getPluginManager().registerEvents(m_PlayerListner, this);

        if (!this.m_Folder.exists()) {
            System.out.print("Whitelist: Config folder missing, creating...");
            this.m_Folder.mkdir();
            System.out.println("done.");
        }
        File fWhitelist = new File(this.m_Folder.getAbsolutePath() + File.separator + "whitelist.txt");
        if (!fWhitelist.exists()) {
            System.out.print("Whitelist: Whitelist is missing, creating...");
            try {
                fWhitelist.createNewFile();
                System.out.println("done.");
            } catch (IOException ex) {
                System.out.println("failed.");
            }
        }

        this.m_Watcher = new FileWatcher(fWhitelist);
        this.m_Timer = new Timer(true);
        this.m_Timer.schedule(this.m_Watcher, 0L, 1000L);

        File fConfig = new File(this.m_Folder.getAbsolutePath() + File.separator + "whitelist.properties");
        if (!fConfig.exists()) {
            System.out.print("Whitelist: Config is missing, creating...");
            try {
                fConfig.createNewFile();
                Properties propConfig = new Properties();
                propConfig.setProperty("kick-message", "Sorry, you are not on the whitelist!");
                propConfig.setProperty("whitelist-admins", "Name1,Name2,Name3");
                propConfig.setProperty("disable-list-command", "false");

                BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(fConfig.getAbsolutePath()));
                propConfig.store(stream, "Auto generated config file, please modify");
                System.out.println("done.");
            } catch (IOException ex) {
                System.out.println("failed.");
            }
        }
        loadWhitelistSettings();

        PluginDescriptionFile pdfFile = getDescription();
        System.out.println(pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!");
    }

    @Override
    public void onDisable() {
        this.m_Timer.cancel();
        this.m_Timer.purge();
        this.m_Timer = null;
        System.out.println("Goodbye world!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Player player = null;
        try {
            player = (Player) sender;
        } catch (Exception e) {
        }

        if (player != null) {
            if (!isAdmin(player.getName())) {
                return true;
            }
        }
        if (args.length < 1) {
            return false;
        }
        if (args[0].compareToIgnoreCase("help") == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Commands:");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist reload  (reloads the whitelist and settings)");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist add [player]  (adds a player to the whitelist)");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist remove [player]  (removes a player from the whitelist)");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist on|off  (actives/deactivates whitelist)");
            sender.sendMessage(ChatColor.YELLOW + "/whitelist list  (list whitelist entries)");
            return true;
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
        if (args[0].compareToIgnoreCase("list") == 0) {
            if (!isListCommandDisabled()) {
                sender.sendMessage(ChatColor.RED + "List command is disabled!");
            } else {
                sender.sendMessage(ChatColor.YELLOW + "Players on whitelist: " + ChatColor.GRAY + getFormatedAllowList());
            }
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
        System.out.print("Whitelist: Trying to load whitelist and settings...");
        try {
            this.m_SettingsWhitelistAllow.clear();
            BufferedReader reader = new BufferedReader(new FileReader(this.m_Folder.getAbsolutePath() + File.separator + "whitelist.txt"));
            String line = reader.readLine();
            while (line != null) {
                this.m_SettingsWhitelistAllow.add(line);
                line = reader.readLine();
            }
            reader.close();

            Properties propConfig = new Properties();
            BufferedInputStream stream = new BufferedInputStream(new FileInputStream(this.m_Folder.getAbsolutePath() + File.separator + "whitelist.properties"));
            propConfig.load(stream);
            this.m_strSettingsKickMessage = colorSet(propConfig.getProperty("kick-message"));
            if (this.m_strSettingsKickMessage == null) {
                this.m_strSettingsKickMessage = "";
            }
            this.m_SettingsWhitelistAdmins.clear();
            String rawAdminList = propConfig.getProperty("whitelist-admins");
            if (rawAdminList != null) {
                String[] admins = rawAdminList.split(",");
                if (admins != null) {
                    this.m_SettingsWhitelistAdmins.addAll(Arrays.asList(admins));
                }
            }
            String rawDisableListCommand = propConfig.getProperty("disable-list-command");
            if (rawDisableListCommand != null) {
                this.m_bSettingsListCommandDisabled = Boolean.parseBoolean(rawDisableListCommand);
            }
            String rawUseSql = propConfig.getProperty("sql-enable");
            if (rawUseSql != null) {
                this.m_bSettingsSqlEnabled = Boolean.parseBoolean(rawUseSql);
            }
            this.m_strSettingsSqlDriver = propConfig.getProperty("sql-driver");
            if (this.m_strSettingsSqlDriver == null) {
                this.m_strSettingsSqlDriver = "";
            }
            this.m_strSettingsSqlConnection = propConfig.getProperty("sql-driver-connection");
            if (this.m_strSettingsSqlConnection == null) {
                this.m_strSettingsSqlConnection = "";
            }
            this.m_strSettingsSqlQuery = propConfig.getProperty("sql-query");
            if (this.m_strSettingsSqlQuery == null) {
                this.m_strSettingsSqlQuery = "";
            }
            this.m_strSettingsSqlQueryAdd = propConfig.getProperty("sql-query-add");
            if (this.m_strSettingsSqlQueryAdd == null) {
                this.m_strSettingsSqlQueryAdd = "";
            }
            this.m_strSettingsSqlQueryRemove = propConfig.getProperty("sql-query-remove");
            if (this.m_strSettingsSqlQueryRemove == null) {
                this.m_strSettingsSqlQueryRemove = "";
            }
            this.m_strSettingsSqlDriverJar = propConfig.getProperty("sql-driver-jar");
            if (this.m_bSettingsSqlEnabled) {
                this.m_SqlConnection = new SQLConnection(this.m_strSettingsSqlDriver, this.m_strSettingsSqlConnection, this.m_strSettingsSqlQuery, this.m_strSettingsSqlQueryAdd, this.m_strSettingsSqlQueryRemove, this.m_strSettingsSqlDriverJar);
            } else {
                if (this.m_SqlConnection != null) {
                    this.m_SqlConnection.Cleanup();
                }
                this.m_SqlConnection = null;
            }

            System.out.println("done.");
        } catch (Exception ex) {
            System.out.println("failed: " + ex);
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
            System.out.println(ex);
            return false;
        }
        return true;
    }

    public boolean isAdmin(String playerName) {
        for (String admin : this.m_SettingsWhitelistAdmins) {
            if (admin.compareToIgnoreCase(playerName) == 0) {
                return true;
            }
        }
        return false;
    }

    public boolean isOnWhitelist(String playerName) {
        if ((this.m_bSettingsSqlEnabled) && (this.m_SqlConnection != null)) {
            return this.m_SqlConnection.isOnWhitelist(playerName, true);
        }

        for (String player : this.m_SettingsWhitelistAllow) {
            if (player.compareToIgnoreCase(playerName) == 0) {
                return true;
            }
        }

        return false;
    }

    public boolean addPlayerToWhitelist(String playerName) {
        if (this.m_SqlConnection != null) {
            if (!isOnWhitelist(playerName)) {
                return this.m_SqlConnection.addPlayerToWhitelist(playerName, true);
            }

        } else if (!isOnWhitelist(playerName)) {
            this.m_SettingsWhitelistAllow.add(playerName);
            return saveWhitelist();
        }

        return false;
    }

    public boolean removePlayerFromWhitelist(String playerName) {
        if (this.m_SqlConnection != null) {
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

    public String getKickMessage() {
        return this.m_strSettingsKickMessage;
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

    public boolean isListCommandDisabled() {
        if (this.m_SqlConnection != null) {
            return false;
        }
        return this.m_bSettingsListCommandDisabled;
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