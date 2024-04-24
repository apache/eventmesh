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

public class DecimalType extends NumberType<Double> {

    public static final DecimalType INSTANCE = new DecimalType();

    public DecimalType() {
        super(Double.class, SQLType.DOUBLE, "DECIMAL");
    }

    @Override
    public String getTypeName(Column<?> column) {
        // DECIMAL[(M[,D])] [UNSIGNED] [ZEROFILL]
        // A packed “exact” fixed-point number. M is the total number of digits (the precision) and D is the number of digits after the decimal point
        // (the scale). The decimal point and (for negative numbers) the - sign are not counted in M. If D is 0, values have no decimal point or
        // fractional part. The maximum number of digits (M) for DECIMAL is 65.
        // The maximum number of supported decimals (D) is 30. If D is omitted, the default is 0. If M is omitted, the default is 10.
        StringBuilder typeNameBuilder = new StringBuilder();
        Long length = Optional.ofNullable(column.getColumnLength()).orElse(10L);
        if (column.getDecimal() == null) {
            typeNameBuilder.append("DECIMAL(" + length + ")");
        } else {
            String typeName = hibernateDialect.getTypeName(column.getJdbcType().getVendorTypeNumber(), length, length.intValue(),
                Optional.ofNullable(column.getDecimal()).orElse(0));
            typeNameBuilder.append(typeName);
        }
        typeNameBuilder.append(convertOptions2Sql(column));
        return typeNameBuilder.toString();
    }

    @Override
    public List<String> ofRegistrationKeys() {
        return Arrays.asList("decimal", getName());
    }
}
