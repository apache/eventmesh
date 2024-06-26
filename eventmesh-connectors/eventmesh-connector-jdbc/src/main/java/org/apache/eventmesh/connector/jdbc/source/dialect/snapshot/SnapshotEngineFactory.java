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

package org.apache.eventmesh.connector.jdbc.source.dialect.snapshot;

import org.apache.eventmesh.common.config.connector.rdb.jdbc.JdbcSourceConfig;
import org.apache.eventmesh.connector.jdbc.JdbcContext;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

/**
 * The SnapshotEngineFactory interface represents a factory for creating snapshot engines. It provides a method to create a snapshot engine based on
 * the given configuration and database dialect.
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.JDBC_SNAPSHOT_ENGINE)
public interface SnapshotEngineFactory {

    /**
     * Creates a snapshot engine with the specified JDBC source configuration and database dialect.
     *
     * @param jdbcSourceConfig The JDBC source configuration.
     * @param databaseDialect  The database dialect.
     * @return A snapshot engine that can perform snapshot operations.
     */
    SnapshotEngine<? extends JdbcContext> createSnapshotEngine(final JdbcSourceConfig jdbcSourceConfig,
        final DatabaseDialect databaseDialect);
}
