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

import org.apache.eventmesh.connector.jdbc.table.catalog.AbstractTableEditorImpl;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableSchema;

import java.util.ArrayList;

public class MysqlTableEditorImpl extends AbstractTableEditorImpl<MysqlTableEditor, MysqlColumn, MysqlTableSchema> implements MysqlTableEditor {

    public MysqlTableEditorImpl(TableId tableId) {
        super(tableId);
    }

    /**
     * Builds and returns the table schema with the configured properties.
     *
     * @return The resulting table schema.
     */
    @Override
    public MysqlTableSchema build() {
        return TableSchema.newMysqlTableSchemaBuilder()
            .withTableId(ofTableId())
            .withColumns(new ArrayList<>(ofColumns().values()))
            .withPrimaryKey(ofPrimaryKey())
            .withUniqueKeys(ofUniqueKeys())
            .withComment(ofComment())
            .withOptions(ofOptions())
            .build();
    }
}
