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

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class DefaultColumn extends Column {

    public DefaultColumn(String name, EventMeshDataType<?> dataType, JDBCType jdbcType, Integer columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression) {
        super(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression);
    }

    public static DefaultColumn of(
        String name, EventMeshDataType<?> dataType, JDBCType jdbcType, Integer columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression) {
        return new DefaultColumn(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression);
    }

    /**
     * creates a clone of the Column
     *
     * @return clone of column
     */
    @Override
    public Column clone() {
        return DefaultColumn.of(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression);
    }
}
