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

package org.apache.eventmesh.connector.jdbc.table.catalog.mysql;

import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.Options;
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;

import java.sql.JDBCType;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents a MySQL column in a database table.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MysqlColumn extends Column<MysqlColumn> {

    public MysqlColumn(String name, EventMeshDataType dataType, JDBCType jdbcType, Long columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName,
        String charsetName, List<String> enumValues, String nativeType, Options options) {
        super(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression, 0, charsetName,
            autoIncremented, generated, collationName, enumValues, nativeType, options);
    }

    public MysqlColumn(String name, EventMeshDataType dataType, JDBCType jdbcType, Long columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName,
        int order, String charsetName, List<String> enumValues, String nativeType, Options options) {
        super(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression, order, charsetName,
            autoIncremented, generated, collationName, enumValues, nativeType, options);
    }

    public MysqlColumn() {

    }

    public static MysqlColumn of(
        String name, EventMeshDataType dataType, JDBCType jdbcType, Long columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName,
        String charsetName, List<String> enumValues, String nativeType, Options options) {
        return new MysqlColumn(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression,
            autoIncremented, generated, collationName, charsetName, enumValues, nativeType, options);
    }

    public static MysqlColumn of(
        String name, EventMeshDataType dataType, JDBCType jdbcType, Long columnLength, Integer decimal, boolean notNull, String comment,
        Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName, int order,
        String charsetName, List<String> enumValues, String nativeType, Options options) {
        return new MysqlColumn(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression,
            autoIncremented, generated, collationName, order, charsetName, enumValues, nativeType, options);
    }

    /**
     * creates a clone of the Column
     *
     * @return clone of column
     */
    @Override
    public MysqlColumn clone() {
        return null;
    }
}
