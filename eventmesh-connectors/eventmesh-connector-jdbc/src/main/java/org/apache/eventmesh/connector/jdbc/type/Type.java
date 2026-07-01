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

package org.apache.eventmesh.connector.jdbc.type;

import org.apache.eventmesh.connector.jdbc.dialect.DatabaseDialect;
import org.apache.eventmesh.connector.jdbc.table.catalog.Column;

import java.util.List;

import org.hibernate.dialect.Dialect;
import org.hibernate.query.Query;

/**
 * top interface of jdbc type
 */
public interface Type {

    /**
     * Configures the database dialects for the EventMesh and Hibernate.
     *
     * @param eventMeshDialect The database dialect for the EventMesh.
     * @param hibernateDialect The database dialect for Hibernate.
     */
    void configure(DatabaseDialect<?> eventMeshDialect, Dialect hibernateDialect);

    /**
     * Retrieves a list of registration keys.
     *
     * @return A list of registration keys as strings.
     */
    List<String> ofRegistrationKeys();

    /**
     * Retrieves the default value for a given database dialect and column.
     *
     * @param databaseDialect The specific database dialect.
     * @param column          The column for which to retrieve the default value.
     * @return The default value for the specified database dialect and column.
     */
    String getDefaultValue(DatabaseDialect<?> databaseDialect, Column<?> column);

    /**
     * Returns the type name of the specified column.
     *
     * @param column the column object for which to retrieve the type name
     * @return the type name of the column
     */
    String getTypeName(Column<?> column);

    /**
     * Returns the query binding with value for the specified column and database dialect.
     *
     * @param databaseDialect the database dialect object to determine the query binding
     * @param column          the column object for which to retrieve the query binding with value
     * @return the query binding with value for the column and database dialect
     */
    default String getQueryBindingWithValue(DatabaseDialect<?> databaseDialect, Column<?> column) {
        return databaseDialect.getQueryBindingWithValueCast(column);
    }

    /**
     * Converts the given value to the appropriate database type.
     *
     * @param value the value to be converted
     * @return the converted value in the database type
     */
    default Object convert2DatabaseTypeValue(Object value) {
        return value;
    }

    /**
     * Binds the parameter value to the specified position in the query object and returns the number of bound parameters.
     *
     * @param startIndex The starting index for binding parameters
     * @param value The value to bind
     * @param query The query object to bind parameters to
     * @return The number of bound parameters, Default is 1
     */
    default int bindValue(int startIndex, Object value, Query<?> query) {
        // Binds the parameter value to the specified index position in the query object
        query.setParameter(startIndex, value);
        // Returns the number of bound parameters, always 1
        return 1;
    }

}
