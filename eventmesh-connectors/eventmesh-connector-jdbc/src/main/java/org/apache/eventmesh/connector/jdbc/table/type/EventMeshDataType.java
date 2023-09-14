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

/**
 * Defines Event Mesh data type with methods to get the type class and SQL type of the data.
 */
public interface EventMeshDataType<T> {

    /**
     * Gets the type class of the data.
     *
     * @return the type class of the data.
     */
    Class<T> getTypeClass();

    /**
     * Gets the SQL type of the data.
     *
     * @return the SQL type of the data.
     */
    SQLType getSQLType();

    /**
     * Gets the name of the data type.
     *
     * @return The name of the data type.
     */
    default String getName() {
        return EventMeshTypeNameConverter.ofName(this);
    }
}
