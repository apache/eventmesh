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

package org.apache.eventmesh.connector.jdbc;

import org.apache.eventmesh.connector.jdbc.exception.DataTypeConvertException;
import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;

import java.sql.JDBCType;
import java.util.Map;

/**
 * convert data types between EventMesh and connector.
 */
public interface DataTypeConvertor<T> {

    /**
     * Converts a string representation of a connector data type to the corresponding JDBCType.
     *
     * @param connectorDataType The string representation of the connector data type.
     * @return The corresponding JDBCType, or null if the connector data type is not recognized.
     */
    JDBCType toJDBCType(String connectorDataType);


    /**
     * Converts a connector data type to an EventMesh data type.
     *
     * @param connectorDataType The connector data type to be converted.
     * @return The converted EventMesh data type.
     * @throws DataTypeConvertException If the conversion fails.
     */
    EventMeshDataType<?> toEventMeshType(String connectorDataType) throws DataTypeConvertException;

    /**
     * Converts JDBCType and dataTypeProperties to EventMeshDataType.
     *
     * @param jdbcType           the JDBCType to be converted
     * @param dataTypeProperties the properties of the data type
     * @return the converted EventMeshDataType
     * @throws DataTypeConvertException if there is an error during conversion
     */
    EventMeshDataType<?> toEventMeshType(JDBCType jdbcType, Map<String, Object> dataTypeProperties) throws DataTypeConvertException;

    /**
     * Converts a connector data type to an EventMesh data type with additional data type properties.
     *
     * @param connectorDataType  The connector data type to be converted.
     * @param dataTypeProperties Additional data type properties.
     * @return The converted EventMesh data type.
     * @throws DataTypeConvertException If the conversion fails.
     */
    EventMeshDataType<?> toEventMeshType(T connectorDataType, Map<String, Object> dataTypeProperties) throws DataTypeConvertException;

    /**
     * Converts an EventMesh data type to a connector data type with additional data type properties.
     *
     * @param eventMeshDataType  The EventMesh data type to be converted.
     * @param dataTypeProperties Additional data type properties.
     * @return The converted connector data type.
     * @throws DataTypeConvertException If the conversion fails.
     */
    T toConnectorType(EventMeshDataType<?> eventMeshDataType, Map<String, Object> dataTypeProperties) throws DataTypeConvertException;
}
