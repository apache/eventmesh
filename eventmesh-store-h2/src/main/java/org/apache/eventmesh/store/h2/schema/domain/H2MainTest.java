package org.apache.eventmesh.store.h2.schema.domain;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.eventmesh.store.h2.schema.H2SchemaAdapter;
import org.apache.eventmesh.store.h2.schema.configuration.DBConfiguration;
import org.apache.eventmesh.store.h2.schema.configuration.H2AdapterConfiguration;
import org.apache.eventmesh.store.h2.schema.util.DBDataSource;

public class H2MainTest {

    private static final String DB_DRIVER = "org.h2.Driver";
    private static final String DB_CONNECTION = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";
    private static final String DB_USER = "";
    private static final String DB_PASSWORD = "";
    
    private static DBDataSource dataSource;
    
    private static H2AdapterConfiguration adapterConfig;
    
    private static H2SchemaAdapter schemaAdapter;
    
    //private static SchemaServiceImpl schemaServiceImpl;
    
    public static void main(String[] args) throws Exception {
        try {
            insertWithStatement();
            insertWithPreparedStatement();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    	schemaAdapter = new H2SchemaAdapter();
    	schemaAdapter.init();
    	schemaAdapter.start();
    	
    	//schemaServiceImpl = new SchemaServiceImpl();
    	//schemaServiceImpl.createSubject(null);
    }

    private static void insertWithPreparedStatement() throws SQLException {
        Connection connection = getDBConnection();
        PreparedStatement createPreparedStatement = null;
        PreparedStatement insertPreparedStatement = null;
        PreparedStatement selectPreparedStatement = null;

        String CreateQuery = "CREATE TABLE PERSON(id int primary key, name varchar(255))";
        String InsertQuery = "INSERT INTO PERSON" + "(id, name) values" + "(?,?)";
        String SelectQuery = "select * from PERSON";

        try {
            connection.setAutoCommit(false);

            createPreparedStatement = connection.prepareStatement(CreateQuery);
            createPreparedStatement.executeUpdate();
            createPreparedStatement.close();

            insertPreparedStatement = connection.prepareStatement(InsertQuery);
            insertPreparedStatement.setInt(1, 1);
            insertPreparedStatement.setString(2, "Jose");
            insertPreparedStatement.executeUpdate();
            insertPreparedStatement.close();

            selectPreparedStatement = connection.prepareStatement(SelectQuery);
            ResultSet rs = selectPreparedStatement.executeQuery();
            System.out.println("H2 In-Memory Database inserted through PreparedStatement");
            while (rs.next()) {
                System.out.println("Id " + rs.getInt("id") + " Name " + rs.getString("name"));
            }
            selectPreparedStatement.close();

            connection.commit();
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connection.close();
        }
    }

    private static void insertWithStatement() throws SQLException {
        Connection connection = getDBConnection();
        Statement stmt = null;
        try {
            connection.setAutoCommit(false);
            stmt = connection.createStatement();
            stmt.execute("CREATE TABLE PERSON(id int primary key, name varchar(255))");
            stmt.execute("INSERT INTO PERSON(id, name) VALUES(1, 'Anju')");
            stmt.execute("INSERT INTO PERSON(id, name) VALUES(2, 'Sonia')");
            stmt.execute("INSERT INTO PERSON(id, name) VALUES(3, 'Asha')");

            ResultSet rs = stmt.executeQuery("select * from PERSON");
            System.out.println("H2 In-Memory Database inserted through Statement");
            while (rs.next()) {
                System.out.println("Id " + rs.getInt("id") + " Name " + rs.getString("name"));
            }

            stmt.execute("DROP TABLE PERSON");
            stmt.close();
            connection.commit();
        } catch (SQLException e) {
            System.out.println("Exception Message " + e.getLocalizedMessage());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            connection.close();
        }
    }

    private static Connection getDBConnection() throws SQLException {
        Connection dbConnection = null;
        /*try {
            Class.forName(DB_DRIVER);
        } catch (ClassNotFoundException e) {
            System.out.println("hoho" + e.getMessage());
        }
        try {
            dbConnection = DriverManager.getConnection(DB_CONNECTION, DB_USER, DB_PASSWORD);
            return dbConnection;
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }*/
        adapterConfig = new H2AdapterConfiguration();
        DBConfiguration dbConfig = new DBConfiguration(adapterConfig.adapterDatabaseUrl,
                adapterConfig.adapterDatabaseUsername, adapterConfig.adapterDatabasePassword,
                adapterConfig.adapterDatabaseMaxIdle, adapterConfig.adapterDatabaseMinIdle,
                adapterConfig.adapterDatabaseMaxStatements);
        dataSource = DBDataSource.createDataSource(dbConfig);
        dbConnection = dataSource.getConnection();
        return dbConnection;
    }
}
