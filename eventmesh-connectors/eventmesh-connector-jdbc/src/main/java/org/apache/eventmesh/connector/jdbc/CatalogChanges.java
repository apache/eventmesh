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

package org.apache.eventmesh.connector.jdbc;

import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.table.catalog.CatalogSchema;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.Table;

import java.util.List;

import lombok.Data;

@Data
/**
 * Represents changes in a catalog, such as schema or table modifications.
 */
public class CatalogChanges {

    /**
     * The type of change (e.g., "T(Table)" or "D(Database)").
     * <pr>
     *     {@link SchemaChangeEventType#type}
     * </pr>
     */
    private String type;

    /**
     * The specific operation type (e.g., "C(Create)", "D(Drop)", "A(Alert)").
     * <pr>
     *     {@link SchemaChangeEventType#operationType}
     * </pr>
     */
    private String operationType;
    // The catalog schema associated with the changes
    private CatalogSchema catalog;
    // The table associated with the changes
    private Table table;
    // The list of columns affected by the changes
    private List<? extends Column> columns;

    private CatalogChanges(String type, String operationType, CatalogSchema catalog, Table table,
        List<? extends Column> columns) {
        this.type = type;
        this.operationType = operationType;
        this.catalog = catalog;
        this.table = table;
        this.columns = columns;
    }

    /**
     * Creates a new instance of the Builder for constructing CatalogChanges.
     *
     * @return The Builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builder class for constructing CatalogChanges.
     */
    public static class Builder {

        private String type;
        private String operationType;
        private CatalogSchema catalog;
        private Table table;
        private List<? extends Column> columns;

        /**
         * Sets the operation type for the change.
         *
         * @param changeEventType The SchemaChangeEventType representing the change.
         * @return The Builder instance.
         */
        public Builder operationType(SchemaChangeEventType changeEventType) {
            this.type = changeEventType.ofType();
            this.operationType = changeEventType.ofOperationType();
            return this;
        }

        /**
         * Sets the catalog schema associated with the changes.
         *
         * @param catalog The CatalogSchema instance.
         * @return The Builder instance.
         */
        public Builder catalog(CatalogSchema catalog) {
            this.catalog = catalog;
            return this;
        }

        /**
         * Sets the table associated with the changes.
         *
         * @param table The Table instance.
         * @return The Builder instance.
         */
        public Builder table(Table table) {
            this.table = table;
            return this;
        }

        /**
         * Sets the list of columns affected by the changes.
         *
         * @param columns The list of Column instances.
         * @return The Builder instance.
         */
        public Builder columns(List<? extends Column> columns) {
            this.columns = columns;
            return this;
        }

        /**
         * Builds a new CatalogChanges instance.
         *
         * @return The constructed CatalogChanges instance.
         */
        public CatalogChanges build() {
            return new CatalogChanges(type, operationType, catalog, table, columns);
        }
    }
}
