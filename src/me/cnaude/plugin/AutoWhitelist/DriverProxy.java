package me.cnaude.plugin.AutoWhitelist;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;

public class DriverProxy implements Driver {

    private Driver m_Driver;

    public DriverProxy(Driver driver) {
        this.m_Driver = driver;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return this.m_Driver.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return this.m_Driver.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return this.m_Driver.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return this.m_Driver.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return this.m_Driver.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return this.m_Driver.jdbcCompliant();
    }
}
