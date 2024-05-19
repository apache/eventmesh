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
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * This class represents an Index object with attributes such as name, column names, index type, index method, and comment.
 * It provides a Builder pattern for creating Index objects.
 */
@Getter
@Setter
@NoArgsConstructor
public class Index implements Serializable {

    // Name of the index
    private String name;

    // List of column names included in the index
    private List<String> columnNames;

    // Type of the index (e.g., unique, normal, etc.)
    private String indexType;

    // Method used for the index (e.g., B-tree, Hash, etc.)
    private String indexMethod;

    // Comment associated with the index
    private String comment;

    protected Index(Builder builder) {
        this.name = builder.name;
        this.columnNames = builder.columnNames;
        this.indexType = builder.indexType;
        this.indexMethod = builder.indexMethod;
        this.comment = builder.comment;
    }

    public Index(List<String> columnNames) {
        this.columnNames = columnNames;
    }

    public Index(String name, List<String> columnNames) {
        this.name = name;
        this.columnNames = columnNames;
    }

    public Index(String name, List<String> columnNames, String comment) {
        this.name = name;
        this.columnNames = columnNames;
        this.comment = comment;
    }

    public void addColumnNames(String... columnNames) {
        if (columnNames != null && columnNames.length > 0) {
            for (String columnName : columnNames) {
                this.columnNames.add(columnName);
            }
        }
    }

    public Index(String name, List<String> columnNames, String indexType, String indexMethod, String comment) {
        this.name = name;
        this.columnNames = columnNames;
        this.indexType = indexType;
        this.indexMethod = indexMethod;
        this.comment = comment;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        protected String name;
        protected List<String> columnNames;
        protected String indexType;
        protected String indexMethod;
        protected String comment;

        public static Builder newIndex() {
            return new Builder();
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder withColumnNames(List<String> columnNames) {
            this.columnNames = columnNames;
            return this;
        }

        public Builder withIndexType(String indexType) {
            this.indexType = indexType;
            return this;
        }

        public Builder withIndexMethod(String indexMethod) {
            this.indexMethod = indexMethod;
            return this;
        }

        public Builder withComment(String comment) {
            this.comment = comment;
            return this;
        }

        public Index build() {
            return new Index(this);
        }
    }
}
