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

package org.apache.eventmesh.connector.canal.source.table;

import org.apache.eventmesh.common.config.connector.rdb.canal.RdbTableDefinition;

import java.util.Objects;

import lombok.Data;

@Data
public class RdbSimpleTable extends RdbTableDefinition {
    public RdbSimpleTable(String database, String schema, String tableName) {
        this.schemaName = schema;
        this.tableName = tableName;
        this.database = database;
    }

    public RdbSimpleTable(String schema, String tableName) {
        this(null, schema, tableName);
    }

    private final String database;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        RdbSimpleTable that = (RdbSimpleTable) o;
        return Objects.equals(database, that.database);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), database);
    }
}
