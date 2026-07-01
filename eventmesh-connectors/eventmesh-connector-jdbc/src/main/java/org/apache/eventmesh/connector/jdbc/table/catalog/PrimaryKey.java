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

public class PrimaryKey extends UniqueKey {

    public PrimaryKey() {

    }

    public PrimaryKey(String name, List<String> columnNames, String comment) {
        super(name, columnNames, comment);
    }

    public PrimaryKey(String name, List<String> columnNames) {
        super(name, columnNames);
    }

    public PrimaryKey(List<String> columnNames) {
        super(columnNames);
    }

    public PrimaryKey(List<String> columnNames, String comment) {
        super(null, columnNames, comment);
    }

    /**
     * Creates a new PrimaryKey instance with the given name and column names.
     *
     * @param columnNames The list of column names that make up the primary key.
     * @return A new PrimaryKey instance.
     */
    public static PrimaryKey of(List<String> columnNames) {
        return new PrimaryKey(columnNames);
    }

    /**
     * Creates a copy of this PrimaryKey instance.
     *
     * @return A new PrimaryKey instance with the same name and column names.
     */
    public PrimaryKey copy() {
        return new PrimaryKey(getName(), getColumnNames(), getComment());
    }
}