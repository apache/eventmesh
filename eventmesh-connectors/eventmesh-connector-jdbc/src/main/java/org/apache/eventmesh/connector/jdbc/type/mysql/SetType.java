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

import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SetType extends AbstractType<String> {

    public static final SetType INSTANCE = new SetType();

    private SetType() {
        super(String.class, SQLType.STRING, "SET");
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList(getName(), "set");
    }

    @Override
    public String getTypeName(Column<?> column) {
        // https://dev.mysql.com/doc/refman/8.0/en/set.html
        List<String> enumValues = column.getEnumValues();
        if (CollectionUtils.isNotEmpty(enumValues)) {
            return "SET(" + enumValues.stream().map(val -> "'" + val + "'").collect(Collectors.joining(", ")) + ")";
        }
        return "SET()";
    }

    @Override
    public String getDefaultValue(DatabaseDialect<?> databaseDialect, Column<?> column) {
        if (column.getDefaultValue() == null) {
            return "NULL";
        }
        return "'" + column.getDefaultValue() + "'";
    }
}