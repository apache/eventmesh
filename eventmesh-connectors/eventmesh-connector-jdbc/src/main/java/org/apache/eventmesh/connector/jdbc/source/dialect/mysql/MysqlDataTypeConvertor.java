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
import org.apache.eventmesh.connector.jdbc.table.type.CalendarType;
import org.apache.eventmesh.connector.jdbc.table.type.DecimalType;
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;
import org.apache.eventmesh.connector.jdbc.table.type.PrimitiveByteArrayType;
import org.apache.eventmesh.connector.jdbc.table.type.PrimitiveType;
import org.apache.eventmesh.connector.jdbc.table.type.SQLType;

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
                return PrimitiveType.VOID_TYPE;
            case BOOLEAN:
                return PrimitiveType.BOOLEAN_TYPE;
            case BIT: {
                /**
                 *  @see <a href="https://dev.mysql.com/doc/refman/8.0/en/bit-type.html">Mysql doc</a>
                 */
                if (null == dataTypeProperties) {
                    return PrimitiveByteArrayType.BYTES_TYPE;
                }
                Integer precision = (Integer) dataTypeProperties.get(MysqlDataTypeConvertor.PRECISION);
                if (precision != null && precision == 1) {
                    return PrimitiveType.BOOLEAN_TYPE;
                }
                return PrimitiveByteArrayType.BYTES_TYPE;
            }
            case TINYINT:
                return PrimitiveType.BYTE_TYPE;
            case TINYINT_UNSIGNED:
            case SMALLINT:
                return PrimitiveType.SHORT_TYPE;
            case SMALLINT_UNSIGNED:
            case INT:
            case MEDIUMINT:
            case MEDIUMINT_UNSIGNED:
                return PrimitiveType.INT_TYPE;
            case INT_UNSIGNED:
            case BIGINT:
                return PrimitiveType.LONG_TYPE;
            case FLOAT:
            case FLOAT_UNSIGNED:
                return PrimitiveType.FLOAT_TYPE;
            case DOUBLE:
            case DOUBLE_UNSIGNED:
                return PrimitiveType.DOUBLE_TYPE;
            case TIME:
                return CalendarType.LOCAL_TIME_TYPE;
            case YEAR:
            case DATE:
                return CalendarType.LOCAL_DATE_TYPE;
            case TIMESTAMP:
            case DATETIME:
                return CalendarType.LOCAL_DATE_TIME_TYPE;
            case CHAR:
            case VARCHAR:
            case TINYTEXT:
            case TEXT:
            case MEDIUMTEXT:
            case LONGTEXT:
            case JSON:
            case ENUM:
                return PrimitiveType.STRING_TYPE;
            case BINARY:
            case VARBINARY:
            case TINYBLOB:
            case BLOB:
            case MEDIUMBLOB:
            case LONGBLOB:
            case GEOMETRY:
                return PrimitiveByteArrayType.BYTES_TYPE;
            case BIGINT_UNSIGNED:
            case DECIMAL:
            case DECIMAL_UNSIGNED: {
                /**
                 *  @see <a https://dev.mysql.com/doc/refman/8.0/en/fixed-point-types.html">Mysql doc-DECIMAL, NUMERIC</a>
                 */
                if (dataTypeProperties == null) {
                    return new DecimalType(DEFAULT_PRECISION, DEFAULT_SCALE);
                }
                Integer precision = (Integer) dataTypeProperties.getOrDefault(PRECISION, DEFAULT_PRECISION);
                Integer scale = (Integer) dataTypeProperties.getOrDefault(SCALE, DEFAULT_SCALE);
                return new DecimalType(precision, scale);
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
            case MAP:
            case ROW:
            case STRING:
                return MysqlType.VARCHAR;
            default:
                throw new DataTypeConvertException(String.format("%s type is not supported", sqlType.name()));
        }
    }
}
