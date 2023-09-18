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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractTableEditorImpl<TE extends TableEditor, Col extends Column, TB extends TableSchema>
        implements
            TableEditor<TE, Col, TB> {

    private TableId tableId;

    private PrimaryKey primaryKey = new PrimaryKey();

    private List<UniqueKey> uniqueKeys = new ArrayList<>(8);

    private Map<String, Col> columns = new HashMap<>(16);

    private String comment;

    private Options options = new Options();

    public AbstractTableEditorImpl(TableId tableId) {
        this.tableId = tableId;
    }

    public AbstractTableEditorImpl() {
    }

    /**
     * Sets the unique identifier (ID) of the table.
     *
     * @param tableId The unique ID of the table.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withTableId(TableId tableId) {
        this.tableId = tableId;
        return (TE) this;
    }

    /**
     * Adds columns to the table.
     *
     * @param columns The columns to add to the table.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE addColumns(Col... columns) {
        if (columns != null && columns.length > 0) {
            for (Col column : columns) {
                this.columns.put(column.getName(), column);
            }
        }
        return (TE) this;
    }

    /**
     * Sets a comment or description for the table.
     *
     * @param comment A comment or description for the table.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withComment(String comment) {
        this.comment = comment;
        return (TE) this;
    }

    /**
     * Removes a column from the table.
     *
     * @param columnName The name of the column to remove.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE removeColumn(String columnName) {
        this.columns.remove(columnName);
        return (TE) this;
    }

    /**
     * Sets the primary key columns for the table.
     *
     * @param pkColumnNames The names of the columns that form the primary key.
     * @param comment       A comment or description for the primary key.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withPrimaryKeyNames(List<String> pkColumnNames, String comment) {
        this.primaryKey = new PrimaryKey(pkColumnNames, comment);
        return (TE) this;
    }

    /**
     * Sets the primary key columns for the table.
     *
     * @param pkColumnNames The names of the columns that form the primary key.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withPrimaryKeyNames(String... pkColumnNames) {
        if (pkColumnNames != null && pkColumnNames.length > 0) {
            primaryKey.addColumnNames(pkColumnNames);
        }
        return (TE) this;
    }

    /**
     * Sets the primary key for the table.
     *
     * @param primaryKey The primary key definition.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withPrimaryKey(PrimaryKey primaryKey) {
        this.primaryKey = primaryKey;
        return (TE) this;
    }

    /**
     * Sets the unique key columns and their names for the table.
     *
     * @param ukName        The name of the unique key constraint.
     * @param ukColumnNames The names of the columns that form the unique key.
     * @param comment       A comment or description for the unique key.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withUniqueKeyColumnsNames(String ukName, List<String> ukColumnNames, String comment) {
        this.uniqueKeys.add(new UniqueKey(ukName, ukColumnNames, comment));
        return (TE) this;
    }

    /**
     * Sets the unique key constraints for the table.
     *
     * @param uniqueKeys The unique key constraints.
     * @return A reference to the table editor for further configuration.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withUniqueKeys(UniqueKey... uniqueKeys) {
        if (uniqueKeys != null && uniqueKeys.length > 0) {
            for (UniqueKey uniqueKey : uniqueKeys) {
                this.uniqueKeys.add(uniqueKey);
            }
        }
        return (TE) this;
    }

    /**
     * @param key
     * @param value
     * @return
     */
    @Override
    @SuppressWarnings("unchecked")
    public TE withOption(String key, Object value) {
        this.options.put(key, value);
        return (TE) this;
    }

    protected TableId ofTableId() {
        return tableId;
    }

    protected PrimaryKey ofPrimaryKey() {
        return primaryKey;
    }

    protected List<UniqueKey> ofUniqueKeys() {
        return uniqueKeys;
    }

    protected Map<String, Col> ofColumns() {
        return columns;
    }

    protected String ofComment() {
        return comment;
    }

    protected Options ofOptions() {
        return options;
    }
}
