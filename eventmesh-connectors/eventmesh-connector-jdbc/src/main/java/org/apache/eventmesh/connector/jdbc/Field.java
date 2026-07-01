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

package org.apache.eventmesh.connector.jdbc;

import org.apache.eventmesh.connector.jdbc.table.catalog.Column;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Field {

    private boolean required;

    private String field;

    private String name;

    private Column<?> column;

    private List<Field> fields;

    public Field(Column<?> column, boolean required, String field, String name) {
        this.column = column;
        this.required = required;
        this.field = field;
        this.name = name;
    }

    public Field withColumn(Column<?> column) {
        this.column = column;
        return this;
    }

    public Field withRequired(boolean required) {
        this.required = required;
        return this;
    }

    public Field withField(String field) {
        this.field = field;
        return this;
    }

    public Field withName(String name) {
        this.name = name;
        return this;
    }

    public Field withFields(List<Field> fields) {
        this.fields = fields;
        return this;
    }
}
