package net.immortal_forces.silence.plugin.whitelist;

import java.io.File;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;

public class SQLConnection {

    final String m_strQuery;
    final String m_strQueryAdd;
    final String m_strQueryRemove;
    final String m_strConnection;
    Connection m_Connection;
    Driver m_ProxyDriver;

    public SQLConnection(String strDriver, String strConnection, String strQuery, String strQueryAdd, String strQueryRemove, String strDriverPath)
            throws Exception {
        this.m_strQuery = strQuery;
        this.m_strQueryAdd = strQueryAdd;
        this.m_strQueryRemove = strQueryRemove;
        this.m_strConnection = strConnection;
        this.m_Connection = null;
        try {
            if ((strDriverPath == null) || (!new File(strDriverPath).exists())) {
                Class.forName(strDriver).newInstance();
            } else {
                boolean bUseLoader = true;
                Enumeration e = DriverManager.getDrivers();
                while (e.hasMoreElements()) {
                    Driver driver = (Driver) e.nextElement();
                    if (driver.getClass().getName().compareToIgnoreCase(strDriver) == 0) {
                        bUseLoader = false;
                    }
                }
                if (bUseLoader) {
                    URL url = new URL("jar:file:" + strDriverPath + "!/");
                    URLClassLoader ucl = new URLClassLoader(new URL[]{url});
                    this.m_ProxyDriver = new DriverProxy((Driver) Class.forName(strDriver, true, ucl).newInstance());
                    DriverManager.registerDriver(this.m_ProxyDriver);
                } else {
                    Class.forName(strDriver).newInstance();
                }
            }
            this.m_Connection = DriverManager.getConnection(strConnection);
        } catch (SQLException ex) {
            System.out.println("Whitelist: SQLException: " + ex.getMessage());
            System.out.println("Whitelist: SQLState: " + ex.getSQLState());
            System.out.println("Whitelist: VendorError: " + ex.getErrorCode());
            throw ex;
        } catch (Exception ex) {
            System.out.println("Whitelist: Exception: " + ex.toString() + " - missing connector?");
            throw ex;
        }
    }

    public void Cleanup() {
        if (this.m_ProxyDriver != null) {
            try {
                DriverManager.deregisterDriver(this.m_ProxyDriver);
                this.m_ProxyDriver = null;
            } catch (Exception ex) {
                this.m_ProxyDriver = null;
            }
        }
    }

    public boolean isOnWhitelist(String playerName, boolean bRetry) {
        try {
            if (this.m_Connection == null) {
                this.m_Connection = DriverManager.getConnection(this.m_strConnection);
            }

            if (!playerName.matches("[a-zA-Z0-9_]*")) {
                System.out.println("Whitelist: Illegal characters in player name, disallow!");
                return false;
            }
            Statement stmt = this.m_Connection.createStatement();
            ResultSet rst = stmt.executeQuery(this.m_strQuery.replace("<%USERNAME%>", playerName));

            return rst.first();
        } catch (SQLException ex) {
            this.m_Connection = null;
            if (bRetry) {
                return isOnWhitelist(playerName, false);
            }

            System.out.println("Whitelist: SQLException: " + ex.getMessage());
            System.out.println("Whitelist: SQLState: " + ex.getSQLState());
            System.out.println("Whitelist: VendorError: " + ex.getErrorCode());
        } catch (Exception ex) {
            System.out.println("Whitelist: Exception: " + ex.getMessage());
        }
        return false;
    }

    public boolean addPlayerToWhitelist(String playerName, boolean bRetry) {
        if ((this.m_strQueryAdd != null) && (!this.m_strQueryAdd.isEmpty())) {
            try {
                if (this.m_Connection == null) {
                    this.m_Connection = DriverManager.getConnection(this.m_strConnection);
                }
                Statement stmt = this.m_Connection.createStatement();
                stmt.execute(this.m_strQueryAdd.replace("<%USERNAME%>", playerName));
                return true;
            } catch (SQLException ex) {
                this.m_Connection = null;
                if (bRetry) {
                    return addPlayerToWhitelist(playerName, false);
                }

                System.out.println("Whitelist: SQLException: " + ex.getMessage());
                System.out.println("Whitelist: SQLState: " + ex.getSQLState());
                System.out.println("Whitelist: VendorError: " + ex.getErrorCode());
            } catch (Exception ex) {
                System.out.println("Whitelist: Exception: " + ex.getMessage());
            }
        }
        return false;
    }

    public boolean removePlayerFromWhitelist(String playerName, boolean bRetry) {
        if ((this.m_strQueryRemove != null) && (!this.m_strQueryRemove.isEmpty())) {
            try {
                if (this.m_Connection == null) {
                    this.m_Connection = DriverManager.getConnection(this.m_strConnection);
                }
                Statement stmt = this.m_Connection.createStatement();
                stmt.execute(this.m_strQueryRemove.replace("<%USERNAME%>", playerName));
                return true;
            } catch (SQLException ex) {
                this.m_Connection = null;
                if (bRetry) {
                    return removePlayerFromWhitelist(playerName, false);
                }

                System.out.println("Whitelist: SQLException: " + ex.getMessage());
                System.out.println("Whitelist: SQLState: " + ex.getSQLState());
                System.out.println("Whitelist: VendorError: " + ex.getErrorCode());
            } catch (Exception ex) {
                System.out.println("Whitelist: Exception: " + ex.getMessage());
            }
        }
        return false;
    }
}
