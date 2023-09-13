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

package org.apache.eventmesh.connector.jdbc.ddl;

import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import lombok.Getter;

public abstract class AbstractDdlParser implements DdlParser {

    // Indicates whether to skip parsing views.
    private final boolean skipViews;

    // Indicates whether to skip parsing comments.
    private final boolean skipComments;

    @Getter
    private final TableId tableId;

    public AbstractDdlParser(boolean skipViews, boolean skipComments) {
        this.skipViews = skipViews;
        this.skipComments = skipComments;
        this.tableId = new TableId();
    }

    @Override
    public void setCurrentDatabase(String databaseName) {
        this.tableId.setCatalogName(databaseName);
    }

    @Override
    public void setCurrentSchema(String schema) {
        this.tableId.setSchemaName(schema);
    }

    public String getCurrentDatabase() {
        return this.tableId.getCatalogName();
    }

    public boolean isSkipViews() {
        return skipViews;
    }

    public boolean isSkipComments() {
        return skipComments;
    }
}
