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

import org.apache.eventmesh.connector.jdbc.table.catalog.ColumnEditor;

/**
 * Interface for editing MySQL column properties.
 */
public interface MysqlColumnEditor extends ColumnEditor<MysqlColumnEditor, MysqlColumn> {

    /**
     * Creates a new MySQL column editor with the specified name.
     *
     * @param name The name of the column.
     * @return A new MySQL column editor instance.
     */
    static MysqlColumnEditor ofEditor(String name) {
        return new MysqlColumnEditorImpl(name);
    }

    /**
     * Creates a new MySQL column editor without specifying a name.
     *
     * @return A new MySQL column editor instance.
     */
    static MysqlColumnEditor ofEditor() {
        return new MysqlColumnEditorImpl();
    }

    /**
     * Sets whether the column is auto-incremented.
     *
     * @param autoIncremented Whether the column is auto-incremented.
     * @return The ColumnEditor instance.
     */
    MysqlColumnEditor autoIncremented(boolean autoIncremented);

    /**
     * Sets whether the column is generated.
     *
     * @param generated Whether the column is generated.
     * @return The ColumnEditor instance.
     */
    MysqlColumnEditor generated(boolean generated);

    /**
     * Sets the collation (character set) for the column.
     *
     * @param collationName The name of the collation to set.
     * @return The column editor with the collation set.
     */
    MysqlColumnEditor collate(String collationName);

}
