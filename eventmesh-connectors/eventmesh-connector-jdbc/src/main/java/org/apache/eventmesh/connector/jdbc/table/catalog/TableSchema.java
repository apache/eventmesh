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

import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlColumn;
import org.apache.eventmesh.connector.jdbc.table.catalog.mysql.MysqlTableSchema;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TableSchema implements Serializable {

    private TableId tableId = new TableId();

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
        this.tableId.setTableName(name);
    }

    public TableSchema(TableId tableId) {
        this.tableId = tableId;
    }

    public String getSimpleName() {
        return this.tableId.getTableName();
    }

    public static TableSchemaBuilder newTableSchemaBuilder() {
        return new TableSchemaBuilder();
    }

    public static class TableSchemaBuilder {

        private TableId tableId;
        private Map<String, Column> columnMap;
        private List<Column> columns;
        private PrimaryKey primaryKey;
        private List<UniqueKey> uniqueKeys;
        private String comment;

        public TableSchemaBuilder() {

        }

        public TableSchemaBuilder withTableId(TableId tableId) {
            this.tableId = tableId;
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
            return new TableSchema(tableId, columnMap, columns, primaryKey, uniqueKeys, comment);
        }

    }

    public static MysqlTableSchemaBuilder newMysqlTableSchemaBuilder() {
        return new MysqlTableSchemaBuilder();
    }

    public static class MysqlTableSchemaBuilder {

        private TableId tableId = new TableId();
        private Map<String, MysqlColumn> columnMap;
        private List<MysqlColumn> columns;
        private PrimaryKey primaryKey;
        private List<UniqueKey> uniqueKeys;
        private String comment;
        private Options tableOptions = new Options();

        public MysqlTableSchemaBuilder() {

        }

        public MysqlTableSchemaBuilder withName(String name) {
            this.tableId.setTableName(name);
            return this;
        }

        public MysqlTableSchemaBuilder withTableId(TableId tableId) {
            this.tableId = tableId;
            return this;
        }

        public MysqlTableSchemaBuilder withColumns(List<MysqlColumn> columns) {
            this.columns = columns;
            this.columnMap = Optional.ofNullable(columns).orElse(new ArrayList<>(0)).stream()
                    .collect(Collectors.toMap(MysqlColumn::getName, Function.identity()));
            return this;
        }

        public MysqlTableSchemaBuilder withPrimaryKey(PrimaryKey primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public MysqlTableSchemaBuilder withUniqueKeys(List<UniqueKey> uniqueKeys) {
            this.uniqueKeys = uniqueKeys;
            return this;
        }

        public MysqlTableSchemaBuilder withComment(String comment) {
            this.comment = comment;
            return this;
        }

        public MysqlTableSchemaBuilder withOption(String key, Object value) {
            this.tableOptions.put(key, value);
            return this;
        }

        public MysqlTableSchemaBuilder withOptions(Options options) {
            this.tableOptions.putAll(options);
            return this;
        }

        public MysqlTableSchema build() {
            return new MysqlTableSchema(tableId, columnMap, columns, primaryKey, uniqueKeys, comment);
        }

    }
}
