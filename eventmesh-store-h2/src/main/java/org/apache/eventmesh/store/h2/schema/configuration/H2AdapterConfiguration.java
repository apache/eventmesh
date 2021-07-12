package org.apache.eventmesh.store.h2.schema.configuration;

public class H2AdapterConfiguration {    

    public boolean adapterEnabled = true;

    public String adapterDatabaseDriver = "org.h2.Driver";

    public String adapterDatabaseUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";

    public String adapterDatabaseUsername = "";

    public String adapterDatabasePassword = "";

    public int adapterDatabaseMaxIdle = 10;

    public int adapterDatabaseMinIdle = 5;
    
    public int adapterDatabaseMaxStatements = 5;    
    
    public H2AdapterConfiguration() {}
    
}
