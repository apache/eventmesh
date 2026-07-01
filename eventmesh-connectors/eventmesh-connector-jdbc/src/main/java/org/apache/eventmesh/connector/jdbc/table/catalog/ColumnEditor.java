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

import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;

import java.sql.JDBCType;
import java.util.List;

/**
 * An interface for building and configuring columns in a database table.
 *
 * @param <CE>  The concrete type of the column editor.
 * @param <Col> The concrete type of the column being edited.
 */
public interface ColumnEditor<CE extends ColumnEditor, Col extends Column> {

    /**
     * Sets the name of the column.
     *
     * @param name The name of the column.
     * @return The column editor instance.
     */
    CE withName(String name);

    /**
     * Retrieves the name associated with this column editor.
     *
     * @return The name of the column.
     */
    String ofName();

    /**
     * Sets the data type of the column.
     *
     * @param typeName The data type name of the column.
     * @return The column editor instance.
     */
    CE withType(String typeName);

    /**
     * Sets the JDBC data type of the column.
     *
     * @param jdbcType The JDBC data type of the column.
     * @return The column editor instance.
     */
    CE withJdbcType(JDBCType jdbcType);

    /**
     * Sets the EventMesh data type of the column.
     *
     * @param eventMeshType The EventMesh data type of the column.
     * @return The column editor instance.
     */
    CE withEventMeshType(EventMeshDataType eventMeshType);

    /**
     * Sets the order or position of the column within a table.
     *
     * @param order The order or position of the column.
     * @return The column editor instance.
     */
    CE withOrder(int order);

    /**
     * Sets the length of the column (if applicable).
     *
     * @param length The length of the column.
     * @return The column editor instance.
     */
    CE length(long length);

    /**
     * Sets the scale of the column (if applicable).
     *
     * @param scale The scale of the column.
     * @return The column editor instance.
     */
    CE scale(Integer scale);

    /**
     * Sets whether the column is optional (nullable).
     *
     * @param optional Indicates whether the column is optional (true) or not (false).
     * @return The column editor instance.
     */
    CE optional(boolean optional);

    /**
     * Sets the comment for the column.
     *
     * @param comment The comment for the column.
     * @return The column editor instance.
     */
    CE comment(String comment);

    /**
     * Sets the default value expression for the column.
     *
     * @param defaultValueExpression The default value expression for the column.
     * @return The column editor instance.
     */
    CE defaultValueExpression(String defaultValueExpression);

    /**
     * Sets the default value for the column.
     *
     * @param value The default value for the column.
     * @return The column editor instance.
     */
    CE defaultValue(Object value);

    /**
     * Sets whether the column is marked as not null.
     *
     * @param notNull Indicates whether the column is marked as not null (true) or not (false).
     * @return The column editor instance.
     */
    CE notNull(boolean notNull);

    CE charsetName(String charsetName);

    CE enumValues(List<String> enumValues);

    CE withOption(String key, Object value);

    CE withOptions(Options options);

    /**
     * Builds and returns the configured column.
     *
     * @return The configured column.
     */
    Col build();

}
