package org.apache.eventmesh.store.h2.schema.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.eventmesh.store.api.openschema.request.SchemaCreateRequest;
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
    
    public Schema getSchemaBySubjectAndVersion(String subject, int version) throws SQLException {        
        String sql = "SELECT id, name, comment, serialization, schemaType, schemaDefinition, validator, version" +
                " FROM schema_registry WHERE subject_name = ? and version = ?";

        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, subject);
            stmt.setInt(2, version);
            rs = stmt.executeQuery();
            Schema schema = null;
            while (rs.next()) {
                schema = new Schema(rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("comment"),                        
                        rs.getString("serialization"),
                        rs.getString("schemaType"),
                		rs.getString("schemaDefinition"),
						rs.getString("validator"),
						rs.getInt("version"),
						"");
            }
            return schema;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    public List<Integer> getAllVersionsBySubject(String subject) throws SQLException {        
        String sql = "SELECT version" +
                " FROM schema_registry WHERE subject_name = ?";

        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, subject);            
            rs = stmt.executeQuery();
            List<Integer> allVersions = new ArrayList<>();
            while (rs.next()) {
            	allVersions.add(rs.getInt("version"));
            }
            return allVersions;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    public Schema getSchemaById(String id) throws SQLException {        
        String sql = "SELECT id, name, comment, serialization, schemaType, schemaDefinition, validator, version," +
                " FROM schema_registry WHERE id = ?";

        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, id);
            rs = stmt.executeQuery();
            Schema schema = null;
            while (rs.next()) {
                schema = new Schema(rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("comment"),                        
                        rs.getString("serialization"),
                        rs.getString("schemaType"),
                		rs.getString("schemaDefinition"),
						rs.getString("validator"),
						rs.getInt("version"),
						"");
            }
            return schema;
        } finally {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    public String insertSchema(SchemaCreateRequest schemaCreateRequest, String subject) throws SQLException {
    	String sql = "INSERT INTO schema_registry" + 
   			 "(id, name, comment, serialization, schemaType, schemaDefinition, validator, version, subject_name) values" +
   			 "(?,?,?,?,?,?,?,?,?)";                

	   Connection connection = null;
	   PreparedStatement stmt = null;
	   int row = 0;
	   try {
	       connection = dataSource.getConnection();
	       stmt = connection.prepareStatement(sql);
	       stmt.setString(1, schemaCreateRequest.getId());
	       stmt.setString(2, schemaCreateRequest.getName());
	       stmt.setString(3, schemaCreateRequest.getComment());
	       stmt.setString(4, schemaCreateRequest.getSerialization());
	       stmt.setString(5, schemaCreateRequest.getSchemaType());
	       stmt.setString(6, schemaCreateRequest.getSchemaDefinition());
	       stmt.setString(7, schemaCreateRequest.getValidator());
	       stmt.setString(8, schemaCreateRequest.getVersion());
	       stmt.setString(9, subject);
	       row = stmt.executeUpdate();
	       if (row == 0) {
	       	   return null;
	       }                        
	       
	       //If no error from insert, return subject object.
	       //Schema schema = new Schema(schemaCreateRequest.getId());
	      
	       return schemaCreateRequest.getId();
	   } finally {
	       if (stmt != null) {
	           stmt.close();
	       }
	       if (connection != null) {
	           connection.close();
	       }
	   }

    }
    
    public int deleteAllSchemaVersionsBySubject(String subject) throws SQLException {        
        String sql = "DELETE" +
                " FROM schema_registry WHERE subject_name = ?";

        Connection connection = null;
        PreparedStatement stmt = null;
        int row = 0;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, subject);            
            row = stmt.executeUpdate();
            return row;
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    public int deleteSchemaBySubjectAndVersion(String subject, int version) throws SQLException {        
        String sql = "DELETE" +
        		" FROM schema_registry WHERE subject_name = ? and version = ?";

        Connection connection = null;
        PreparedStatement stmt = null;
        int row = 0;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, subject);
            stmt.setInt(2, version);      
            row = stmt.executeUpdate();
            return row;
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
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
