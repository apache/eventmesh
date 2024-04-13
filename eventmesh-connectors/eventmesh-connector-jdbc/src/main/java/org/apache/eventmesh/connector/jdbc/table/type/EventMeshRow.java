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

package org.apache.eventmesh.connector.jdbc.table.type;

import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a row in an event mesh.
 */
@Getter
@Setter
public class EventMeshRow implements Serializable {

    /**
     * The mode for handling the row (e.g. INSERT, UPDATE, DELETE) {@link RowHandleMode}
     */
    private RowHandleMode mode = RowHandleMode.INSERT;

    /**
     * The ID of the table that the row belongs to
     */
    private TableId tableId;

    /**
     * The values of the fields in the row
     */
    private final Object[] fieldValues;

    /**
     * Any additional metadata associated with the row
     */
    private Map<String, ?> ext = new HashMap<>();

    public EventMeshRow(int fieldNum) {
        this.fieldValues = new Object[fieldNum];
    }

    public EventMeshRow(int fieldNum, TableId tableId) {
        this.tableId = tableId;
        this.fieldValues = new Object[fieldNum];
    }

    public EventMeshRow(RowHandleMode mode, int fieldNum, TableId tableId) {
        this.mode = mode;
        this.tableId = tableId;
        this.fieldValues = new Object[fieldNum];
    }

    /**
     * Sets the values of the fields in the row.
     *
     * @param fieldValues the new field values
     * @throws NullPointerException     if fieldValues is null
     * @throws IllegalArgumentException if fieldValues has a different length than the existing field values
     */
    public void setFieldValues(Object[] fieldValues) {
        Objects.requireNonNull(fieldValues, "Parameter fields can not be null");
        if (this.fieldValues.length != fieldValues.length) {
            throw new IllegalArgumentException();
        }
        System.arraycopy(fieldValues, 0, this.fieldValues, 0, this.fieldValues.length);
    }

}
