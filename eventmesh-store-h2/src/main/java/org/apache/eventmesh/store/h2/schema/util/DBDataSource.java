package org.apache.eventmesh.store.h2.schema.util;

import org.apache.eventmesh.store.h2.schema.configuration.DBConfiguration;
import org.apache.commons.dbcp2.BasicDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DBDataSource {
    private static BasicDataSource ds = null;

    private DBDataSource() {

    }

    public static synchronized DBDataSource createDataSource(DBConfiguration dbConfig) {
        if (ds == null) { 
            ds = new BasicDataSource();
            ds.setDriverClassName("org.h2.Driver");
            ds.setUrl(dbConfig.getUrl());
            ds.setUsername(dbConfig.getUserName());
            ds.setPassword(dbConfig.getPassword());
            ds.setMaxIdle(dbConfig.getMaxIdleConnections());
            ds.setMinIdle(dbConfig.getMinIdleConnections());
            ds.setMaxOpenPreparedStatements(dbConfig.getMaxOpenPreparedStatements());
        }
        return new DBDataSource();
    }

    public Connection getConnection() throws SQLException {
        if (ds == null) {
            return null;
        }
        return ds.getConnection();
    }

    public void close() throws SQLException {
        if(ds != null) {
            ds.close();
        }
    }
}
