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

package org.apache.eventmesh.connector.jdbc.table.type;

import java.util.Objects;

public class PrimitiveType<T> implements EventMeshDataType<T> {

    public static final PrimitiveType<String> STRING_TYPE = new PrimitiveType<>(String.class, SQLType.STRING);
    public static final PrimitiveType<Boolean> BOOLEAN_TYPE = new PrimitiveType<>(Boolean.class, SQLType.BOOLEAN);
    public static final PrimitiveType<Byte> BYTE_TYPE = new PrimitiveType<>(Byte.class, SQLType.TINYINT);
    public static final PrimitiveType<Short> SHORT_TYPE = new PrimitiveType<>(Short.class, SQLType.SMALLINT);
    public static final PrimitiveType<Integer> INT_TYPE = new PrimitiveType<>(Integer.class, SQLType.INTEGER);
    public static final PrimitiveType<Long> LONG_TYPE = new PrimitiveType<>(Long.class, SQLType.BIGINT);
    public static final PrimitiveType<Float> FLOAT_TYPE = new PrimitiveType<>(Float.class, SQLType.FLOAT);
    public static final PrimitiveType<Double> DOUBLE_TYPE = new PrimitiveType<>(Double.class, SQLType.DOUBLE);
    public static final PrimitiveType<Void> VOID_TYPE = new PrimitiveType<>(Void.class, SQLType.NULL);

    private final Class<T> typeClass;

    private final SQLType sqlType;

    public PrimitiveType(Class<T> typeClass, SQLType sqlType) {
        this.typeClass = typeClass;
        this.sqlType = sqlType;
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PrimitiveType)) {
            return false;
        }
        PrimitiveType<?> that = (PrimitiveType<?>) o;
        return Objects.equals(getTypeClass(), that.getTypeClass()) && sqlType == that.sqlType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTypeClass(), sqlType);
    }

    @Override
    public String toString() {
        return typeClass.getName();
    }
}
