package com.cnaude.autowhitelist;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class SqlConnection {

    Connection connection;
    Driver proxyDriver;
    private final AutoWhitelist plugin;

    public SqlConnection(AutoWhitelist instance) throws Exception {
        plugin = instance;
        this.connection = null;
        try {
            if ((plugin.getWLConfig().sqlDriverJar() == null) || (!new File(plugin.getWLConfig().sqlDriverJar()).exists())) {
                Class.forName(plugin.getWLConfig().sqlDriver()).newInstance();
            } else {
                boolean bUseLoader = true;
                Enumeration e = DriverManager.getDrivers();
                while (e.hasMoreElements()) {
                    Driver driver = (Driver) e.nextElement();
                    if (driver.getClass().getName().compareToIgnoreCase(plugin.getWLConfig().sqlDriver()) == 0) {
                        bUseLoader = false;
                    }
                }
                if (bUseLoader) {
                    URL url = new URL("jar:file:" + plugin.getWLConfig().sqlDriver() + "!/");
                    URLClassLoader ucl = new URLClassLoader(new URL[]{url});
                    this.proxyDriver = new DriverProxy((Driver) Class.forName(plugin.getWLConfig().sqlDriver(), true, ucl).newInstance());
                    DriverManager.registerDriver(this.proxyDriver);
                } else {
                    Class.forName(plugin.getWLConfig().sqlDriver()).newInstance();
                }
            }
            plugin.logDebug("SqlDriverConnection: " + plugin.getWLConfig().sqlConnection());
            this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
        } catch (SQLException ex) {
            logSqlException(ex, plugin.getServer().getConsoleSender());
            throw ex;
        }
    }

    public void Cleanup() {
        if (this.proxyDriver != null) {
            try {
                DriverManager.deregisterDriver(this.proxyDriver);
                this.proxyDriver = null;
            } catch (SQLException ex) {
                this.proxyDriver = null;
            }
        }
    }

    public boolean isOnWhitelist(String playerName, CommandSender sender) {
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }
            if (!playerName.matches("[a-zA-Z0-9_]*")) {
                plugin.logInfo("Whitelist: Illegal characters in player name, disallow!");
                return false;
            }
            Statement stmt = this.connection.createStatement();
            ResultSet rst = stmt.executeQuery(plugin.getWLConfig().sqlQuery().replace("<%USERNAME%>", playerName));

            return rst.first();
        } catch (SQLException ex) {
            this.connection = null;
            logSqlException(ex, sender);
        }
        return false;
    }

    public boolean isOnWhitelist(User user, CommandSender sender) {
        String playerName = user.name;
        String uuidStr = user.uuid.toString();
        plugin.logDebug("p: " + playerName + ", u: " + uuidStr);
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }
            if (!playerName.matches("[a-zA-Z0-9_]*")) {
                plugin.logInfo("Whitelist: Illegal characters in player name, disallow!");
                return false;
            }
            Statement stmt = this.connection.createStatement();
            ResultSet rst = stmt.executeQuery(plugin.getWLConfig().sqlQuery()
                    .replace("<%USERNAME%>", playerName)
                    .replace("<%UUID%>", uuidStr)
            );

            return rst.first();
        } catch (SQLException ex) {
            this.connection = null;
            logSqlException(ex, sender);
        }
        return false;
    }

    public boolean printDBUserList(CommandSender sender) {
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }

            Statement stmt = this.connection.createStatement();
            ResultSet rst = stmt.executeQuery(plugin.getWLConfig().sqlQueryList());

            sender.sendMessage(ChatColor.YELLOW + "Players in whitelist database: ");
            //sender.sendMessage(rst.getString(1));              
            while (rst.next()) {
                sender.sendMessage(rst.getString(1));
            }
            return true;
        } catch (SQLException ex) {
            this.connection = null;
            sender.sendMessage(ChatColor.RED + "SQL Error: " + ex.getMessage());
            logSqlException(ex, sender);
        }
        return false;
    }

    public void dumpDB(CommandSender sender) {
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }
            Statement stmt = this.connection.createStatement();
            ResultSet rst = stmt.executeQuery(plugin.getWLConfig().sqlQueryList());

            int c = 0;
            while (rst.next()) {
                String pName = rst.getString(1);
                if (!plugin.whitelist.contains(pName)) {
                    plugin.whitelist.add(pName);
                    c++;
                }
            }
            plugin.saveWhitelist();
            sender.sendMessage(ChatColor.YELLOW + "Dumped " + ChatColor.WHITE + c + ChatColor.YELLOW
                    + " users to the " + ChatColor.WHITE + "whitelist.txt" + ChatColor.YELLOW + " file.");
        } catch (SQLException ex) {
            this.connection = null;
            logSqlException(ex, sender);
        }
    }

    public void addPlayerToWhitelist(String playerName, CommandSender sender) {
        if (plugin.getWLConfig().sqlQueryAdd().isEmpty()) {
            plugin.logError("SqlQueryAdd is empty!");
            return;
        }
        boolean success;
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }
            Statement stmt = this.connection.createStatement();
            stmt.execute(plugin.getWLConfig().sqlQueryAdd().replace("<%USERNAME%>", playerName));
            success = isOnWhitelist(playerName, sender);
        } catch (SQLException ex) {
            this.connection = null;
            logSqlException(ex, sender);
            success = false;
        }
        if (success) {
            sender.sendMessage(ChatColor.YELLOW + "Player added: " + ChatColor.WHITE + playerName);
        } else {
            sender.sendMessage(ChatColor.RED + "Error adding player: " + ChatColor.WHITE + playerName);
        }
    }

    public void addPlayerToWhitelist(User user, CommandSender sender) {
        if (plugin.getWLConfig().sqlQueryAdd().isEmpty()) {
            plugin.logError("SqlQueryAdd is empty!");
            return;
        }
        if (user.uuid == null) {
            sender.sendMessage(ChatColor.RED + "Invalid player: " + ChatColor.WHITE + user.name);
            return;
        }
        if (isOnWhitelist(user, sender)) {
            sender.sendMessage(ChatColor.YELLOW + "Player is already in the whitelist: " + ChatColor.WHITE + user.name + " (" + user.uuid + ")");
            return;
        }
        boolean success;
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }
            Statement stmt = this.connection.createStatement();
            stmt.execute(plugin.getWLConfig().sqlQueryAdd()
                    .replace("<%USERNAME%>", user.name)
                    .replace("<%UUID%>", user.uuid.toString())
                    .replace("<%OPER%>", user.oper)
                    .replace("<%TIME%>", String.valueOf(user.time))
            );
            success = isOnWhitelist(user, sender);
        } catch (SQLException ex) {
            this.connection = null;
            logSqlException(ex, sender);
            success = false;
        }
        if (success) {
            sender.sendMessage(ChatColor.YELLOW + "Player added: " + ChatColor.WHITE + user.name + " (" + user.uuid + ")");
        } else {
            sender.sendMessage(ChatColor.RED + "Error adding player: " + ChatColor.WHITE + user.name + " (" + user.uuid + ")");
        }
    }

    public void removePlayerFromWhitelist(String playerName, CommandSender sender) {
        if (plugin.getWLConfig().sqlQueryRemove().isEmpty()) {
            plugin.logError("SqlQueryRemove is empty!");
            return;
        }
        if (!isOnWhitelist(playerName, sender)) {
            sender.sendMessage(ChatColor.YELLOW + "Player is not in the whitelist: " + ChatColor.WHITE + playerName);
            return;
        }
        boolean success;
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }
            Statement stmt = this.connection.createStatement();
            stmt.execute(plugin.getWLConfig().sqlQueryRemove().replace("<%USERNAME%>", playerName));
            success = (!isOnWhitelist(playerName, sender));
        } catch (SQLException ex) {
            this.connection = null;
            logSqlException(ex, sender);
            success = false;
        }
        if (success) {
            sender.sendMessage(ChatColor.YELLOW + "Player removed: " + ChatColor.WHITE + playerName);
        } else {
            sender.sendMessage(ChatColor.RED + "Error removing player: " + ChatColor.WHITE + playerName);
        }
    }

    public void removePlayerFromWhitelist(User user, CommandSender sender) {
        if (plugin.getWLConfig().sqlQueryRemove().isEmpty()) {
            plugin.logError("SqlQueryRemove is empty!");
            return;
        }
        if (user.uuid == null) {
            sender.sendMessage(ChatColor.RED + "Invalid player: " + ChatColor.WHITE + user.name);
            return;
        }
        if (!isOnWhitelist(user, sender)) {
            sender.sendMessage(ChatColor.YELLOW + "Player is not in the whitelist: " + ChatColor.WHITE + user.name + " (" + user.uuid + ")");
            return;
        }
        boolean success;
        try {
            if (this.connection == null) {
                this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
            }
            Statement stmt = this.connection.createStatement();
            stmt.execute(plugin.getWLConfig().sqlQueryRemove()
                    .replace("<%USERNAME%>", user.name)
                    .replace("<%UUID%>", user.uuid.toString())
            );
            success = (!isOnWhitelist(user, sender));
        } catch (SQLException ex) {
            this.connection = null;
            logSqlException(ex, sender);
            success = false;
        }
        if (success) {
            sender.sendMessage(ChatColor.YELLOW + "Player removed: " + ChatColor.WHITE + user.name + " (" + user.uuid + ")");
        } else {
            sender.sendMessage(ChatColor.RED + "Error removing player: " + ChatColor.WHITE + user.name + " (" + user.uuid + ")");
        }
    }

    private void logSqlException(SQLException ex, CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "SQL Error: " + ex.getMessage());
        plugin.logError("SQLException: " + ex.getMessage());
        plugin.logError("SQLState: " + ex.getSQLState());
        plugin.logError("VendorError: " + ex.getErrorCode());
    }

}
