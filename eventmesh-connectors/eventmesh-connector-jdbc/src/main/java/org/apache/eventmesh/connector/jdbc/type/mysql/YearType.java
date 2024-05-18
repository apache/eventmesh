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

package org.apache.eventmesh.connector.jdbc.type.mysql;

import org.apache.eventmesh.connector.jdbc.JdbcDriverMetaData;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;
import org.apache.eventmesh.connector.jdbc.type.AbstractType;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public class YearType extends AbstractType<Integer> {

    public static final YearType INSTANCE = new YearType();

    private YearType() {
        super(Integer.class, SQLType.INTEGER, "YEAR");
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList(getName(), "year", "Year");
    }

    @Override
    public String getTypeName(Column<?> column) {
        JdbcDriverMetaData jdbcDriverMetaData = eventMeshDialect.getJdbcDriverMetaData();

        // As of MySQL 8.0.19, the YEAR(4) data type with an explicit display width is deprecated; you should expect support for it to be removed in a
        // future version of MySQL.
        // Instead, use YEAR without a display width, which has the same meaning
        if (JdbcStringUtils.compareVersion(jdbcDriverMetaData.getDatabaseProductVersion(), "8.0.19") >= 0) {
            return "year";
        }
        // MySQL 8.0 does not support the 2-digit YEAR(2) data type permitted in older versions of MySQL. For instructions on converting to 4-digit
        // YEAR
        if (column.getColumnLength() != null && column.getColumnLength() <= 2
            && JdbcStringUtils.compareVersion(jdbcDriverMetaData.getDatabaseProductVersion(), "8.0") >= 0) {
            return "year(4)";
        }
        return column.getColumnLength() == null ? "year" : "year(" + column.getColumnLength() + ")";
    }

    @Override
    public String getDefaultValue(DatabaseDialect<?> databaseDialect, Column<?> column) {

        final Object defaultValue = column.getDefaultValue();
        if (defaultValue == null) {
            return "NULL";
        }
        return "'" + LocalDate.parse(defaultValue.toString()).getYear() + "'";
    }

    @Override
    public Object convert2DatabaseTypeValue(Object value) {
        if (value == null) {
            return null;
        }
        return LocalDate.parse(value.toString()).getYear();
    }
}
