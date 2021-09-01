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

package org.apache.eventmesh.schema.rest.controller;

import java.io.IOException;

import com.sun.net.httpserver.HttpServer;

import org.apache.eventmesh.schema.rest.handler.SubjectsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaRegistryController {

    private static final Logger logger = LoggerFactory.getLogger(SchemaRegistryController.class);

    public SchemaRegistryController() {}

    public void run(HttpServer server) throws IOException {
                
        server.createContext("/schemaregistry/subjects", new SubjectsHandler());
        
        //server.createContext("/schemaregistry/schemas", new SchemasHandler());
        
        //server.createContext("/schemaregistry/compatibility/subjects", new CompatibilityHandler());
        
        //server.createContext("/schemaregistry/config", new ConfigHandler());
        
        logger.info("EventMesn-REST Controller server context created successfully");
    }
}
