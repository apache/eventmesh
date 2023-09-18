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


import java.util.List;

/**
 * An interface for editing and configuring properties of a database table schema.
 *
 * @param <TE>  The specific type of table editor.
 * @param <Col> The type of columns in the table.
 * @param <TB>  The type of the resulting table schema.
 */
public interface TableEditor<TE extends TableEditor, Col extends Column, TB extends TableSchema> {

    /**
     * Sets the unique identifier (ID) of the table.
     *
     * @param tableId The unique ID of the table.
     * @return A reference to the table editor for further configuration.
     */
    TE withTableId(TableId tableId);

    /**
     * Adds columns to the table.
     *
     * @param columns The columns to add to the table.
     * @return A reference to the table editor for further configuration.
     */
    @SuppressWarnings("unchecked")
    TE addColumns(Col... columns);

    /**
     * Sets a comment or description for the table.
     *
     * @param comment A comment or description for the table.
     * @return A reference to the table editor for further configuration.
     */
    TE withComment(String comment);

    /**
     * Removes a column from the table.
     *
     * @param columnName The name of the column to remove.
     * @return A reference to the table editor for further configuration.
     */
    TE removeColumn(String columnName);

    /**
     * Sets the primary key columns for the table.
     *
     * @param pkColumnNames The names of the columns that form the primary key.
     * @param comment       A comment or description for the primary key.
     * @return A reference to the table editor for further configuration.
     */
    TE withPrimaryKeyNames(List<String> pkColumnNames, String comment);

    /**
     * Sets the primary key columns for the table.
     *
     * @param pkColumnNames The names of the columns that form the primary key.
     * @return A reference to the table editor for further configuration.
     */
    TE withPrimaryKeyNames(String... pkColumnNames);


    /**
     * Sets the primary key for the table.
     *
     * @param primaryKey The primary key definition.
     * @return A reference to the table editor for further configuration.
     */
    TE withPrimaryKey(PrimaryKey primaryKey);

    /**
     * Sets the unique key columns and their names for the table.
     *
     * @param ukName        The name of the unique key constraint.
     * @param ukColumnNames The names of the columns that form the unique key.
     * @param comment       A comment or description for the unique key.
     * @return A reference to the table editor for further configuration.
     */
    TE withUniqueKeyColumnsNames(String ukName, List<String> ukColumnNames, String comment);

    /**
     * Sets the unique key constraints for the table.
     *
     * @param uniqueKeys The unique key constraints.
     * @return A reference to the table editor for further configuration.
     */
    TE withUniqueKeys(UniqueKey... uniqueKeys);

    TE withOption(String key, Object value);

    /**
     * Builds and returns the table schema with the configured properties.
     *
     * @return The resulting table schema.
     */
    TB build();
}


