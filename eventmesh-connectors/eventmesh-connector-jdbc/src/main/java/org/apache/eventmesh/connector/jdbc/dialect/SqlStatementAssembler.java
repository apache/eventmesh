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

import org.apache.eventmesh.connector.jdbc.table.catalog.Column;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;

/**
 * The {@code SqlStatementAssembler} class is used to assemble SQL statements by appending SQL slices.
 */
public final class SqlStatementAssembler {

    private final StringBuilder statement;

    public SqlStatementAssembler() {
        statement = new StringBuilder();
    }

    public SqlStatementAssembler appendSqlSlice(String slice) {
        statement.append(slice);
        return this;
    }

    public SqlStatementAssembler appendSqlSliceLists(String delimiter, Collection<String> columnNames, Function<String, String> function) {
        for (Iterator<String> iterator = columnNames.iterator(); iterator.hasNext();) {
            statement.append(function.apply(iterator.next()));
            if (iterator.hasNext()) {
                statement.append(delimiter);
            }
        }
        return this;
    }

    public SqlStatementAssembler appendSqlSliceOfColumns(String delimiter, Collection<Column<?>> columns, Function<Column<?>, String> function) {
        for (Iterator<Column<?>> iterator = columns.iterator(); iterator.hasNext();) {
            statement.append(function.apply(iterator.next()));
            if (iterator.hasNext()) {
                statement.append(delimiter);
            }
        }
        return this;
    }

    public String build() {
        return statement.toString();
    }

}
