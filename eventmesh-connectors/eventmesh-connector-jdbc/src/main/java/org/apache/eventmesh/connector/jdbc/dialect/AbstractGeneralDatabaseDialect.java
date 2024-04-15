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

package org.apache.eventmesh.connector.jdbc.dialect;

import org.apache.eventmesh.connector.jdbc.config.JdbcConfig;
import org.apache.eventmesh.connector.jdbc.connection.JdbcConnection;
import org.apache.eventmesh.connector.jdbc.exception.JdbcConnectionException;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.type.Type;
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
import org.apache.eventmesh.connector.jdbc.type.eventmesh.StringEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.TimeEventMeshDataType;
import org.apache.eventmesh.connector.jdbc.type.eventmesh.YearEventMeshDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.hibernate.dialect.Dialect;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractGeneralDatabaseDialect<JC extends JdbcConnection, Col extends Column> implements DatabaseDialect<JC> {

    private static final int DEFAULT_BATCH_MAX_ROWS = 20;

    private JdbcConfig config;

    private int batchMaxRows = DEFAULT_BATCH_MAX_ROWS;

    private final Map<String, Type> typeRegisters = new HashMap<>(32);

    private Dialect hibernateDialect;

    public AbstractGeneralDatabaseDialect(JdbcConfig config) {
        this.config = config;
    }

    @Override
    public void configure(Dialect hibernateDialect) {
        this.hibernateDialect = hibernateDialect;
    }

    @Override
    public boolean isValid(Connection connection, int timeout) throws JdbcConnectionException, SQLException {
        return connection == null ? false : connection.isValid(timeout);
    }

    @Override
    public PreparedStatement createPreparedStatement(Connection connection, String sql) throws SQLException {
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        if (batchMaxRows > 0) {
            preparedStatement.setFetchSize(batchMaxRows);
        }
        return preparedStatement;
    }

    @Override
    public Type getType(Column<?> column) {
        final String nativeType = column.getNativeType();
        if (nativeType != null) {
            final Type type = typeRegisters.get(nativeType);
            if (type != null) {
                log.debug("found type {} for column {}", type.getClass().getName(), column.getName());
                return type;
            }
        }
        final String dataTypeName = column.getDataType().getName();
        if (dataTypeName != null) {
            final Type type = typeRegisters.get(dataTypeName);
            if (type != null) {
                log.debug("found type {} for column {}", type.getClass().getName(), column.getName());
                return type;
            }
        }

        final String jdbcTypeName = column.getJdbcType().name();
        if (jdbcTypeName != null) {
            final Type type = typeRegisters.get(jdbcTypeName);
            if (type != null) {
                log.debug("found type {} for column {}", type.getClass().getName(), column.getName());
                return type;
            }
        }

        return null;
    }

    protected void registerTypes() {
        registerType(BooleanEventMeshDataType.INSTANCE);
        registerType(Float32EventMeshDataType.INSTANCE);
        registerType(Float64EventMeshDataType.INSTANCE);
        registerType(Int8EventMeshDataType.INSTANCE);
        registerType(Int16EventMeshDataType.INSTANCE);
        registerType(Int32EventMeshDataType.INSTANCE);
        registerType(Int64EventMeshDataType.INSTANCE);
        registerType(StringEventMeshDataType.INSTANCE);
        registerType(DateEventMeshDataType.INSTANCE);
        registerType(TimeEventMeshDataType.INSTANCE);
        registerType(DateTimeEventMeshDataType.INSTANCE);
        registerType(DecimalEventMeshDataType.INSTANCE);
        registerType(BytesEventMeshDataType.INSTANCE);
        registerType(YearEventMeshDataType.INSTANCE);
    }

    protected void registerType(Type type) {
        type.configure(this, hibernateDialect);
        Optional.ofNullable(type.ofRegistrationKeys()).orElse(new ArrayList<>(0)).forEach(key -> typeRegisters.put(key, type));
    }

    public abstract String getQualifiedTableName(TableId tableId);

    public abstract String getQualifiedText(String text);

    @Override
    public String getTypeName(Dialect hibernateDialect, Column<?> column) {
        Type type = this.getType(column);
        if (type != null) {
            return type.getTypeName(column);
        }
        Long length = Optional.ofNullable(column.getColumnLength()).orElse(0L);
        return hibernateDialect.getTypeName(column.getJdbcType().getVendorTypeNumber(), length, length.intValue(),
            Optional.ofNullable(column.getDecimal()).orElse(0));
    }
}
