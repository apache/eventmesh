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

package org.apache.eventmesh.connector.jdbc.table.catalog.mysql;

import org.apache.eventmesh.connector.jdbc.table.catalog.Options;
import org.apache.eventmesh.connector.jdbc.table.catalog.PrimaryKey;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableSchema;
import org.apache.eventmesh.connector.jdbc.table.catalog.UniqueKey;

import java.util.List;
import java.util.Map;

import lombok.Getter;

public class MysqlTableSchema extends TableSchema {

    @Getter
    private Options tableOptions;

    public MysqlTableSchema(TableId tableId, Map<String, MysqlColumn> columnMap, List<MysqlColumn> columns, Map<Integer, MysqlColumn> orderColumnMap,
        PrimaryKey primaryKey, List<UniqueKey> uniqueKeys, String comment, Options tableOptions) {
        super(tableId, columnMap, columns, orderColumnMap, primaryKey, uniqueKeys, comment);
        this.tableOptions = tableOptions;
    }

    public MysqlTableSchema() {
    }

    public MysqlTableSchema(String name) {
        super(name);
    }
}
