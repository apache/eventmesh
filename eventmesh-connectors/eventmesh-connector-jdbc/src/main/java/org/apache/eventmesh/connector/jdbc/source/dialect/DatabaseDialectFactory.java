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

package org.apache.eventmesh.connector.jdbc.source.dialect;

import org.apache.eventmesh.connector.jdbc.DatabaseDialect;
import org.apache.eventmesh.openconnect.api.config.SourceConfig;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * Interface for creating a database dialect based on the provided source configuration.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.JDBC_DATABASE_DIALECT)
public interface DatabaseDialectFactory {

    /**
     * Creates a database dialect based on the provided source configuration.
     *
     * @param config the source configuration to create a database dialect for
     * @return the created database dialect
     */
    DatabaseDialect createDatabaseDialect(SourceConfig config);

}
