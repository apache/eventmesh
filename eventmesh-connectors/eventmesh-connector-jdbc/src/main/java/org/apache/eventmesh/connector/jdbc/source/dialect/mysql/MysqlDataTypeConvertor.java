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

package org.apache.eventmesh.connector.jdbc.source.dialect.mysql;

import org.apache.eventmesh.connector.jdbc.DataTypeConvertor;
import org.apache.eventmesh.connector.jdbc.exception.DataTypeConvertException;
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.BooleanEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.BytesEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.DateEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.DateTimeEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.DecimalEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Float32EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Float64EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int16EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int32EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int64EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.Int8EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.NullEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.StringEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.TimeEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.YearEventMeshDataType;

import java.sql.JDBCType;
import java.util.Map;
import java.util.Objects;

import com.mysql.cj.MysqlType;

public class MysqlDataTypeConvertor implements DataTypeConvertor<MysqlType> {

    public static final String PRECISION = "precision";

    public static final String SCALE = "scale";

    /**
     * @see <a href="https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html">Fixed-Point Types (Exact Value) - DECIMAL, NUMERIC</a>
     */
    public static final Integer DEFAULT_PRECISION = 10;

    public static final Integer DEFAULT_SCALE = 0;

    /**
     * Converts a string representation of a connector data type to the corresponding JDBCType.
     *
     * @param connectorDataType The string representation of the connector data type.
     * @return The corresponding JDBCType, or null if the connector data type is not recognized.
     */
    @Override
    public JDBCType toJDBCType(String connectorDataType) {
        MysqlType mysqlType = MysqlType.getByName(connectorDataType);
        return JDBCType.valueOf(mysqlType.getJdbcType());
    }

    /**
     * Converts a connector data type to an EventMesh data type. e.g. "int", "varchar(255)","decimal(10,2)"
     *
     * @param connectorDataType The connector data type to be converted.
     * @return The converted EventMesh data type.
     * @throws DataTypeConvertException If the conversion fails.
     */
    @Override
    public EventMeshDataType<?> toEventMeshType(String connectorDataType) throws DataTypeConvertException {
        MysqlType mysqlType = MysqlType.getByName(connectorDataType);
        return toEventMeshType(mysqlType, null);
    }

    /**
     * Converts JDBCType and dataTypeProperties to EventMeshDataType.
     *
     * @param jdbcType           the JDBCType to be converted
     * @param dataTypeProperties the properties of the data type
     * @return the converted EventMeshDataType
     * @throws DataTypeConvertException if there is an error during conversion
     */
    @Override
    public EventMeshDataType<?> toEventMeshType(JDBCType jdbcType, Map<String, Object> dataTypeProperties) throws DataTypeConvertException {
        return toEventMeshType(MysqlType.getByJdbcType(jdbcType.getVendorTypeNumber()), dataTypeProperties);
    }

