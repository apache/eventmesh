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
import java.sql.Types;

/**
 * Interface for editing column properties.
 */
public interface ColumnEditor {

    /**
     * Sets the name of the column.
     *
     * @param name The name of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor withName(String name);

    /**
     * Sets the type of the column.
     *
     * @param typeName The type of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor withType(String typeName);

    /**
     * Sets the {@link Types JDBC type} of this column.
     *
     * @param jdbcType The JDBC type of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor withJdbcType(JDBCType jdbcType);

    /**
     * Sets the event mesh type of the column.
     *
     * @param eventMeshType The event mesh type of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor withEventMeshType(EventMeshDataType<?> eventMeshType);

    /**
     * Sets the character set name of the column.
     *
     * @param charsetName The character set name of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor charsetName(String charsetName);

    /**
     * Sets the character set name of the table associated with the column.
     *
     * @param charsetName The character set name of the table.
     * @return The ColumnEditor instance.
     */
    ColumnEditor charsetNameOfTable(String charsetName);

    /**
     * Sets the length of the column.
     *
     * @param length The length of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor length(int length);

    /**
     * Sets the scale of the column.
     *
     * @param scale The scale of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor scale(Integer scale);

    /**
     * Sets whether the column is optional.
     *
     * @param optional Whether the column is optional.
     * @return The ColumnEditor instance.
     */
    ColumnEditor optional(boolean optional);

    /**
     * Sets the comment of the column.
     *
     * @param comment The comment of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor comment(String comment);

    /**
     * Sets whether the column is auto-incremented.
     *
     * @param autoIncremented Whether the column is auto-incremented.
     * @return The ColumnEditor instance.
     */
    ColumnEditor autoIncremented(boolean autoIncremented);

    /**
     * Sets whether the column is generated.
     *
     * @param generated Whether the column is generated.
     * @return The ColumnEditor instance.
     */
    ColumnEditor generated(boolean generated);

    /**
     * Sets the default value expression of the column.
     *
     * @param defaultValueExpression The default value expression of the column.
     * @return The ColumnEditor instance.
     */
    ColumnEditor defaultValueExpression(String defaultValueExpression);

    /**
     * Specifies whether the column should be marked as "NOT NULL."
     *
     * @param notNull True if the column should be marked as "NOT NULL," false otherwise.
     * @return The updated ColumnEditor.
     */
    ColumnEditor notNull(boolean notNull);


    /**
     * Builds the Column object with the edited properties.
     *
     * @return The built Column object.
     */
    Column build();
}
