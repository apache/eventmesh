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

import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;

import java.util.Optional;

import org.hibernate.dialect.Dialect;

public abstract class AbstractType<T> implements EventMeshDataType<T> {

    protected Dialect hibernateDialect;

    protected DatabaseDialect<?> eventMeshDialect;

    private final Class<T> typeClass;

    private final SQLType sqlType;

    private final String name;

    public AbstractType(Class<T> typeClass, SQLType sqlType, String name) {
        this.typeClass = typeClass;
        this.sqlType = sqlType;
        this.name = name;
    }

    @Override
    public void configure(DatabaseDialect<?> eventMeshDialect, Dialect hibernateDialect) {
        this.hibernateDialect = hibernateDialect;
        this.eventMeshDialect = eventMeshDialect;
    }

    @Override
    public String getTypeName(Column<?> column) {
        Long length = Optional.ofNullable(column.getColumnLength()).orElse(0L);
        return hibernateDialect.getTypeName(column.getJdbcType().getVendorTypeNumber(), length, length.intValue(),
            Optional.ofNullable(column.getDecimal()).orElse(0));
    }

    /**
     * Returns the type class of the data.
     *
     * @return the type class of the data.
     */
    @Override
    public Class<T> getTypeClass() {
        return typeClass;
    }

    /**
     * Returns the SQL type of the data.
     *
     * @return the SQL type of the data.
     */
    @Override
    public SQLType getSQLType() {
        return sqlType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDefaultValue(DatabaseDialect<?> databaseDialect, Column<?> column) {
        return column.getDefaultValue() == null ? "NULL" : column.getDefaultValue().toString();
    }
}
