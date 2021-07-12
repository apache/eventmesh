package org.apache.eventmesh.store.h2.schema.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.eventmesh.store.h2.schema.domain.Schema;
import org.apache.eventmesh.store.h2.schema.util.DBDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaRepository {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());

    private DBDataSource dataSource;

    private static SchemaRepository instance;

    public static synchronized SchemaRepository createInstance(DBDataSource dataSource) {
        if (instance == null) {
            instance = new SchemaRepository(dataSource);
        }
        return instance;
    }

    private SchemaRepository(DBDataSource dataSource) {
        this.dataSource = dataSource;        
    }
    
    public void insertWithPreparedStatement() throws SQLException {
        Connection connection = dataSource.getConnection();
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

    public void insertWithStatement() throws SQLException {
    	Connection connection = dataSource.getConnection();
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
    
    public void createSchemaTable() {    	        
        Connection connection = null;
        Statement stmt = null;
        try {
        	connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            stmt = connection.createStatement();
            
            stmt.execute("CREATE TABLE `schema_registry` (\r\n"
            		+ "  `id` varchar(255) NOT NULL,\r\n"
            		+ "  `name` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `comment` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `serialization` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `schemaType` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `schemaDefinition` varchar(8024) DEFAULT NULL,\r\n"
            		+ "  `validator` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `version` int(11) DEFAULT NULL,\r\n"
            		+ "  `subject_name` varchar(255) DEFAULT NULL,\r\n"
            		+ "  PRIMARY KEY (`id`),\r\n"            		
            		+ "  CONSTRAINT `fk_subject_name` FOREIGN KEY (`subject_name`) REFERENCES `subject_registry` (`name`) ON DELETE NO ACTION ON UPDATE NO ACTION\r\n"
            		+ ")");                                              
            
            stmt.close();
            connection.commit();
        } catch (SQLException e) {
        	logger.info("SQLException Message " + e.getLocalizedMessage());        	
        } finally {
            try {
				connection.close();
			} catch (SQLException e) {				
				logger.info("SQLException Message " + e.getLocalizedMessage());
			}
        }

    }

}
