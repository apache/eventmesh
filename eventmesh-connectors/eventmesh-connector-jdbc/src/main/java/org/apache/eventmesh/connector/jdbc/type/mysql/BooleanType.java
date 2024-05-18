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

import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;
import org.apache.eventmesh.connector.jdbc.type.AbstractType;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

public class BooleanType extends AbstractType<Boolean> {

    public static final BooleanType INSTANCE = new BooleanType();

    public BooleanType() {
        super(Boolean.class, SQLType.BOOLEAN, "BOOLEAN");
    }

    @Override
    public String getDefaultValue(DatabaseDialect<?> databaseDialect, Column<?> column) {
        return column.getDefaultValue() == null ? " NULL " : String.format("b'%s'", column.getDefaultValue());
    }

    @Override
    public String getTypeName(Column<?> column) {
        return "bit(1)";
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList(getName(), "bool", "boolean");
    }

    @Override
    public Object convert2DatabaseTypeValue(Object value) {
        if (value instanceof String) {
            return StringUtils.equalsIgnoreCase("true", (String) value) ? 1 : 0;
        }
        return Long.parseLong(value.toString()) > 0 ? 1 : 0;
    }
}
