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

package org.apache.eventmesh.api.schema.registry.server;

import org.apache.eventmesh.schema.registry.domain.SchemaId;
import org.apache.eventmesh.schema.registry.domain.SchemaWithSubjectName;
import org.apache.eventmesh.schema.registry.schema.Schema;
import org.apache.eventmesh.spi.EventMeshSPI;

@EventMeshSPI(isSingleton = true)
public interface SchemaRegistryServerService {
    /**
     * Initialize schema registry server
     *
     * @param address the ip:port address of a registry server
     * @throws Exception
     */
    void init(String address) throws Exception;

    /**
     * start SchemaRegistryServerService
     * @throws Exception
     */
    void start() throws Exception;

    /**
     * Fetch the latest schema by topic(subject)
     * @param topic subject
     * @return SchemaWithSubjectName
     */
    SchemaWithSubjectName fetchSchemaByTopic(String topic) throws Exception;

    /**
     * Register a new schema by topic(subject)
     * @param topic subject
     * @param schema schema for a topic
     * @return the id of newly added schema
     */
    SchemaId registerSchemaByTopic(String topic, Schema schema) throws Exception;
}
