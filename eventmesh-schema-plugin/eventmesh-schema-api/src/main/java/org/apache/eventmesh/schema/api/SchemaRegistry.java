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

import org.apache.eventmesh.schema.api.domain.Schema;
import org.apache.eventmesh.schema.api.domain.SchemaAgenda;
import org.apache.eventmesh.schema.api.domain.Subject;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.SCHEMA)
public interface SchemaRegistry {
    /**
     * start the schema registry
     */
    void start();

    /**
     * shutdown the schema registry
     */
    void shutdown();

    /**
     * register subject to schema registry
     *
     * @param subject the subject to be created
     */
    void registerSubject(Subject subject);

    /**
     * register schema to schema registry by subject
     *
     * @param subjectName name of the subject to be added a schema
     * @param schema      the schema to be added
     * @return the id of schema
     */
    int registerSchemaBySubject(String subjectName, Schema schema);

    /**
     * retrieve the latest schema by subject name
     *
     * @param subjectName the subject name
     * @return the latest schema
     */
    Schema retrieveLatestSchemaBySubject(String subjectName);

    /**
     * retrieve the schema by its id
     *
     * @param id the id of schema
     * @return the schema
     */
    Schema retrieveSchemaByID(int id);

    /**
     * retrieve the schema by subject and its version
     *
     * @param subjectName the name of the subject of desired schema
     * @param version     the version of desired schema
     * @return the schema
     */
    Schema retrieveSchemaBySubjectAndVersion(String subjectName, int version);

    /**
     * check the validity of a content according to schema
     * @param schemaAgenda a wrapper of content, content type, and schema
     * @return whether the content is valid
     */
    boolean checkSchemaValidity(SchemaAgenda schemaAgenda);
}
