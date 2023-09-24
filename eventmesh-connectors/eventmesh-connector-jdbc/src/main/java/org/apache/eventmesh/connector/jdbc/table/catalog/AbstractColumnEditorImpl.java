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

public abstract class AbstractColumnEditorImpl<CE extends ColumnEditor, Col extends Column> implements ColumnEditor<CE, Col> {

    /**
     * Name of the column
     */
    private String name;

    /**
     * Data type of the column
     */
    private EventMeshDataType<?> eventMeshDataType;

    /**
     * Length of the column
     */
    private Integer columnLength;

    /**
     * Decimal point of the column
     */
    private Integer scale;

    /**
     * Indicates if the column can be null or not
     */
    private boolean notNull;

    /**
     * Comment for the column
     */
    private String comment;

    /**
     * Default value for the column
     */
    private Object defaultValue;

    private String typeName;

    private JDBCType jdbcType;

    private String defaultValueExpression;

    private boolean optional;

    private int order;

    public AbstractColumnEditorImpl(String name) {
        this.name = name;
    }

    public AbstractColumnEditorImpl() {
    }


    /**
     * Sets the name of the column.
     *
     * @param name The name of the column.
     * @return The column editor instance.
     */
    @Override
    @SuppressWarnings("unchecked")
    public CE withName(String name) {
        this.name = name;
        return (CE) this;
    }

    /**
     * Retrieves the name associated with this column editor.
     *
     * @return The name of the column.
     */
    @Override
    public String ofName() {
        return this.name;
    }

    /**
     * Sets the data type of the column.
     *
     * @param typeName The data type name of the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE withType(String typeName) {
        this.typeName = typeName;
        return (CE) this;
    }

    /**
     * Sets the JDBC data type of the column.
     *
     * @param jdbcType The JDBC data type of the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE withJdbcType(JDBCType jdbcType) {
        this.jdbcType = jdbcType;
        return (CE) this;
    }

    /**
     * Sets the EventMesh data type of the column.
     *
     * @param eventMeshType The EventMesh data type of the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE withEventMeshType(EventMeshDataType<?> eventMeshType) {
        this.eventMeshDataType = eventMeshType;
        return (CE) this;
    }

    /**
     * Sets the order or position of the column within a table.
     *
     * @param order The order or position of the column.
     * @return The column editor instance.
     */
    @Override
    @SuppressWarnings("unchecked")
    public CE withOrder(int order) {
        this.order = order;
        return (CE) this;
    }

    /**
     * Sets the length of the column (if applicable).
     *
     * @param length The length of the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE length(int length) {
        this.columnLength = length;
        return (CE) this;
    }

    /**
     * Sets the scale of the column (if applicable).
     *
     * @param scale The scale of the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE scale(Integer scale) {
        this.scale = scale;
        return (CE) this;
    }

    /**
     * Sets whether the column is optional (nullable).
     *
     * @param optional Indicates whether the column is optional (true) or not (false).
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE optional(boolean optional) {
        this.optional = optional;
        return (CE) this;
    }

    /**
     * Sets the comment for the column.
     *
     * @param comment The comment for the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE comment(String comment) {
        this.comment = comment;
        return (CE) this;
    }

    /**
     * Sets the default value expression for the column.
     *
     * @param defaultValueExpression The default value expression for the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE defaultValueExpression(String defaultValueExpression) {
        this.defaultValueExpression = defaultValueExpression;
        return (CE) this;
    }

    /**
     * Sets the default value for the column.
     *
     * @param value The default value for the column.
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE defaultValue(Object value) {
        this.defaultValue = value;
        return (CE) this;
    }

    /**
     * Sets whether the column is marked as not null.
     *
     * @param notNull Indicates whether the column is marked as not null (true) or not (false).
     * @return The column editor instance.
     */
    @SuppressWarnings("unchecked")
    @Override
    public CE notNull(boolean notNull) {
        this.notNull = notNull;
        return (CE) this;
    }

    public EventMeshDataType<?> ofEventMeshDataType() {
        return eventMeshDataType;
    }

    public Integer ofColumnLength() {
        return columnLength;
    }

    public Integer ofScale() {
        return scale;
    }

    public boolean isNotNull() {
        return notNull;
    }

    public String ofComment() {
        return comment;
    }

    public Object ofDefaultValue() {
        return defaultValue;
    }

    public String ofTypeName() {
        return typeName;
    }

    public JDBCType ofJdbcType() {
        return jdbcType;
    }

    public String ofDefaultValueExpression() {
        return defaultValueExpression;
    }

    public boolean isOptional() {
        return optional;
    }

    public int ofOrder() {
        return this.order;
    }
}
