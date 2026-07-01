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

package org.apache.eventmesh.connector.jdbc.sink.handle;

import org.apache.eventmesh.connector.jdbc.CatalogChanges;
import org.apache.eventmesh.connector.jdbc.Schema;
import org.apache.eventmesh.connector.jdbc.source.SourceMateData;

/**
 * Represents an assembly line for transforming and generating SQL statements based on specific database dialects.
 */
public interface DialectAssemblyLine {

    /**
     * Retrieves the database or table statement from the given {@code SourceMateData}, {@code CatalogChanges}, and {@code statement}.
     *
     * @param sourceMateData the source mate data object containing the information about the source
     * @param catalogChanges the catalog changes object containing the information about the catalog changes
     * @param statement      the statement for which the database or table statement needs to be retrieved
     * @return the database or table statement of the given {@code statement}
     */
    String getDatabaseOrTableStatement(SourceMateData sourceMateData, CatalogChanges catalogChanges, String statement);

    /**
     * Generates an insert statement for the given source mate data, schema, and origin statement.
     *
     * @param sourceMateData  The source mate data of the database.
     * @param schema          The schema of the table.
     * @param originStatement The original insert statement.
     * @return The insert statement with the correct syntax for the given database and table.
     */
    String getInsertStatement(SourceMateData sourceMateData, Schema schema, String originStatement);

    /**
     * Generates an upsert statement using the given sourceMateData, schema, and originStatement.
     *
     * @param sourceMateData  The metadata of the data source.
     * @param schema          The schema to upsert into.
     * @param originStatement The original upsert statement.
     * @return The upsert statement as a string.
     */
    String getUpsertStatement(SourceMateData sourceMateData, Schema schema, String originStatement);

    /**
     * Generates a delete statement based on the given sourceMateData, schema, and original statement.
     *
     * @param sourceMateData  The source metadata used to generate the delete statement.
     * @param schema          The schema used to generate the delete statement.
     * @param originStatement The original statement used as a basis for the delete statement.
     * @return The generated delete statement as a string.
     */
    String getDeleteStatement(SourceMateData sourceMateData, Schema schema, String originStatement);

    /**
     * Generates an SQL update statement based on the provided source metadata, schema, and origin statement.
     *
     * @param sourceMateData  The source metadata to be used for generating the update statement.
     * @param schema          The schema to be used for generating the update statement.
     * @param originStatement The original SQL statement that needs to be updated.
     * @return The generated SQL update statement as a string.
     */
    String getUpdateStatement(SourceMateData sourceMateData, Schema schema, String originStatement);

}
