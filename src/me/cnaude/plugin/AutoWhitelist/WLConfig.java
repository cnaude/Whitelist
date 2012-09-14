/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package me.cnaude.plugin.AutoWhitelist;

import org.bukkit.configuration.Configuration;

/**
 *
 * @author cnaude
 */
public final class WLConfig {

    private final Configuration config;
    private static final String DEBUG_MODE = "DebugMode";
    private static final String FILE_CHECK_INTERVAL = "FileCheckInterval";
    private static final String KICK_MESSAGE = "KickMessage";
    private static final String SQL_ENABLE = "SqlEnable";
    private static final String SQL_DRIVER = "SqlDriver";
    private static final String SQL_DRIVER_CONNECTION = "SqlDriverConnection";
    private static final String SQL_QUERY = "SqlQuery";
    private static final String SQL_QUERY_LIST = "SqlQueryList";
    private static final String SQL_QUERY_ADD = "SqlQueryAdd";
    private static final String SQL_QUERY_REMOVE = "SqlQueryRemove";
    private static final String SQL_DRIVER_JAR = "SqlDriverJar";
    
    private boolean debugMode;
    private long fileCheckInterval;
    private String kickMessage;      
    private boolean sqlEnabled;
    private String sqlDriver;
    private String sqlConnection;
    private String sqlQuery;
    private String sqlQueryCount;
    private String sqlQueryList;
    private String sqlQueryAdd;
    private String sqlQueryRemove;
    private String sqlDriverJar;

    public WLConfig(Whitelist plug) {
        config = plug.getConfig();
        loadValues(plug);
    }

    public void loadValues(Whitelist plug) {
        debugMode = config.getBoolean(DEBUG_MODE, false);
        fileCheckInterval = config.getLong(FILE_CHECK_INTERVAL, 1000L);
        kickMessage = plug.colorSet(config.getString(KICK_MESSAGE, "&bSorry! you are not on the &fwhitelist!"));
        sqlEnabled = config.getBoolean(SQL_ENABLE, false);
        sqlDriver = config.getString(SQL_DRIVER, "com.mysql.jdbc.Driver");
        sqlConnection = config.getString(SQL_DRIVER_CONNECTION, "jdbc:mysql://localhost/DATABASENAME?user=USERNAME&password=PASSWORD");
        sqlQuery = config.getString(SQL_QUERY, "SELECT name FROM tbl_names WHERE name='<%USERNAME%>'");
        sqlQueryList = config.getString(SQL_QUERY_LIST, "SELECT name FROM tbl_names");
        sqlQueryAdd = config.getString(SQL_QUERY_ADD, "INSERT INTO tbl_users (name) VALUES ('<%USERNAME%>')");
        sqlQueryRemove = config.getString(SQL_QUERY_REMOVE, "DELETE FROM tbl_users WHERE Name='<%USERNAME%>'");
        sqlDriverJar = config.getString(SQL_DRIVER_JAR, "lib/mysql-connector-java-bin.jar");

    }

    public boolean debugMode() {
        return debugMode;
    }

    public String kickMessage() {
        return kickMessage;
    }

    public boolean sqlEnabled() {
        return sqlEnabled;
    }

    public String sqlDriver() {
        return sqlDriver;
    }

    public String sqlConnection() {
        return sqlConnection;
    }

    public String sqlQuery() {
        return sqlQuery;
    }

    public String sqlQueryCount() {
        return sqlQueryCount;
    }

    public String sqlQueryList() {
        return sqlQueryList;
    }

    public String sqlQueryAdd() {
        return sqlQueryAdd;
    }

    public String sqlQueryRemove() {
        return sqlQueryRemove;
    }

    public String sqlDriverJar() {
        return sqlDriverJar;
    }
    
    public Long fileCheckInterval() {
        return fileCheckInterval;
    }
}
