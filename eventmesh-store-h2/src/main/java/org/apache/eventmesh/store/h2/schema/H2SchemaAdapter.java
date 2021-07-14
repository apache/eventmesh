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

package org.apache.eventmesh.store.h2.schema;

import org.apache.eventmesh.store.h2.schema.configuration.DBConfiguration;
import org.apache.eventmesh.store.h2.schema.configuration.H2AdapterConfiguration;
import org.apache.eventmesh.store.h2.schema.repository.SchemaRepository;
import org.apache.eventmesh.store.h2.schema.repository.SubjectRepository;
import org.apache.eventmesh.store.h2.schema.util.DBDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class H2SchemaAdapter {
	
	private Logger logger = LoggerFactory.getLogger(this.getClass());
    private DBDataSource dataSource;
    
    private H2AdapterConfiguration adapterConfig;
    
    private static SubjectRepository subjectRepository;
    
    private static SchemaRepository schemaRepository;

    public synchronized void init() {    
    	//Extra initializations in case needed. 
    	adapterConfig = new H2AdapterConfiguration();
        logger.info("H2 Adapter is initialized.");
    }
    
    public H2SchemaAdapter() {}   

    public void start() {
        if (!adapterConfig.adapterEnabled) {
            logger.info("H2 Adapter is not enabled to start.");
            return;
        }
        
    	//Start H2 in-memory database and create new tables for schema registry 
        DBConfiguration dbConfig = new DBConfiguration(adapterConfig.adapterDatabaseUrl,
                adapterConfig.adapterDatabaseUsername, adapterConfig.adapterDatabasePassword,
                adapterConfig.adapterDatabaseMaxIdle, adapterConfig.adapterDatabaseMinIdle,
                adapterConfig.adapterDatabaseMaxStatements);
        dataSource = DBDataSource.createDataSource(dbConfig);
        subjectRepository = SubjectRepository.createInstance(dataSource);
        subjectRepository.createSubjectTable();
        schemaRepository = SchemaRepository.createInstance(dataSource);
        schemaRepository.createSchemaTable();
        logger.info("H2 Adapter is started.");
    }

    public void shutdown() throws Exception {
        if (!adapterConfig.adapterEnabled) {
            return;
        }
        if (dataSource != null) {
            dataSource.close();
        }
        logger.info("H2 Adapter is shut down.");
    }

    public boolean isAdapterEnabled() {
        return adapterConfig.adapterEnabled;
    }

    public static SchemaRepository getSchemaRepository() {
        return schemaRepository;
    }
    
    public static SubjectRepository getSubjectRepository() {
        return subjectRepository;
    }
    
}
