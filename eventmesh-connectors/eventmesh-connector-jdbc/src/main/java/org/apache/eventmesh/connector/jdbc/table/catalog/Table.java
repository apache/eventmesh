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

package org.apache.eventmesh.connector.jdbc.table.catalog;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Table {

    private TableId tableId;

    private PrimaryKey primaryKey;

    private List<UniqueKey> uniqueKeys;

    private String comment;

    private Options options = new Options();

    public Table(TableId tableId, PrimaryKey primaryKey, List<UniqueKey> uniqueKeys, String comment) {
        this.tableId = tableId;
        this.primaryKey = primaryKey;
        this.uniqueKeys = uniqueKeys;
        this.comment = comment;
    }

    public Table(TableId tableId, PrimaryKey primaryKey, List<UniqueKey> uniqueKeys, String comment, Options options) {
        this.tableId = tableId;
        this.primaryKey = primaryKey;
        this.uniqueKeys = uniqueKeys;
        this.comment = comment;
        if (options != null) {
            this.options.putAll(options);
        }
    }

    public void put(String key, Object value) {
        options.put(key, value);
    }

    public void putAll(Options options) {
        this.options.putAll(options);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private TableId tableId;
        private PrimaryKey primaryKey;
        private List<UniqueKey> uniqueKeys;
        private String comment;
        private Options options;

        public Builder withTableId(TableId tableId) {
            this.tableId = tableId;
            return this;
        }

        public Builder withPrimaryKey(PrimaryKey primaryKey) {
            this.primaryKey = primaryKey;
            return this;
        }

        public Builder withUniqueKeys(List<UniqueKey> uniqueKeys) {
            this.uniqueKeys = uniqueKeys;
            return this;
        }

        public Builder withComment(String comment) {
            this.comment = comment;
            return this;
        }

        public Builder withOptions(Options options) {
            this.options = options;
            return this;
        }

        public Table build() {
            return new Table(tableId, primaryKey, uniqueKeys, comment, options);
        }
    }
}
