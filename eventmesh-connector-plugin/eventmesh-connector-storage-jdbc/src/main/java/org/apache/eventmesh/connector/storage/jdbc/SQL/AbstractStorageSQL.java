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

import lombok.Setter;

public abstract class AbstractStorageSQL implements StorageSQL {

    @Setter
    protected String offsetField;

    protected String idFieldName = "cloud_event_info_id";

    protected String timeFieldName = "cloud_event_create_time";

    protected String insertSQL = "";

    protected String selectSQL = "";

    protected String updateOffset = "";

    @Override
    public String insertSQL(String tableName) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("insert info ").append(tableName).append(
            "(cloud_event_id,cloud_event_topic,cloud_event_storage_node_adress,cloud_event_type,cloud_event_producer_group_name,cloud_event_source,cloud_event_content_type,cloud_event_tag,cloud_event_extensions,cloud_event_data)")
            .append("values(?,?,?,?,?,?,?,CAST(? as JSON),?,?)");
        return stringBuffer.toString();
    }

    @Override
    public String selectSQL(String tableName) {
        return this.queryLocationEventSQL(tableName);
    }

    public String selectConsumerGroup() {
        return "select * from consumer_group_info";
    }

    public String insertConsumerGroup() {
        return null;
    }

    public String locationEventSQL(String tableName) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("update ").append(tableName).append(" set json_set( cloud_event_consume_location , ? ,? )")
            .append(" where cloud_event_info_id > ? and json_extract(cloud_event_consume_location, ?) is null limit 200");
        return stringBuffer.toString();
    }

    public String queryLocationEventSQL(String tableName) {
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("select * from ").append(tableName)
            .append(" where cloud_event_info_id > ? and JSON_CONTAINS_PATH(cloud_event_consume_location, 'one', ?)");
        return stringBuffer.toString();
    }

    private StringBuffer getSelectSQLById(String tableName) {
        StringBuffer sql = new StringBuffer();
        return sql.append("select '").append(tableName).append("' as tableName , ").append(idFieldName).append(" from ")
            .append(tableName);
    }

    // select * from tables where id > {id} and
    public String selectFastMessageSQL(String tableName) {
        return this.getSelectSQLById(tableName).append("  order by  ").append(idFieldName).append(" limit 1").toString();
    }

    public String selectLastMessageSQL(String tableName) {
        return this.getSelectSQLById(tableName).append("  order by  ").append(idFieldName).append(" desc  limit 1").toString();
    }

    public String selectNoConsumptionMessageSQL(String tableName, Long consumerGroupId) {
        return this.getSelectSQLById(tableName).append(" where ").append(timeFieldName)
            .append("> ? and json_extract(cloud_event_consume_location, ?) is not null limit 1 ")
            .append(offsetField).append(" & ").append(consumerGroupId).append(" = ").append(consumerGroupId)
            .toString();
    }

    public String selectAppointTimeMessageSQL(String tableName, String time) {
        return this.getSelectSQLById(tableName).append(" where ").append(timeFieldName).append(" > ? limit 1")
            .toString();
    }

    @Override
    public String createDatabaseSQL(String batabase) {
        return "create database if not exists " + batabase;
    }

}
