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

package org.apache.eventmesh.connector.jdbc.type;

import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.Table;

import org.hibernate.dialect.Dialect;

/**
 * Interface for defining database type dialects.
 */
public interface DatabaseTypeDialect {

    String EMPTY_STRING = "";

    /**
     * Configures the given Hibernate dialect.
     *
     * @param hibernateDialect The Hibernate dialect to be configured.
     */
    void configure(Dialect hibernateDialect);

    /**
     * Gets  type for the given column.
     *
     * @param column The column for which to get the type.
     * @return The Hibernate type.
     */
    Type getType(Column<?> column);

    /**
     * Gets the type name for the given column and Hibernate dialect.
     *
     * @param hibernateDialect The Hibernate dialect.
     * @param column           The column for which to get the type name.
     * @return The type name.
     */
    String getTypeName(Dialect hibernateDialect, Column<?> column);

    /**
     * Gets a formatted string for a boolean value.
     *
     * @param value The boolean value.
     * @return The formatted string.
     */
    default String getBooleanFormatted(boolean value) {
        return value ? Boolean.TRUE.toString() : Boolean.FALSE.toString();
    }

    /**
     * Gets a formatted string for an auto-incrementing column.
     *
     * @param column The auto-incrementing column.
     * @return The formatted string.
     */
    default String getAutoIncrementFormatted(Column<?> column) {
        return "";
    }

    /**
     * Gets a formatted string for the default value of a column.
     *
     * @param column The column.
     * @return The formatted string.
     */
    default String getDefaultValueFormatted(Column<?> column) {
        return "";
    }

    /**
     * Gets a formatted string for the character set or collation of a column.
     *
     * @param column The column.
     * @return The formatted string.
     */
    default String getCharsetOrCollateFormatted(Column<?> column) {
        return "";
    }

    /**
     * Gets a formatted string for table options.
     *
     * @param table The table.
     * @return The formatted string.
     */
    default String getTableOptionsFormatted(Table table) {
        return "";
    }

    /**
     * Gets a formatted string for query binding with a value cast.
     *
     * @param column The column.
     * @return The formatted string.
     */
    default String getQueryBindingWithValueCast(Column<?> column) {
        return "?";
    }

    /**
     * Gets a formatted string for the comment of a column.
     *
     * @param column The column.
     * @return The formatted string.
     */
    default String getCommentFormatted(Column<?> column) {
        return "";
    }
}
