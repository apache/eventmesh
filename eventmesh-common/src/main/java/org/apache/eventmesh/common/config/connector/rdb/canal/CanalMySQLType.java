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

package org.apache.eventmesh.common.config.connector.rdb.canal;

import java.util.HashMap;
import java.util.Map;

import com.mysql.cj.MysqlType;

public enum CanalMySQLType {
    BIT("BIT"),
    TINYINT("TINYINT"),
    SMALLINT("SMALLINT"),
    MEDIUMINT("MEDIUMINT"),
    INT("INT"),
    BIGINT("BIGINT"),
    DECIMAL("DECIMAL"),
    FLOAT("FLOAT"),
    DOUBLE("DOUBLE"),
    DATE("DATE"),
    DATETIME("DATETIME"),
    TIMESTAMP("TIMESTAMP"),
    TIME("TIME"),
    YEAR("YEAR"),
    CHAR("CHAR"),
    VARCHAR("VARCHAR"),
    BINARY("BINARY"),
    VARBINARY("VARBINARY"),
    TINYBLOB("TINYBLOB"),
    BLOB("BLOB"),
    MEDIUMBLOB("MEDIUMBLOB"),
    LONGBLOB("LONGBLOB"),
    TINYTEXT("TINYTEXT"),
    TEXT("TEXT"),
    MEDIUMTEXT("MEDIUMTEXT"),
    LONGTEXT("LONGTEXT"),
    ENUM("ENUM"),
    SET("SET"),
    JSON("JSON"),
    GEOMETRY("GEOMETRY"),
    // MysqlType not include the following type
    POINT("POINT"),
    LINESTRING("LINESTRING"),
    POLYGON("POLYGON"),
    MULTIPOINT("MULTIPOINT"),
    GEOMETRY_COLLECTION("GEOMETRYCOLLECTION"),
    GEOM_COLLECTION("GEOMCOLLECTION"),
    MULTILINESTRING("MULTILINESTRING"),
    MULTIPOLYGON("MULTIPOLYGON");

    private final String codeKey;
    private final MysqlType mysqlType;

    CanalMySQLType(String codeKey) {
        this.codeKey = codeKey;
        this.mysqlType = MysqlType.getByName(codeKey);
    }

    private static final Map<String, CanalMySQLType> TYPES = new HashMap<>();

    static {
        CanalMySQLType[] values = values();
        for (CanalMySQLType tableType : values) {
            TYPES.put(tableType.codeKey, tableType);
        }
    }

    public String genPrepareStatement4Insert() {
        switch (this) {
            case GEOMETRY:
            case GEOM_COLLECTION:
            case GEOMETRY_COLLECTION:
                return "ST_GEOMFROMTEXT(?)";
            case POINT:
                return "ST_PointFromText(?)";
            case LINESTRING:
                return "ST_LineStringFromText(?)";
            case POLYGON:
                return "ST_PolygonFromText(?)";
            case MULTIPOINT:
                return "ST_MultiPointFromText(?)";
            case MULTILINESTRING:
                return "ST_MultiLineStringFromText(?)";
            case MULTIPOLYGON:
                return "ST_MultiPolygonFromText(?)";
            default:
                return "?";
        }
    }

    public static CanalMySQLType valueOfCode(String code) {
        CanalMySQLType type = TYPES.get(code.toUpperCase());
        if (type != null) {
            return type;
        }
        switch (MysqlType.getByName(code)) {
            case BOOLEAN:
            case TINYINT:
            case TINYINT_UNSIGNED:
                return TINYINT;
            case SMALLINT:
            case SMALLINT_UNSIGNED:
                return SMALLINT;
            case INT:
            case INT_UNSIGNED:
                return INT;
            case BIGINT:
            case BIGINT_UNSIGNED:
                return BIGINT;
            case MEDIUMINT:
            case MEDIUMINT_UNSIGNED:
                return MEDIUMINT;
            case DECIMAL:
            case DECIMAL_UNSIGNED:
                return DECIMAL;
            case FLOAT:
            case FLOAT_UNSIGNED:
                return FLOAT;
            case DOUBLE:
            case DOUBLE_UNSIGNED:
                return DOUBLE;
            case BIT:
                return BIT;
            case BINARY:
                return BINARY;
            case VARBINARY:
                return VARBINARY;
            case TINYBLOB:
                return TINYBLOB;
            case MEDIUMBLOB:
                return MEDIUMBLOB;
            case LONGBLOB:
                return LONGBLOB;
            case BLOB:
                return BLOB;
            case CHAR:
                return CHAR;
            case VARCHAR:
                return VARCHAR;
            case TINYTEXT:
                return TINYTEXT;
            case MEDIUMTEXT:
                return MEDIUMTEXT;
            case LONGTEXT:
                return LONGTEXT;
            case TEXT:
                return TEXT;
            case DATE:
                return DATE;
            case TIME:
                return TIME;
            case TIMESTAMP:
                return TIMESTAMP;
            case DATETIME:
                return DATETIME;
            case YEAR:
                return YEAR;
            case JSON:
                return JSON;
            case ENUM:
                return ENUM;
            case SET:
                return SET;
            case GEOMETRY:
                return GEOMETRY;
            case NULL:
            case UNKNOWN:
            default:
                throw new UnsupportedOperationException("Unsupported mysql columnType " + code);
        }
    }

    public MysqlType getMysqlType() {
        return mysqlType;
    }
}
