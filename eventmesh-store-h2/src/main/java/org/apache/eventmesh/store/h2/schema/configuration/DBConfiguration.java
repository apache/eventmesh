package org.apache.eventmesh.store.h2.schema.configuration;

public class DBConfiguration {
    private String url;

    private String userName;

    private String password;

    private int maxIdleConnections;

    private int minIdleConnections;
    
    private int maxOpenPreparedStatements;        

    public DBConfiguration(String url, String userName, String password, int maxIdle, int minIdle, 
    		int maxOpenPreparedStatements) {
        this.url = url;
        this.userName = userName;
        this.password = password;
        this.maxIdleConnections = maxIdle;
        this.minIdleConnections = minIdle;
        this.maxOpenPreparedStatements = maxOpenPreparedStatements;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getMaxIdleConnections() {
        return maxIdleConnections;
    }

    public void setMaxIdleConnections(int maxIdleConnections) {
        this.maxIdleConnections = maxIdleConnections;
    }

    public int getMinIdleConnections() {
        return minIdleConnections;
    }

    public void setMinIdleConnections(int minIdleConnections) {
        this.minIdleConnections = minIdleConnections;
    }
    
    public int getMaxOpenPreparedStatements() {
        return maxOpenPreparedStatements;
    }

    public void setMaxOpenPreparedStatements(int maxOpenPreparedStatements) {
        this.maxOpenPreparedStatements = maxOpenPreparedStatements;
    }
}
