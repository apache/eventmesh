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

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class BitType extends AbstractType<byte[]> {

    public static final BitType INSTANCE = new BitType();

    public BitType() {
        super(byte[].class, SQLType.BIT, "BIT");
    }

    @Override
    public String getDefaultValue(DatabaseDialect<?> databaseDialect, Column<?> column) {
        return column.getDefaultValue() == null ? " NULL " : String.format("b'%s'", column.getDefaultValue());
    }

    @Override
    public String getTypeName(Column<?> column) {
        // https://dev.mysql.com/doc/refman/8.0/en/bit-type.html
        Long columnLength = column.getColumnLength();
        return String.format("bit(%d)", Optional.ofNullable(columnLength).orElse(1L).intValue());
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList("BIT", "bit");
    }

    @Override
    public Object convert2DatabaseTypeValue(Object value) {
        String strValue = (String) value;
        return Base64.getDecoder().decode(strValue);
    }
}
