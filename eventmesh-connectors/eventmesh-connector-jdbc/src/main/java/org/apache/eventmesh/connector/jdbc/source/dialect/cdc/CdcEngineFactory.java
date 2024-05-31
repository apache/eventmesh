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

package org.apache.eventmesh.connector.jdbc.source.dialect.cdc;

import org.apache.eventmesh.common.config.connector.SourceConfig;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * This interface defines the methods required to create a Change Data Capture (CDC) engine
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.JDBC_CDC_ENGINE)
public interface CdcEngineFactory {

    /**
     * Determines whether the provided JDBC URL is compatible with the CDC engine
     *
     * @param url jdbc url, e.g. mysql: jdbc:mysql://localhost:3306/
     * @return true if the JDBC URL is compatible with the CDC engine, false otherwise
     */
    boolean acceptJdbcProtocol(String url);

    /**
     * Creates a CDC engine based on the provided source configuration and database dialect.
     *
     * @param config          the source configuration for the CDC engine
     * @param databaseDialect the database dialect for the CDC engine
     * @return the created CDC engine
     */
    CdcEngine createCdcEngine(SourceConfig config, DatabaseDialect databaseDialect);

}
