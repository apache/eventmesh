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

import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class IntType extends NumberType<Integer> {

    public static final IntType INSTANCE = new IntType();

    public IntType() {
        super(Integer.class, SQLType.INTEGER, "INT");
    }

    @Override
    public String getTypeName(Column<?> column) {
        Long length = Optional.ofNullable(column.getColumnLength()).orElse(0L);
        String typeName = hibernateDialect.getTypeName(column.getJdbcType().getVendorTypeNumber(), length, length.intValue(),
            Optional.ofNullable(column.getDecimal()).orElse(0));
        StringBuilder typeNameBuilder = new StringBuilder(length > 0 ? typeName + "(" + length + ")" : typeName);
        typeNameBuilder.append(convertOptions2Sql(column));
        return typeNameBuilder.toString();
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList("INT32", getName(), "int");
    }
}
