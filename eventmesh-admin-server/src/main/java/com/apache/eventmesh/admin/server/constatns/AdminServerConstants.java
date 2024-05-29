package com.apache.eventmesh.admin.server.constatns;

public class AdminServerConstants {
    public static final String CONF_ENV = "configurationPath";

    public static final String EVENTMESH_CONF_HOME = System.getProperty(CONF_ENV, System.getenv(CONF_ENV));

    public static final String EVENTMESH_CONF_FILE = "eventmesh-admin.properties";
}
