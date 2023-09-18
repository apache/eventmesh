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

package org.apache.eventmesh.connector.jdbc.table.catalog.mysql;

import org.apache.eventmesh.connector.jdbc.table.catalog.AbstractColumnEditorImpl;

public class MysqlColumnEditorImpl extends AbstractColumnEditorImpl<MysqlColumnEditor, MysqlColumn> implements MysqlColumnEditor {

    private boolean autoIncremented;

    private boolean generated;

    private String collationName;

    public MysqlColumnEditorImpl(String name) {
        super(name);
    }

    public MysqlColumnEditorImpl() {
    }

    /**
     * Sets whether the column is auto-incremented.
     *
     * @param autoIncremented Whether the column is auto-incremented.
     * @return The ColumnEditor instance.
     */
    @Override
    public MysqlColumnEditor autoIncremented(boolean autoIncremented) {
        this.autoIncremented = autoIncremented;
        return this;
    }

    /**
     * Sets whether the column is generated.
     *
     * @param generated Whether the column is generated.
     * @return The ColumnEditor instance.
     */
    @Override
    public MysqlColumnEditor generated(boolean generated) {
        this.generated = generated;
        return this;
    }

    /**
     * Sets the collation (character set) for the column.
     *
     * @param collationName The name of the collation to set.
     * @return The column editor with the collation set.
     */
    @Override
    public MysqlColumnEditor collate(String collationName) {
        this.collationName = collationName;
        return this;
    }

    /**
     * Builds and returns the configured column.
     *
     * @return The configured column.
     */
    @Override
    public MysqlColumn build() {
        return MysqlColumn.of(ofName(), ofEventMeshDataType(), ofJdbcType(), ofColumnLength(), ofScale(), isNotNull(), ofComment(), ofDefaultValue(),
                ofDefaultValueExpression(), autoIncremented, generated, collationName);
    }
}
