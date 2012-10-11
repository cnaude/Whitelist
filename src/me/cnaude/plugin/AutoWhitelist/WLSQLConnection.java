package me.cnaude.plugin.AutoWhitelist;

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

public class WLSQLConnection {

    Connection connection;
    Driver proxyDriver;
    private WLMain plugin;    

    public WLSQLConnection(WLMain instance)
            throws Exception {
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
                    this.proxyDriver = new WLDriverProxy((Driver) Class.forName(plugin.getWLConfig().sqlDriver(), true, ucl).newInstance());
                    DriverManager.registerDriver(this.proxyDriver);
                } else {
                    Class.forName(plugin.getWLConfig().sqlDriver()).newInstance();
                }
            }
            plugin.logDebug("SqlDriverConnection: " + plugin.getWLConfig().sqlConnection());            
            this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
        } catch (SQLException ex) {
            plugin.logError("SQLException: " + ex.getMessage());
            plugin.logError("SQLState: " + ex.getSQLState());
            plugin.logError("VendorError: " + ex.getErrorCode());
            throw ex;
        } catch (Exception ex) {
            plugin.logError("Exception: " + ex.toString() + " - missing connector?");
            throw ex;
        }
    }

    public void Cleanup() {
        if (this.proxyDriver != null) {
            try {
                DriverManager.deregisterDriver(this.proxyDriver);
                this.proxyDriver = null;
            } catch (Exception ex) {
                this.proxyDriver = null;
            }
        }
    }

    public boolean isOnWhitelist(String playerName, boolean bRetry) {
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
            if (bRetry) {
                return isOnWhitelist(playerName, false);
            }

            plugin.logError("SQLException: " + ex.getMessage());
            plugin.logError("SQLState: " + ex.getSQLState());
            plugin.logError("VendorError: " + ex.getErrorCode());
        } catch (Exception ex) {
            plugin.logError("Exception: " + ex.getMessage());
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

            plugin.logError("Whitelist: SQLException: " + ex.getMessage());
            plugin.logError("Whitelist: SQLState: " + ex.getSQLState());
            plugin.logError("Whitelist: VendorError: " + ex.getErrorCode());
        } catch (Exception ex) {
            plugin.logError("Whitelist: Exception: " + ex.getMessage());
        }
        return false;
    }
    
    public boolean addPlayerToWhitelist(String playerName, boolean bRetry) {
        if ((plugin.getWLConfig().sqlQueryAdd() != null) && (!plugin.getWLConfig().sqlQueryAdd().isEmpty())) {
            try {
                if (this.connection == null) {
                    this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
                }
                Statement stmt = this.connection.createStatement();
                stmt.execute(plugin.getWLConfig().sqlQueryAdd().replace("<%USERNAME%>", playerName));
                return true;
            } catch (SQLException ex) {
                this.connection = null;
                if (bRetry) {
                    return addPlayerToWhitelist(playerName, false);
                }
                plugin.logError("SQLException: " + ex.getMessage());
                plugin.logError("SQLState: " + ex.getSQLState());
                plugin.logError("VendorError: " + ex.getErrorCode());
            } catch (Exception ex) {
                plugin.logError("Exception: " + ex.getMessage());
            }
        }
        return false;
    }

    public boolean removePlayerFromWhitelist(String playerName, boolean bRetry) {
        if ((plugin.getWLConfig().sqlQueryRemove() != null) && (!plugin.getWLConfig().sqlQueryRemove().isEmpty())) {
            try {
                if (this.connection == null) {
                    this.connection = DriverManager.getConnection(plugin.getWLConfig().sqlConnection(), plugin.getWLConfig().sqlUsername(), plugin.getWLConfig().sqlPassword());
                }
                Statement stmt = this.connection.createStatement();
                stmt.execute(plugin.getWLConfig().sqlQueryRemove().replace("<%USERNAME%>", playerName));                
                return true;
            } catch (SQLException ex) {
                this.connection = null;
                if (bRetry) {
                    return removePlayerFromWhitelist(playerName, false);
                }

                plugin.logError("SQLException: " + ex.getMessage());
                plugin.logError("SQLState: " + ex.getSQLState());
                plugin.logError("VendorError: " + ex.getErrorCode());
            } catch (Exception ex) {
                plugin.logError("Exception: " + ex.getMessage());
            }
        }
        return false;
    }
    
}
