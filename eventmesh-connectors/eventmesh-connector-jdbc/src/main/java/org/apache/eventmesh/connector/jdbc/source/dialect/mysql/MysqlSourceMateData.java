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

import org.apache.eventmesh.connector.jdbc.source.SourceMateData;
import org.apache.eventmesh.connector.jdbc.table.catalog.TableId;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Represents MySQL-specific metadata related to a data source.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MysqlSourceMateData extends SourceMateData {

    /**
     * The server ID of the MySQL instance.
     */
    private int serverId;

    /**
     * The Global Transaction Identifier (GTID) associated with the metadata.
     */
    private String gtid;

    /**
     * The name of the mysql binary log file.
     */
    private String binlogFile;

    /**
     * The position within the binary log file.
     */
    private long position;

    /**
     * The SQL statement associated with the metadata.
     */
    private String sql;

    public static MysqlSourceMateDataBuilder newBuilder() {
        return new MysqlSourceMateDataBuilder();
    }

    public static class MysqlSourceMateDataBuilder {

        private String connector = "mysql";
        private String name;
        private long timestamp = System.currentTimeMillis();
        private boolean snapshot;
        private String catalogName;
        private String schemaName;
        private String tableName;
        private int serverId;
        private String gtid;
        private String binlogFile;
        private long position;
        private String sql;

        public MysqlSourceMateDataBuilder connector(String connector) {
            this.connector = connector;
            return this;
        }

        public MysqlSourceMateDataBuilder name(String name) {
            this.name = name;
            return this;
        }

        public MysqlSourceMateDataBuilder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MysqlSourceMateDataBuilder snapshot(boolean snapshot) {
            this.snapshot = snapshot;
            return this;
        }

        public MysqlSourceMateDataBuilder withTableId(TableId tableId) {
            this.catalogName = tableId.getCatalogName();
            this.schemaName = tableId.getSchemaName();
            this.tableName = tableId.getTableName();
            return this;
        }

        public MysqlSourceMateDataBuilder catalogName(String catalogName) {
            this.catalogName = catalogName;
            return this;
        }

        public MysqlSourceMateDataBuilder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public MysqlSourceMateDataBuilder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public MysqlSourceMateDataBuilder serverId(int serverId) {
            this.serverId = serverId;
            return this;
        }

        public MysqlSourceMateDataBuilder gtid(String gtid) {
            this.gtid = gtid;
            return this;
        }

        public MysqlSourceMateDataBuilder binlogFile(String binlogFile) {
            this.binlogFile = binlogFile;
            return this;
        }

        public MysqlSourceMateDataBuilder position(long position) {
            this.position = position;
            return this;
        }

        public MysqlSourceMateDataBuilder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public MysqlSourceMateData build() {
            MysqlSourceMateData metadata = new MysqlSourceMateData();
            metadata.setConnector(connector);
            metadata.setName(name);
            metadata.setTimestamp(timestamp);
            metadata.setSnapshot(snapshot);
            metadata.setCatalogName(catalogName);
            metadata.setSchemaName(schemaName);
            metadata.setTableName(tableName);
            metadata.setServerId(serverId);
            metadata.setGtid(gtid);
            metadata.setBinlogFile(binlogFile);
            metadata.setPosition(position);
            metadata.setSql(sql);
            return metadata;
        }
    }
}

