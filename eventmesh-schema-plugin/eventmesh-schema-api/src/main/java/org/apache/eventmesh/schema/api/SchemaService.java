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

package org.apache.eventmesh.schema.api;

import org.apache.eventmesh.common.schema.SchemaAgenda;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * Schema services should be implemented in plugins.
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.SCHEMA)
public interface SchemaService {

    /**
     * contract
     */
    void contract();

    /**
     * serialization according to schema
     */
    void serialize();

    /**
     * deserialization from string according to schema
     */
    void deserialize();

    /**
     * check the validity of a content according to schema
     *
     * @param schemaAgenda a wrapper of content, content type, and schema
     * @return whether the content is valid
     */
    boolean checkSchemaValidity(SchemaAgenda schemaAgenda);
}
