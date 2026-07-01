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

package org.apache.eventmesh.connector.jdbc.sink.mysql;

import org.apache.eventmesh.connector.jdbc.Field;
import org.apache.eventmesh.connector.jdbc.Schema;
import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.dialect.SqlStatementAssembler;
import org.apache.eventmesh.connector.jdbc.sink.handle.GeneralDialectAssemblyLine;
import org.apache.eventmesh.connector.jdbc.source.SourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;
import org.apache.eventmesh.connector.jdbc.utils.JdbcStringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.dialect.Dialect;

public class MysqlDialectAssemblyLine extends GeneralDialectAssemblyLine {

    public MysqlDialectAssemblyLine(DatabaseDialect<?> databaseDialect, Dialect hibernateDialect) {
        super(databaseDialect, hibernateDialect);
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
        final SqlStatementAssembler sqlStatementAssembler = new SqlStatementAssembler();
        sqlStatementAssembler.appendSqlSlice(getInsertStatement(sourceMateData, schema, originStatement));
        Field afterField = schema.getFields().get(0);
        List<Column<?>> columns = afterField.getFields().stream().map(item -> item.getColumn()).sorted(Comparator.comparingInt(Column::getOrder))
            .collect(Collectors.toList());
        if (JdbcStringUtils.compareVersion(getDatabaseDialect().getJdbcDriverMetaData().getDatabaseProductVersion(), "8.0.20") >= 0) {
            // mysql doc:https://dev.mysql.com/doc/refman/8.0/en/insert-on-duplicate.html
            // Beginning with MySQL 8.0.20, an INSERT ... SELECT ... ON DUPLICATE KEY UPDATE statement that uses VALUES() in the UPDATE clause
            sqlStatementAssembler.appendSqlSlice("AS new ON DUPLICATE KEY UPDATE ");
            sqlStatementAssembler.appendSqlSliceOfColumns(",", columns, column -> {
                final String columnName = column.getName();
                return columnName + "=new." + columnName;
            });

        } else {
            sqlStatementAssembler.appendSqlSlice(" ON DUPLICATE KEY UPDATE ");
            sqlStatementAssembler.appendSqlSliceOfColumns(",", columns, column -> {
                final String columnName = column.getName();
                return columnName + "=VALUES(" + columnName + ")";
            });

        }
        return sqlStatementAssembler.build();
    }
}
