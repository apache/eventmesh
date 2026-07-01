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

package org.apache.eventmesh.connector.jdbc.source;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialectFactory;
import org.apache.eventmesh.connector.jdbc.source.dialect.cdc.CdcEngineFactory;
import org.apache.eventmesh.connector.jdbc.source.dialect.snapshot.SnapshotEngineFactory;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Objects;

import lombok.experimental.UtilityClass;

/**
 * Get a CdcEngineFactory for a given database name
 */
@UtilityClass
public class JdbcAllFactoryLoader {

    /**
     * Returns a CdcEngineFactory for the given database name.
     * <p>Throws NullPointerException if databaseName is null.</p>
     * <p>Throws IllegalArgumentException if CdcEngineFactory is not supported for the given database name.</p>
     *
     * @param databaseName Name of the database for which CdcEngineFactory is required.
     * @return CdcEngineFactory for the given database name.
     */
    public static CdcEngineFactory getCdcEngineFactory(String databaseName) {
        checkNotNull(databaseName, "database name can not be null");
        CdcEngineFactory engineFactory = EventMeshExtensionFactory.getExtension(CdcEngineFactory.class, databaseName);
        return checkNotNull(engineFactory, "CdcEngineFactory: " + databaseName + " is not supported");
    }

    /**
     * Returns a DatabaseDialectFactory based on the specified database name.
     *
     * @param databaseName the name of the database
     * @return the DatabaseDialectFactory for the specified database name
     * @throws NullPointerException     if the database name is null
     * @throws IllegalArgumentException if the specified database name is not supported
     */
    public static DatabaseDialectFactory getDatabaseDialectFactory(String databaseName) {
        Objects.requireNonNull(databaseName, "database name can not be null");
        DatabaseDialectFactory databaseDialectFactory = EventMeshExtensionFactory.getExtension(DatabaseDialectFactory.class, databaseName);
        Objects.requireNonNull(databaseDialectFactory, "DatabaseDialectFactory: " + databaseName + " is not supported");
        return databaseDialectFactory;
    }

    public static SnapshotEngineFactory getSnapshotEngineFactory(String databaseName) {
        Objects.requireNonNull(databaseName, "database name can not be null");
        SnapshotEngineFactory databaseDialectFactory = EventMeshExtensionFactory.getExtension(SnapshotEngineFactory.class, databaseName);
        Objects.requireNonNull(databaseDialectFactory, "SnapshotEngineFactory: " + databaseName + " is not supported");
        return databaseDialectFactory;
    }
}
