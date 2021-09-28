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

package org.apache.eventmesh.schema.registry.openschema.server;

import org.apache.eventmesh.api.schema.registry.server.SchemaRegistryServerService;
import org.apache.eventmesh.schema.registry.domain.SchemaId;
import org.apache.eventmesh.schema.registry.domain.SchemaWithSubjectName;

public class SchemaRegistryServerServiceImpl implements SchemaRegistryServerService {
    @Override
    public void init(String address) throws Exception {

    }

    @Override
    public void start() throws Exception {

    }

    @Override
    public SchemaWithSubjectName fetchSchemaByTopic(String topic) throws Exception {
        return null;
    }

    @Override
    public SchemaId registerSchemaByTopic(String topic, org.apache.eventmesh.schema.registry.schema.Schema schema) throws Exception {
        return null;
    }
}
