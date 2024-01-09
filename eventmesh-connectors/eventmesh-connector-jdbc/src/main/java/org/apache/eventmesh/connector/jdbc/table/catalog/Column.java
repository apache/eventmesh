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

import org.apache.eventmesh.connector.jdbc.table.type.EventMeshDataType;

import java.io.Serializable;
import java.sql.JDBCType;
import java.sql.Types;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Column of {@link TableSchema}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class Column<Col extends Column> implements Serializable {

    /**
     * Name of the column
     */
    protected String name;

    /**
     * Data type of the column
     */
    protected EventMeshDataType<?> dataType;

    /**
     * {@link Types JDBC type}
     */
    protected JDBCType jdbcType;

    /**
     * Length of the column
     */
    protected Integer columnLength;

    /**
     * Decimal point of the column
     */
    protected Integer decimal;

    /**
     * Indicates if the column can be null or not
     */
    protected boolean notNull;

    /**
     * Comment for the column
     */
    protected String comment;

    /**
     * Default value for the column
     */
    protected Object defaultValue;

    protected String defaultValueExpression;

    protected int order;

    /**
     * creates a clone of the Column
     *
     * @return clone of column
     */
    public abstract Col clone();

}
