/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.store.h2.schema.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.apache.eventmesh.store.api.openschema.request.SubjectCreateRequest;
import org.apache.eventmesh.store.h2.schema.domain.Subject;
import org.apache.eventmesh.store.h2.schema.util.DBDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubjectRepository {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());

    private DBDataSource dataSource;

    private static SubjectRepository instance;

    public static synchronized SubjectRepository createInstance(DBDataSource dataSource) {
        if (instance == null) {
            instance = new SubjectRepository(dataSource);            
        }
        return instance;
    }

    private SubjectRepository(DBDataSource dataSource) {
        this.dataSource = dataSource;        
    }  
    
    public List<String> getAllSubjects() throws SQLException {
        String sql = "SELECT name FROM subject_registry";

        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            rs = stmt.executeQuery();
            List<String> subjects = new ArrayList<>();
            while (rs.next()) {
            	String subject = rs.getString("name");
                subjects.add(subject);
            }
            return subjects;
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
    
    public Subject getSubjectByName(String name) throws SQLException {        
        String sql = "SELECT name, tenant, namespace, app, description, status, compatibility, coordinate" +
                " FROM subject_registry WHERE name = ?";

        Connection connection = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, name);
            rs = stmt.executeQuery();
            Subject subject = null;
            while (rs.next()) {
                subject = new Subject(rs.getString("name"),
                        rs.getString("tenant"),
                        rs.getString("namespace"),                        
                        rs.getString("app"),
                        rs.getString("description"),
                		rs.getString("status"),
						rs.getString("compatibility"),
						rs.getString("coordinate"));
            }
            return subject;
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
    
    public Subject insertSubject(SubjectCreateRequest subjectCreateRequest) throws SQLException {        
        String sql = "INSERT INTO subject_registry" + 
        			 "(name, tenant, namespace, app, description, status, compatibility, coordinate) values" +
        			 "(?,?,?,?,?,?,?,?)";                

        Connection connection = null;
        PreparedStatement stmt = null;
        int row = 0;
        try {
            connection = dataSource.getConnection();
            stmt = connection.prepareStatement(sql);
            stmt.setString(1, subjectCreateRequest.getSubject());
            stmt.setString(2, subjectCreateRequest.getTenant());
            stmt.setString(3, subjectCreateRequest.getNamespace());
            stmt.setString(4, subjectCreateRequest.getApp());
            stmt.setString(5, subjectCreateRequest.getDescription());
            stmt.setString(6, subjectCreateRequest.getStatus());
            stmt.setString(7, subjectCreateRequest.getCompatibility());
            stmt.setString(8, subjectCreateRequest.getCoordinate());
            row = stmt.executeUpdate();
            if (row == 0) {
            	return null;
            }                        
            
            //If no error from insert, return subject object.
            Subject subject = new Subject(subjectCreateRequest.getSubject(),
					            		subjectCreateRequest.getTenant(),
					            		subjectCreateRequest.getNamespace(),                        
					            		subjectCreateRequest.getApp(),
					            		subjectCreateRequest.getDescription(),
					            		subjectCreateRequest.getStatus(),
					            		subjectCreateRequest.getCompatibility(),
					            		subjectCreateRequest.getCoordinate());
           
            return subject;
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }
    
    public void createSubjectTable() {
    	Connection connection = null;
        Statement stmt = null;
        try {
        	connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            stmt = connection.createStatement();
            
            stmt.execute("CREATE TABLE `subject_registry` (\r\n"
            		+ "  `name` varchar(255) NULL,\r\n"
            		+ "  `tenant` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `namespace` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `app` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `description` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `status` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `compatibility` varchar(255) DEFAULT NULL,\r\n"
            		+ "  `coordinate` varchar(255) DEFAULT NULL,\r\n"            		            	
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
