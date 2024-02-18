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

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a unique key constraint for a database table.
 * <p>A unique key ensures that the values in specified columns are unique across all rows in the table.</p>
 */
@Setter
@Getter
public class UniqueKey extends Index {

    private static final String INDEX_TYPE = "UNIQUE";

    public UniqueKey() {
    }

    public UniqueKey(String name, List<String> columnNames, String comment) {
        super(name, columnNames, INDEX_TYPE, null, comment);
    }

    public UniqueKey(String name, List<String> columnNames) {
        super(name, columnNames);
    }

    public UniqueKey(List<String> columnNames) {
        super(columnNames);
    }

    public UniqueKey copy() {
        return new UniqueKey(getName(), getColumnNames(), getComment());
    }
}
