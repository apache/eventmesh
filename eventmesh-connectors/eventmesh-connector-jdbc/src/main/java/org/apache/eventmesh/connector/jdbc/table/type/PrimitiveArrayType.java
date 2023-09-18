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

public class PrimitiveArrayType<E, T> implements EventMeshDataType<T> {

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<String[], String> STRING_ARRAY_TYPE = new PrimitiveArrayType(String[].class, PrimitiveType.STRING_TYPE);

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<Boolean[], Boolean> BOOLEAN_ARRAY_TYPE = new PrimitiveArrayType(Boolean[].class,
            PrimitiveType.BOOLEAN_TYPE);

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<Byte[], Byte> BYTE_ARRAY_TYPE = new PrimitiveArrayType(Byte[].class, PrimitiveType.BYTE_TYPE);

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<Short[], Short> SHORT_ARRAY_TYPE = new PrimitiveArrayType(Short[].class, PrimitiveType.SHORT_TYPE);

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<Integer[], Integer> INT_ARRAY_TYPE = new PrimitiveArrayType(Integer[].class, PrimitiveType.INT_TYPE);

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<Long[], Long> LONG_ARRAY_TYPE = new PrimitiveArrayType(Long[].class, PrimitiveType.LONG_TYPE);

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<Float[], Float> FLOAT_ARRAY_TYPE = new PrimitiveArrayType(Float[].class, PrimitiveType.FLOAT_TYPE);

    @SuppressWarnings("unchecked")
    public static final PrimitiveArrayType<Double[], Double> DOUBLE_ARRAY_TYPE = new PrimitiveArrayType(Double[].class, PrimitiveType.DOUBLE_TYPE);

    private final Class<T> typeClass;

    private final PrimitiveType<E> sqlType;

    private PrimitiveArrayType(Class<T> arrayClass, PrimitiveType<E> elementType) {
        this.typeClass = arrayClass;
        this.sqlType = elementType;
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
        return SQLType.ARRAY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PrimitiveArrayType)) {
            return false;
        }
        PrimitiveArrayType<?, ?> that = (PrimitiveArrayType<?, ?>) o;
        return Objects.equals(getTypeClass(), that.getTypeClass()) && Objects.equals(sqlType, that.sqlType);
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
