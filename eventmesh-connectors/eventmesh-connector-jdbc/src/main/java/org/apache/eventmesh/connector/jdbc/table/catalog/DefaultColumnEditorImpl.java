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

public class DefaultColumnEditorImpl implements ColumnEditor {

    /**
     * Name of the column
     */
    private String name;

    /**
     * Data type of the column
     */
    private EventMeshDataType<?> dataType;

    /**
     * Length of the column
     */
    private Integer columnLength;

    /**
     * Decimal point of the column
     */
    private Integer decimal;

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

    private String characterSet;

    private boolean optional;

    private boolean autoIncremented;

    private boolean generated;

    public DefaultColumnEditorImpl(String name) {
        this.name = name;
    }

    public DefaultColumnEditorImpl() {
    }

    /**
     * Sets the name of the column.
     *
     * @param name The name of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor withName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Sets the type of the column.
     *
     * @param typeName The type of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor withType(String typeName) {
        this.typeName = typeName;
        return this;
    }

    /**
     * Sets the JDBC type of the column.
     *
     * @param jdbcType The JDBC type of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor withJdbcType(JDBCType jdbcType) {
        this.jdbcType = jdbcType;
        return this;
    }

    /**
     * Sets the event mesh type of the column.
     *
     * @param eventMeshType The event mesh type of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor withEventMeshType(EventMeshDataType<?> eventMeshType) {
        this.dataType = eventMeshType;
        return this;
    }

    /**
     * Sets the character set name of the column.
     *
     * @param charsetName The character set name of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor charsetName(String charsetName) {
        this.characterSet = charsetName;
        return this;
    }

    /**
     * Sets the character set name of the table associated with the column.
     *
     * @param charsetName The character set name of the table.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor charsetNameOfTable(String charsetName) {
        return this;
    }

    /**
     * Sets the length of the column.
     *
     * @param length The length of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor length(int length) {
        this.columnLength = length;
        return this;
    }

    /**
     * Sets the scale of the column.
     *
     * @param scale The scale of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor scale(Integer scale) {
        this.decimal = scale;
        return this;
    }

    /**
     * Sets whether the column is optional.
     *
     * @param optional Whether the column is optional.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    /**
     * Sets the comment of the column.
     *
     * @param comment The comment of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor comment(String comment) {
        this.comment = comment;
        return this;
    }

    /**
     * Sets whether the column is auto-incremented.
     *
     * @param autoIncremented Whether the column is auto-incremented.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor autoIncremented(boolean autoIncremented) {
        this.autoIncremented = autoIncremented;
        return this;
    }

    /**
     * Sets whether the column is generated.
     *
     * @param generated Whether the column is generated.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor generated(boolean generated) {
        this.generated = generated;
        return this;
    }

    /**
     * Sets the default value expression of the column.
     *
     * @param defaultValueExpression The default value expression of the column.
     * @return The ColumnEditor instance.
     */
    @Override
    public ColumnEditor defaultValueExpression(String defaultValueExpression) {
        this.defaultValueExpression = defaultValueExpression;
        return this;
    }

    /**
     * Specifies whether the column should be marked as "NOT NULL."
     *
     * @param notNull True if the column should be marked as "NOT NULL," false otherwise.
     * @return The updated ColumnEditor.
     */
    @Override
    public ColumnEditor notNull(boolean notNull) {
        this.notNull = notNull;
        return this;
    }

    /**
     * Builds the Column object with the edited properties.
     *
     * @return The built Column object.
     */
    @Override
    public Column build() {
        return DefaultColumn.of(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression);
    }
}
