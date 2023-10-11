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
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;

import java.sql.JDBCType;

/**
 * Represents a MySQL column in a database table.
 */
public class MysqlColumn extends Column<MysqlColumn> {

    private boolean autoIncremented;

    private boolean generated;

    private String collationName;

    public MysqlColumn(String name, EventMeshDataType<?> dataType, JDBCType jdbcType, Integer columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName) {
        super(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression, 0);
        this.autoIncremented = autoIncremented;
        this.generated = generated;
        this.collationName = collationName;
    }

    public MysqlColumn(String name, EventMeshDataType<?> dataType, JDBCType jdbcType, Integer columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName,
        int order) {
        super(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression, order);
        this.autoIncremented = autoIncremented;
        this.generated = generated;
        this.collationName = collationName;
    }

    public MysqlColumn() {

    }

    public static MysqlColumn of(
        String name, EventMeshDataType<?> dataType, JDBCType jdbcType, Integer columnLength, Integer decimal, boolean notNull,
        String comment, Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName) {
        return new MysqlColumn(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression,
            autoIncremented, generated, collationName);
    }

    public static MysqlColumn of(
        String name, EventMeshDataType<?> dataType, JDBCType jdbcType, Integer columnLength, Integer decimal, boolean notNull, String comment,
        Object defaultValue, String defaultValueExpression, boolean autoIncremented, boolean generated, String collationName, int order) {
        return new MysqlColumn(name, dataType, jdbcType, columnLength, decimal, notNull, comment, defaultValue, defaultValueExpression,
            autoIncremented, generated, collationName, order);
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
