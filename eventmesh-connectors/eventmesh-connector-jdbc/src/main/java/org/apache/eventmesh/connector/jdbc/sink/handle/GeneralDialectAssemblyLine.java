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

package org.apache.eventmesh.connector.jdbc.sink.handle;

import org.apache.eventmesh.connector.jdbc.CatalogChanges;
import org.apache.eventmesh.connector.jdbc.Field;
import org.apache.eventmesh.connector.jdbc.Schema;
import org.apache.eventmesh.connector.jdbc.dialect.AbstractGeneralDatabaseDialect;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.dialect.SqlStatementAssembler;
import org.apache.eventmesh.connector.jdbc.event.SchemaChangeEventType;
import org.apache.eventmesh.connector.jdbc.source.SourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.table.catalog.Table;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.type.Type;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.dialect.Dialect;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeneralDialectAssemblyLine implements DialectAssemblyLine {

    @Getter
    private final DatabaseDialect<?> databaseDialect;

    @Getter
    private final Dialect hibernateDialect;

    public GeneralDialectAssemblyLine(DatabaseDialect<?> databaseDialect, Dialect hibernateDialect) {
        this.databaseDialect = databaseDialect;
        this.hibernateDialect = hibernateDialect;
    }

    @Override
    public String getDatabaseOrTableStatement(SourceMateData sourceMateData, CatalogChanges catalogChanges, String statement) {
        String type = catalogChanges.getType();
        String operationType = catalogChanges.getOperationType();
        SchemaChangeEventType schemaChangeEventType = SchemaChangeEventType.ofSchemaChangeEventType(type, operationType);
        String sql = null;
        switch (schemaChangeEventType) {
            case DATABASE_CREATE:
                sql = assembleCreateDatabaseSql(catalogChanges);
                break;
            case DATABASE_DROP:
                sql = assembleDropDatabaseSql(catalogChanges);
                break;
            case DATABASE_ALERT:
                sql = assembleAlertDatabaseSql(catalogChanges);
                break;
            case TABLE_CREATE:
                sql = assembleCreateTableSql(catalogChanges);
                break;
            case TABLE_DROP:
                sql = assembleDropTableSql(catalogChanges);
                break;
            case TABLE_ALERT:
                sql = assembleAlertTableSql(catalogChanges);
                break;
            default:
                log.warn("Type={}, OperationType={} not support", type, operationType);
        }
        return sql;
    }

    /**
     * Generates an upsert statement using the given sourceMateData, schema, and originStatement.
     *
     * @param sourceMateData  The metadata of the data source.
     * @param schema          The schema to upsert into.
     * @param originStatement The original upsert statement.
     * @return The upsert statement as a string.
     */
    @Override
    public String getUpsertStatement(SourceMateData sourceMateData, Schema schema, String originStatement) {
        return null;
    }

    /**
     * Generates a delete statement based on the given sourceMateData, schema, and original statement.
     *
     * @param sourceMateData  The source metadata used to generate the delete statement.
     * @param schema          The schema used to generate the delete statement.
     * @param originStatement The original statement used as a basis for the delete statement.
     * @return The generated delete statement as a string.
     */
    @Override
    public String getDeleteStatement(SourceMateData sourceMateData, Schema schema, String originStatement) {
        SqlStatementAssembler sqlStatementAssembler = new SqlStatementAssembler();
        sqlStatementAssembler.appendSqlSlice("DELETE FROM ")
            .appendSqlSlice(((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedTableName(sourceMateData.ofTableId()));
        sqlStatementAssembler.appendSqlSlice(" WHERE ");
        if (schema.containsKey()) {
            sqlStatementAssembler.appendSqlSliceLists(" AND ", schema.getKeySet(), (columnName) -> columnName + " =?");
        } else {
            Field after = schema.getFields().get(0);
            sqlStatementAssembler.appendSqlSliceOfColumns(" AND ",
                after.getFields().stream().map(field -> field.getColumn()).sorted(Comparator.comparingInt(Column::getOrder))
                    .collect(Collectors.toList()),
                (column) -> column.getName() + " =?");
        }
        return sqlStatementAssembler.build();
    }

    /**
     * Generates an SQL update statement based on the provided source metadata, schema, and origin statement.
     *
     * @param sourceMateData  The source metadata to be used for generating the update statement.
     * @param schema          The schema to be used for generating the update statement.
     * @param originStatement The original SQL statement that needs to be updated.
     * @return The generated SQL update statement as a string.
     */
    @Override
    public String getUpdateStatement(SourceMateData sourceMateData, Schema schema, String originStatement) {
        final SqlStatementAssembler sqlStatementAssembler = new SqlStatementAssembler();
        final TableId tableId = sourceMateData.ofTableId();
        // primary key set
        final Set<String> keySet = schema.getKeySet();
        Field tableColumns = schema.getFields().get(0);
        sqlStatementAssembler.appendSqlSlice("UPDATE ");
        sqlStatementAssembler.appendSqlSlice(((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedTableName(tableId));
        sqlStatementAssembler.appendSqlSlice(" SET ");
        sqlStatementAssembler.appendSqlSliceLists(", ",
            tableColumns.getFields().stream().map(Field::getColumn).sorted(Comparator.comparingInt(Column::getOrder))
                .filter(column -> !keySet.contains(column.getName())).map(column -> column.getName()).collect(Collectors.toList()),
            (columnName) -> columnName + " =?");
        if (schema.containsKey()) {
            sqlStatementAssembler.appendSqlSlice(" WHERE ");
            sqlStatementAssembler.appendSqlSliceLists(" AND ", keySet, (columnName) -> columnName + " =?");
        } else {
            sqlStatementAssembler.appendSqlSlice(" WHERE ");
            sqlStatementAssembler.appendSqlSliceOfColumns(" AND ",
                tableColumns.getFields().stream().map(field -> field.getColumn()).sorted(Comparator.comparingInt(Column::getOrder))
                    .collect(Collectors.toList()),
                column -> column.getName() + " =?");
        }
        return sqlStatementAssembler.build();
    }

    @Override
    public String getInsertStatement(SourceMateData sourceMateData, Schema schema, String originStatement) {
        final TableId tableId = sourceMateData.ofTableId();

        List<Field> afterFields = schema.getFields().stream().filter(field -> StringUtils.equals(field.getField(), "after"))
            .collect(Collectors.toList());

        final SqlStatementAssembler sqlAssembler = new SqlStatementAssembler();
        sqlAssembler.appendSqlSlice("INSERT INTO ");
        sqlAssembler.appendSqlSlice(((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedTableName(tableId));
        sqlAssembler.appendSqlSlice(" (");
        // assemble columns
        Field afterField = afterFields.get(0);
        List<Column<?>> columns = afterField.getFields().stream().map(item -> item.getColumn()).sorted(Comparator.comparingInt(Column::getOrder))
            .collect(Collectors.toList());
        sqlAssembler.appendSqlSliceOfColumns(", ", columns, column -> column.getName());
        sqlAssembler.appendSqlSlice(") VALUES (");
        // assemble values
        sqlAssembler.appendSqlSliceOfColumns(", ", columns, column -> getDmlBindingValue(column));
        sqlAssembler.appendSqlSlice(")");

        return sqlAssembler.build();
    }

    private String getDmlBindingValue(Column<?> column) {
        Type type = this.databaseDialect.getType(column);
        if (type == null) {
            return this.databaseDialect.getQueryBindingWithValueCast(column);
        }
        return type.getQueryBindingWithValue(this.databaseDialect, column);
    }

    private String assembleCreateDatabaseSql(CatalogChanges catalogChanges) {
        SqlStatementAssembler assembler = new SqlStatementAssembler();
        assembler.appendSqlSlice("CREATE DATABASE IF NOT EXISTS ");
        assembler.appendSqlSlice(((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedText(catalogChanges.getCatalog().getName()));
        return assembler.build();
    }

    private String assembleDropDatabaseSql(CatalogChanges catalogChanges) {
        SqlStatementAssembler assembler = new SqlStatementAssembler();
        assembler.appendSqlSlice("DROP DATABASE IF EXISTS ");
        assembler.appendSqlSlice(((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedText(catalogChanges.getCatalog().getName()));
        return assembler.build();
    }

    private String assembleAlertDatabaseSql(CatalogChanges catalogChanges) {
        SqlStatementAssembler assembler = new SqlStatementAssembler();
        // todo
        return assembler.build();
    }

    private String assembleCreateTableSql(CatalogChanges catalogChanges) {
        SqlStatementAssembler assembler = new SqlStatementAssembler();
        assembler.appendSqlSlice("CREATE TABLE IF NOT EXISTS ");
        Table table = catalogChanges.getTable();
        assembler.appendSqlSlice(((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedTableName(table.getTableId()));
        assembler.appendSqlSlice(" (");
        // assemble columns
        List<? extends Column> columns = catalogChanges.getColumns().stream().sorted(Comparator.comparingInt(Column::getOrder))
            .collect(Collectors.toList());
        List<String> columnNames = columns.stream().map(item -> item.getName()).collect(Collectors.toList());
        Map<String, Column> columnMap = columns.stream().collect(Collectors.toMap(Column::getName, item -> item));
        assembler.appendSqlSliceLists(", ", columnNames, (columnName) -> {
            StringBuilder builder = new StringBuilder();
            // assemble column name
            builder.append(((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedText(columnName));
            // assemble column type
            Column column = columnMap.get(columnName);
            String typeName = this.databaseDialect.getTypeName(hibernateDialect, column);
            builder.append(" ").append(typeName);

            builder.append(" ").append(this.databaseDialect.getCharsetOrCollateFormatted(column));
            if (Optional.ofNullable(table.getPrimaryKey().getColumnNames()).orElse(new ArrayList<>(0)).contains(columnName)) {
                builder.append(" NOT NULL ");
                if (column.isAutoIncremented()) {
                    builder.append(this.databaseDialect.getAutoIncrementFormatted(column));
                }
            } else {
                if (column.isNotNull()) {
                    builder.append(" NOT NULL ");
                }
            }
            addColumnDefaultValue(column, builder);
            builder.append(" ").append(this.databaseDialect.getCommentFormatted(column));
            // assemble column default value
            return builder.toString();
        });
        // assemble primary key and others key
        assembler.appendSqlSlice(", PRIMARY KEY(");
        assembler.appendSqlSliceLists(",", catalogChanges.getTable().getPrimaryKey().getColumnNames(),
            (columnName) -> ((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedText(columnName));
        assembler.appendSqlSlice(")");
        assembler.appendSqlSlice(")");
        assembler.appendSqlSlice(this.databaseDialect.getTableOptionsFormatted(catalogChanges.getTable()));
        return assembler.build();
    }

    private void addColumnDefaultValue(Column<?> column, StringBuilder builder) {
        if (column.isNotNull() && column.getDefaultValue() == null) {
            return;
        }
        final String defaultValueFormatted = this.databaseDialect.getDefaultValueFormatted(column);
        if (StringUtils.isNotEmpty(defaultValueFormatted)) {
            builder.append(" DEFAULT ").append(defaultValueFormatted);
        }
    }

    private String assembleDropTableSql(CatalogChanges catalogChanges) {
        SqlStatementAssembler assembler = new SqlStatementAssembler();
        assembler.appendSqlSlice("DROP TABLE IF EXISTS ");
        assembler.appendSqlSlice(
            ((AbstractGeneralDatabaseDialect<?, ?>) databaseDialect).getQualifiedTableName(catalogChanges.getTable().getTableId()));
        return assembler.build();
    }

    private String assembleAlertTableSql(CatalogChanges catalogChanges) {
        SqlStatementAssembler assembler = new SqlStatementAssembler();
        return assembler.build();
    }
}
