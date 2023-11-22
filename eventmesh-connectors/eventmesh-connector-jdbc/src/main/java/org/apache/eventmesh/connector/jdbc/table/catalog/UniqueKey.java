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

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a unique key constraint for a database table.
 * <p>A unique key ensures that the values in specified columns are unique across all rows in the table.</p>
 */
@Setter
@Getter
public class UniqueKey implements Serializable {

    // The name of the unique key, if specified.
    private String name;

    // The list of column names that make up the unique/primary key.
    private List<String> columnNames;

    // An optional comment or description for the unique/primary key.
    private String comment;

    public UniqueKey() {
    }

    public UniqueKey(String name, List<String> columnNames, String comment) {
        this.name = name;
        this.columnNames = columnNames;
        this.comment = comment;
    }

    public UniqueKey(String name, List<String> columnNames) {
        this.name = name;
        this.columnNames = columnNames;
    }

    public UniqueKey(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    public UniqueKey copy() {
        return new UniqueKey(name, columnNames, comment);
    }

    public void addColumnNames(String... columnNames) {
        if (columnNames != null && columnNames.length > 0) {
            for (String columnName : columnNames) {
                this.columnNames.add(columnName);
            }
        }
    }
}
