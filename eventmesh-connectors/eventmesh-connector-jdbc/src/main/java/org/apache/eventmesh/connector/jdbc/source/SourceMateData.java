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

package org.apache.eventmesh.connector.jdbc.source;

import org.apache.eventmesh.connector.jdbc.source.dialect.mysql.MysqlSourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.Data;

/**
 * Represents metadata related to a data source.
 */
@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "connector")
@JsonSubTypes({@JsonSubTypes.Type(value = MysqlSourceMateData.class, name = "mysql")})
public class SourceMateData {

    /**
     * The connector used for the connector source. e.g: mysql, oracle etc.
     */
    private String connector;

    /**
     * The name of the connector source.
     */
    private String name;

    /**
     * The timestamp when the metadata was captured.
     */
    private long timestamp;

    /**
     * Flag indicating whether this metadata belongs to a snapshot.
     */
    private boolean snapshot;

    /**
     * The catalog name associated with the connector source.
     */
    private String catalogName;

    /**
     * The schema name associated with the connector source.
     */
    private String schemaName;

    /**
     * The table name associated with the connector source.
     */
    private String tableName;

    /**
     * This method returns the TableId object with the specified catalog name, schema name, and table name.
     *
     * @return the TableId object
     */
    public TableId ofTableId() {
        return new TableId(catalogName, schemaName, tableName);
    }

}
