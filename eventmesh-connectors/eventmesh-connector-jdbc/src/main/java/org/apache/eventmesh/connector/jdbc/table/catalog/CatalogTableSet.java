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

package org.apache.eventmesh.connector.jdbc.table.catalog;

import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CatalogTableSet {

    private final TableIdSet tableIdSet;

    private final TableSchemaMap tableSchemaMap;

    public CatalogTableSet() {

        this.tableIdSet = new TableIdSet();
        this.tableSchemaMap = new TableSchemaMap();

    }

    public void removeDatabase(String catalogName) {
        removeDatabase(catalogName, null);
    }

    public void removeDatabase(String catalogName, String schemaName) {
        tableSchemaMap.removeDatabase(catalogName, schemaName);
        tableIdSet.removeDatabase(catalogName, schemaName);
    }

    public void overrideTable(TableSchema tableSchema) {
        if (tableSchema == null || tableSchema.getTableId() == null) {
            return;
        }
        tableSchemaMap.putTableSchema(tableSchema);
    }

    public TableSchema getTableSchema(TableId tableId) {
        return tableSchemaMap.getTableSchema(tableId);
    }

    private static class TableIdSet {

        private final Set<TableId> values;

        public TableIdSet() {
            values = new HashSet<>(32);
        }

        public void removeDatabase(String catalogName, String schemaName) {
            values.removeIf(
                entry -> StringUtils.equals(entry.getCatalogName(), catalogName) && StringUtils.equals(entry.getSchemaName(), schemaName));
        }
    }

    private static class TableSchemaMap {

        private final ConcurrentMap<TableId, TableSchema> values;

        public TableSchemaMap() {
            this.values = new ConcurrentHashMap<>(32);
        }

        public void removeDatabase(String catalogName, String schemaName) {
            values.entrySet().removeIf(entry -> {
                TableId key = entry.getKey();
                return StringUtils.equals(key.getCatalogName(), catalogName) && StringUtils.equals(key.getSchemaName(), schemaName);
            });
        }

        public TableSchema getTableSchema(TableId tableId) {
            return values.get(tableId);
        }

        public TableSchema putTableSchema(TableSchema tableSchema) {
            return values.put(tableSchema.getTableId(), tableSchema);
        }
    }
}
