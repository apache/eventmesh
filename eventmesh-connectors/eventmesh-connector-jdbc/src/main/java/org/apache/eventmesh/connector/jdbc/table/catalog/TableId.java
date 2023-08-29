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
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a table identifier with catalog name, schema name and table name.
 */
@Getter
@Setter
public class TableId implements Serializable {

    /**
     * The default mapper that converts a TableId to its string representation.
     */
    public static final TableIdToStringMapper DEFAULT_TABLEIDTOSTRINGMAPPER = new DefaultTableIdToStringMapper();

    private String catalogName;

    private String schemaName;

    private String tableName;

    private String id;

    public TableId() {
    }

    /**
     * Constructs a TableId instance without a TableIdToStringMapper.
     *
     * @param catalogName the catalog name of the table
     * @param schemaName  the schema name of the table
     * @param tableName   the name of the table
     */
    public TableId(String catalogName, String schemaName, String tableName) {
        this(catalogName, schemaName, tableName, null);
    }

    /**
     * Constructs a TableId instance without a TableIdToStringMapper.
     *
     * @param catalogName the catalog name of the table
     */
    public TableId(String catalogName) {
        this(catalogName, null, null, null);
    }

    /**
     * Constructs a TableId instance with a TableIdToStringMapper. If the mapper is null, the default mapper will be used.
     *
     * @param catalogName the catalog name of the table
     * @param schemaName  the schema name of the table
     * @param tableName   the name of the table
     * @param mapper      the mapper that converts a TableId to its string representation
     */
    public TableId(String catalogName, String schemaName, String tableName, TableIdToStringMapper mapper) {
        this.catalogName = catalogName;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.id = mapper == null ? DEFAULT_TABLEIDTOSTRINGMAPPER.toString(this) : mapper.toString(this);

    }

    @Override
    public String toString() {
        return id == null ? DEFAULT_TABLEIDTOSTRINGMAPPER.toString(this) : id;
    }

    /**
     * Returns the string representation of the TableId, which is the same as calling toString().
     *
     * @return the string representation of the TableId
     */
    public String tablePath() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TableId)) {
            return false;
        }
        TableId tableId = (TableId) o;
        return Objects.equals(getCatalogName(), tableId.getCatalogName()) && Objects.equals(getSchemaName(), tableId.getSchemaName())
            && Objects.equals(getTableName(), tableId.getTableName()) && Objects.equals(getId(), tableId.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCatalogName(), getSchemaName(), getTableName(), getId());
    }

    /**
     * A functional interface that converts a TableId to its string representation.
     */
    @FunctionalInterface
    public interface TableIdToStringMapper {

        String toString(TableId tableId);
    }

    /**
     * Returns the string representation of a TableId. If catalog or schema is null or empty, they will be excluded from the string.
     *
     * @param catalog the catalog name of the table
     * @param schema  the schema name of the table
     * @param table   the name of the table
     * @return the string representation of the TableId
     */
    private static String tableId(String catalog, String schema, String table) {
        if (catalog == null || catalog.length() == 0) {
            if (schema == null || schema.length() == 0) {
                return table;
            }
            return schema + "." + table;
        }
        if (schema == null || schema.length() == 0) {
            return catalog + "." + table;
        }
        return catalog + "." + schema + "." + table;
    }

    /**
     * The default mapper that converts a TableId to its string representation.
     */
    private static class DefaultTableIdToStringMapper implements TableIdToStringMapper {

        public String toString(TableId tableId) {
            return tableId(tableId.getCatalogName(), tableId.getSchemaName(), tableId.getTableName());
        }
    }
}
