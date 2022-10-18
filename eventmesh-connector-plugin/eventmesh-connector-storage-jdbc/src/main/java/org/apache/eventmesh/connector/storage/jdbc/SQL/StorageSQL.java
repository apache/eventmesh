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

package org.apache.eventmesh.connector.storage.jdbc.SQL;

public interface StorageSQL {

    public String insertSQL(String tableName);

    public String selectSQL(String tableName);

    public String selectConsumerGroup();

    public String insertConsumerGroup();

    public String createDatabaseSQL(String databaseName);

    public String topicTableCreateSQL(String table);

    public String consumerGroupTableCreateSQL();

    public String locationEventSQL(String tableName);

    public String queryLocationEventSQL(String tableName);

    public String selectFastMessageSQL(String tableName);

    public String selectLastMessageSQL(String tableName);

    public String selectNoConsumptionMessageSQL(String tableName, Long consumerGroupId);

    public String selectAppointTimeMessageSQL(String tableName, String time);

    public String queryTables();

    public default String replySelectSQL(String table, int num) {
        StringBuffer sql = new StringBuffer();
        sql.append("select * from ").append(table).append(" where cloud_event_info_id in(");
        for (int i = 1; i <= num; i++) {
            sql.append("?");
            if (i != num) {
                sql.append(",");
            }
        }
        sql.append(")");
        sql.append(" and cloud_event_reply_data is not null");
        return sql.toString();
    }

    public default String replyResult(String table) {
        StringBuffer sql = new StringBuffer();
        sql.append(" update ").append(table)
            .append("  set  cloud_event_reply_data = ? , cloud_event_reply_state = 'NOTHING' where cloud_event_info_id = ?");
        return sql.toString();
    }

    public default String updateOffsetSQL(String table) {
        StringBuffer sql = new StringBuffer();
        sql.append("update ").append(table).append(" set cloud_event_state = 'SUCCESS'  where cloud_event_info_id = ?");
        return sql.toString();
    }
}
