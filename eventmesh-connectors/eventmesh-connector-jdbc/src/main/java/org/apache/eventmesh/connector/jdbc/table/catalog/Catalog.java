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

import org.apache.eventmesh.connector.jdbc.exception.CatalogException;
import org.apache.eventmesh.connector.jdbc.exception.DatabaseNotExistException;
import org.apache.eventmesh.connector.jdbc.exception.TableNotExistException;

import java.sql.SQLException;
import java.util.List;

/**
 * Interacting with a catalog of databases and tables.
 */
public interface Catalog extends AutoCloseable {

    /**
     * Opens the catalog.
     *
     * @throws CatalogException if there is an error opening the catalog.
     */
    void open() throws CatalogException;

    /**
     * Gets the name of the default database.
     *
     * @return the name of the default database.
     */
    String getDefaultDatabase();

    /**
     * Checks if a database with the given name exists.
     *
     * @param databaseName the name of the database to check.
     * @return true if the database exists, false otherwise.
     * @throws CatalogException if there is an error checking for the database.
     */
    boolean databaseExists(String databaseName) throws CatalogException;

    /**
     * Gets a list of all databases in the catalog.
     *
     * @return a list of all databases in the catalog.
     * @throws CatalogException if there is an error getting the list of databases.
     */
    List<String> listDatabases() throws CatalogException;

    /**
     * Gets a list of all tables in the given database.
     *
     * @param databaseName the name of the database to get the tables for.
     * @return a list of all tables in the given database.
     * @throws CatalogException          if there is an error getting the list of tables.
     * @throws DatabaseNotExistException if the database does not exist.
     * @throws SQLException              if there is an error accessing the database.
     */
    List<TableId> listTables(String databaseName) throws CatalogException, DatabaseNotExistException, SQLException;

    /**
     * Checks if a table with the given ID exists.
     *
     * @param tableId the ID of the table to check.
     * @return true if the table exists, false otherwise.
     * @throws CatalogException if there is an error checking for the table.
     * @throws SQLException     if there is an error accessing the database.
     */
    boolean tableExists(TableId tableId) throws CatalogException, SQLException;

    /**
     * Gets the table with the given ID.
     *
     * @param tableId the ID of the table to get.
     * @return the table with the given ID.
     * @throws CatalogException       if there is an error getting the table.
     * @throws TableNotExistException if the table does not exist.
     * @throws SQLException           if there is an error accessing the database.
     */
    CatalogTable getTable(TableId tableId) throws CatalogException, TableNotExistException, SQLException;

    // TODO: support create table, drop table and update table
}
