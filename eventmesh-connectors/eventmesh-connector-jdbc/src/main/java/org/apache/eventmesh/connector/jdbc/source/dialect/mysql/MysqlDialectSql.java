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

package org.apache.eventmesh.connector.jdbc.source.dialect.mysql;

public enum MysqlDialectSql {

    SHOW_DATABASE("SHOW DATABASES;", "show mysql database sql"),

    SELECT_DATABASE("USE %s", " Try to access database"),

    /**
     * https://dev.mysql.com/doc/refman/8.0/en/show-tables.html
     */
    SHOW_DATABASE_TABLE("SHOW FULL TABLES IN %s WHERE Table_type = 'BASE TABLE';", "show mysql database tables"),

    /**
     * https://dev.mysql.com/doc/refman/8.0/en/show-columns.html
     */
    SHOW_TABLE_COLUMNS("SHOW FULL COLUMNS FROM %s FROM %s;", "show mysql database table columns"),

    SELECT_TABLE_COLUMNS("SELECT * FROM %s WHERE 1=2", "show mysql database table columns"),

    /**
     * https://dev.mysql.com/doc/refman/8.0/en/show-create-table.html
     */
    SHOW_CREATE_TABLE("SHOW CREATE TABLE %s", "show mysql database table columns"),

    SHOW_GTID_STATUS("SHOW GLOBAL VARIABLES LIKE 'GTID_MODE'", "Show GTID_MODE Enable or not"),

    SHOW_MASTER_STATUS("SHOW MASTER STATUS", "Show master status"),

    SELECT_PURGED_GTID("SELECT @@global.gtid_purged", "SELECT the purged GTID values"),

    SHOW_GRANTS_FOR_CURRENT_USER("SHOW GRANTS FOR CURRENT_USER", "s=Show grants for current user"),

    SHOW_CREATE_DATABASE("SHOW CREATE DATABASE %s", "Show create database sql"),

    /**
     * <a href="https://dev.mysql.com/doc/refman/8.0/en/show-table-status.html">SHOW_TABLE_STATUS</a>
     */
    SHOW_TABLE_STATUS("SHOW TABLE STATUS LIKE '%s'", "Show table status"),

    SNAPSHOT_TABLE_SELECT_SQL("SELECT * FROM %s", "Select table data sql in snapshot"),

    LOCK_TABLE_GLOBAL("FLUSH TABLES WITH READ LOCK", "global lock tables"),

    LOCK_TABLES("FLUSH TABLES %s WITH READ LOCK", "lock tables"),

    UNLOCK_TABLES("UNLOCK TABLES", "unlock tables");

    private final String sql;

    private final String desc;

    MysqlDialectSql(String sql, String desc) {
        this.sql = sql;
        this.desc = desc;
    }

    public String ofSQL() {
        return this.sql;
    }

    public String ofWrapperSQL(Object... parameters) {
        return String.format(this.sql, parameters);
    }

    public String ofDescription() {
        return this.desc;
    }
}
