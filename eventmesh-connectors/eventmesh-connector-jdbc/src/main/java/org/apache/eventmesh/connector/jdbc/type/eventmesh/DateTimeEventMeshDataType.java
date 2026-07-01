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

package org.apache.eventmesh.connector.jdbc.type.eventmesh;

import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;
import org.apache.eventmesh.connector.jdbc.type.AbstractType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DateTimeEventMeshDataType extends AbstractType<LocalDateTime> {

    public static final DateTimeEventMeshDataType INSTANCE = new DateTimeEventMeshDataType();

    private DateTimeEventMeshDataType() {
        super(LocalDateTime.class, SQLType.TIMESTAMP, "DATETIME");
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList(getName());
    }

    @Override
    public String getDefaultValue(DatabaseDialect<?> databaseDialect, Column<?> column) {
        if (column.getDefaultValue() == null) {
            return "NULL";
        }
        LocalDateTime localDateTime = LocalDateTime.parse(column.getDefaultValue().toString());
        if (Optional.ofNullable(column.getColumnLength()).orElse(0L) > 0) {
            return "'" + localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S")) + "'";
        }
        return "'" + localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "'";
    }
}
