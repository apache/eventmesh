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

import java.sql.Types;

/**
 * see {@link java.sql.SQLType}
 */
public enum SQLType {

    BIT(Types.BIT),

    /**
     * Identifies the generic SQL type {@code TINYINT}.
     */
    TINYINT(Types.TINYINT),
    /**
     * Identifies the generic SQL type {@code SMALLINT}.
     */
    SMALLINT(Types.SMALLINT),
    /**
     * Identifies the generic SQL type {@code INTEGER}.
     */
    INTEGER(Types.INTEGER),
    /**
     * Identifies the generic SQL type {@code BIGINT}.
     */
    BIGINT(Types.BIGINT),
    /**
     * Identifies the generic SQL type {@code FLOAT}.
     */
    FLOAT(Types.FLOAT),

    /**
     * Identifies the generic SQL type {@code DOUBLE}.
     */
    DOUBLE(Types.DOUBLE),

    /**
     * Identifies the generic SQL type {@code DECIMAL}.
     */
    DECIMAL(Types.DECIMAL),
    NUMERIC(Types.NUMERIC),
    REAL(Types.REAL),

    /**
     * Identifies the generic SQL type {@code DATE}.
     */
    DATE(Types.DATE),
    /**
     * Identifies the generic SQL type {@code TIME}.
     */
    TIME(Types.TIME),
    /**
     * Identifies the generic SQL type {@code TIMESTAMP}.
     */
    TIMESTAMP(Types.TIMESTAMP),

    TIMESTAMP_WITH_TIMEZONE(Types.TIMESTAMP_WITH_TIMEZONE),

    /**
     * Identifies the generic SQL type {@code BINARY}.
     */
    BINARY(Types.BINARY),

    /**
     * Identifies the generic SQL value {@code NULL}.
     */
    NULL(Types.NULL),

    /**
     * Identifies the generic SQL type {@code ARRAY}.
     */
    ARRAY(Types.ARRAY),

    /**
     * Identifies the generic SQL type {@code BOOLEAN}.
     */
    BOOLEAN(Types.BOOLEAN),

    STRING(Types.VARCHAR);

    private int type;

    SQLType(int type) {
        this.type = type;
    }

    public int ofType() {
        return type;
    }
}
