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

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TableSchema implements Serializable {

    private String name;

    /**
     * A map of column names to their respective column objects.
     */
    private Map<String, ? extends Column> columnMap;

    /**
     * A list of columns in the table.
     */
    private List<? extends Column> columns;

    /**
     * The primary key of the table.
     */
    private PrimaryKey primaryKey;

    private List<UniqueKey> uniqueKeys;

    private String comment;

    public TableSchema(String name) {
        this.name = name;
    }

    public static TableSchemaBuilder newTableSchemaBuilder() {
        return new TableSchemaBuilder();
    }

    public static class TableSchemaBuilder {

        private String name;
        private Map<String, Column> columnMap;
        private List<Column> columns;
        private PrimaryKey primaryKey;
        private List<UniqueKey> uniqueKeys;
        private String comment;

        public TableSchemaBuilder() {

        }

        public TableSchemaBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public TableSchemaBuilder withColumns(Map<String, Column> columnMap) {
            this.columnMap = columnMap;
            return this;
        }

        public TableSchemaBuilder withColumns(List<Column> columns) {
            this.columns = columns;
            return this;
        }

        public TableSchemaBuilder withPrimaryKey(PrimaryKey primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public TableSchemaBuilder withUniqueKeys(List<UniqueKey> uniqueKeys) {
            this.uniqueKeys = uniqueKeys;
            return this;
        }

        public TableSchemaBuilder withComment(String comment) {
            this.comment = comment;
            return this;
        }

        public TableSchema build() {
            return new TableSchema(name, columnMap, columns, primaryKey, uniqueKeys, comment);
        }

    }
}
