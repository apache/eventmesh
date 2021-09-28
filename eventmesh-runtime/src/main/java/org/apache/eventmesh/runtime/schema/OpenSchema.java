/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.runtime.schema;

import org.apache.eventmesh.api.schema.registry.server.SchemaRegistryServerService;
import org.apache.eventmesh.api.schema.registry.validator.SchemaValidationService;
import org.apache.eventmesh.schema.registry.domain.SchemaId;
import org.apache.eventmesh.schema.registry.domain.SchemaWithSubjectName;
import org.apache.eventmesh.schema.registry.schema.Schema;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSchema {
    private static final Logger logger = LoggerFactory.getLogger(OpenSchema.class);
    private static SchemaRegistryServerService schemaRegistryServerService;
    private static SchemaValidationService schemaValidationService;

    public void init(String openschemaPluginType, String validatorPluginType, String address) throws Exception {
        schemaRegistryServerService = EventMeshExtensionFactory.getExtension(SchemaRegistryServerService.class, openschemaPluginType);
        schemaValidationService = EventMeshExtensionFactory.getExtension(SchemaValidationService.class, validatorPluginType);
        if (schemaRegistryServerService == null) {
            logger.error("can't load the schemaRegistryServerService plugin, please check.");
            throw new RuntimeException("doesn't load the schemaRegistryServerService plugin, please check.");
        }
        schemaRegistryServerService.init(address);
    }

    public void start() throws Exception{
        schemaRegistryServerService.start();
    }

    public SchemaWithSubjectName fetchSchemaByTopic(String topic) throws Exception {
        return schemaRegistryServerService.fetchSchemaByTopic(topic);
    }

    public SchemaId registerSchemaByTopic(String topic, Schema schema) throws Exception {
        return schemaRegistryServerService.registerSchemaByTopic(topic, schema);
    }

    public boolean validate(String message, Schema schema) throws Exception {
        return schemaValidationService.validate(message, schema);
    }


}
