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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MapType<K, V> {

    private static final List<SQLType> SUPPORTED_KEY_TYPES =
        Arrays.asList(
            SQLType.NULL,
            SQLType.BOOLEAN,
            SQLType.TINYINT,
            SQLType.SMALLINT,
            SQLType.INTEGER,
            SQLType.BIGINT,
            SQLType.DATE,
            SQLType.TIME,
            SQLType.TIMESTAMP,
            SQLType.FLOAT,
            SQLType.DOUBLE,
            SQLType.STRING,
            SQLType.DECIMAL);

    private final EventMeshDataType keyType;

    private final EventMeshDataType valueType;

    public MapType(EventMeshDataType keyType, EventMeshDataType valueType) {
        Objects.requireNonNull(keyType, "The key type is required.");
        Objects.requireNonNull(valueType, "The value type is required.");

        if (!SUPPORTED_KEY_TYPES.contains(keyType.getSQLType())) {
            throw new IllegalArgumentException(String.format("Not support type: %s", keyType.getSQLType()));
        }

        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MapType)) {
            return false;
        }
        MapType<?, ?> mapType = (MapType<?, ?>) o;
        return Objects.equals(keyType, mapType.keyType) && Objects.equals(valueType, mapType.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, valueType);
    }

    public EventMeshDataType keyType() {
        return this.keyType;
    }

    public EventMeshDataType valueType() {
        return this.valueType;
    }
}