    /**
     * Converts a connector data type to an EventMesh data type with additional data type properties.
     *
     * @param connectorDataType  The connector data type to be converted.
     * @param dataTypeProperties Additional data type properties.
     * @return The converted EventMesh data type.
     * @throws DataTypeConvertException If the conversion fails.
     */
    @Override
    public EventMeshDataType<?> toEventMeshType(MysqlType connectorDataType, Map<String, Object> dataTypeProperties) throws DataTypeConvertException {

        Objects.requireNonNull(connectorDataType, "MysqlType can't be null");

        switch (connectorDataType) {
            case NULL:
                return NullEventMeshDataType.INSTANCE;
            case BOOLEAN:
                return BooleanEventMeshDataType.INSTANCE;
            case BIT: {
                /**
                 *  @see <a href="https://dev.mysql.com/doc/refman/8.0/en/bit-type.html">Mysql doc</a>
                 */
                if (null == dataTypeProperties) {
                    return BytesEventMeshDataType.INSTANCE;
                }
                Integer precision = (Integer) dataTypeProperties.get(MysqlDataTypeConvertor.PRECISION);
                if (precision != null && precision == 1) {
                    return BooleanEventMeshDataType.INSTANCE;
                }
                return BytesEventMeshDataType.INSTANCE;
            }
            case TINYINT:
                return Int8EventMeshDataType.INSTANCE;
            case TINYINT_UNSIGNED:
            case SMALLINT:
                return Int16EventMeshDataType.INSTANCE;
            case SMALLINT_UNSIGNED:
            case INT:
            case MEDIUMINT:
            case MEDIUMINT_UNSIGNED:
                return Int32EventMeshDataType.INSTANCE;
            case INT_UNSIGNED:
            case BIGINT:
                return Int64EventMeshDataType.INSTANCE;
            case FLOAT:
            case FLOAT_UNSIGNED:
                return Float32EventMeshDataType.INSTANCE;
            case DOUBLE:
            case DOUBLE_UNSIGNED:
                return Float64EventMeshDataType.INSTANCE;
            case TIME:
                return TimeEventMeshDataType.INSTANCE;
            case YEAR:
                return YearEventMeshDataType.INSTANCE;
            case DATE:
                return DateEventMeshDataType.INSTANCE;
            case TIMESTAMP:
            case DATETIME:
                return DateTimeEventMeshDataType.INSTANCE;
            case CHAR:
            case VARCHAR:
            case TINYTEXT:
            case TEXT:
            case MEDIUMTEXT:
            case LONGTEXT:
            case JSON:
            case ENUM:
            case SET:
                return StringEventMeshDataType.INSTANCE;
            case BINARY:
            case VARBINARY:
            case TINYBLOB:
            case BLOB:
            case MEDIUMBLOB:
            case LONGBLOB:
            case GEOMETRY:
                return BytesEventMeshDataType.INSTANCE;
            case BIGINT_UNSIGNED:
            case DECIMAL:
            case DECIMAL_UNSIGNED: {
                /**
                 *  @see <a https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html">Mysql doc-DECIMAL, NUMERIC</a>
                 */
                if (dataTypeProperties == null) {
                    return new DecimalEventMeshDataType(DEFAULT_PRECISION, DEFAULT_SCALE);
                }
                Integer precision = (Integer) dataTypeProperties.getOrDefault(PRECISION, DEFAULT_PRECISION);
                Integer scale = (Integer) dataTypeProperties.getOrDefault(SCALE, DEFAULT_SCALE);
                return new DecimalEventMeshDataType(precision, scale);
            }
            default:
                throw new DataTypeConvertException(String.format("%s type is not supported", connectorDataType.getName()));
        }
    }

    /**
     * Converts an EventMesh data type to a connector data type with additional data type properties.
     *
     * @param eventMeshDataType  The EventMesh data type to be converted.
     * @param dataTypeProperties Additional data type properties.
     * @return The converted connector data type.
     * @throws DataTypeConvertException If the conversion fails.
     */
    @Override
    public MysqlType toConnectorType(EventMeshDataType<?> eventMeshDataType, Map<String, Object> dataTypeProperties) throws DataTypeConvertException {

        Objects.requireNonNull(eventMeshDataType, "Parameter eventMeshDataType can not be null");
        SQLType sqlType = eventMeshDataType.getSQLType();

        switch (sqlType) {
            case BOOLEAN:
                return MysqlType.BOOLEAN;
            case TINYINT:
                return MysqlType.TINYINT;
            case SMALLINT:
                return MysqlType.SMALLINT;
            case INTEGER:
                return MysqlType.INT;
            case BIGINT:
                return MysqlType.BIGINT;
            case FLOAT:
                return MysqlType.FLOAT;
            case DOUBLE:
                return MysqlType.DOUBLE;
            case DECIMAL:
                return MysqlType.DECIMAL;
            case NULL:
                return MysqlType.NULL;
            case BINARY:
                return MysqlType.BIT;
            case DATE:
                return MysqlType.DATE;
            case TIME:
                return MysqlType.DATETIME;
            case TIMESTAMP:
                return MysqlType.TIMESTAMP;
            case ARRAY:
            case STRING:
                return MysqlType.VARCHAR;
            default:
                throw new DataTypeConvertException(String.format("%s type is not supported", sqlType.name()));
        }
    }
}
