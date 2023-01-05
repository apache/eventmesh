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

package org.apache.eventmesh.connector.storage.jdbc;

import org.apache.eventmesh.api.connector.storage.data.CloudEventInfo;
import org.apache.eventmesh.api.connector.storage.data.ConsumerGroupInfo;
import org.apache.eventmesh.api.connector.storage.data.TopicInfo;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ResultSetTransformUtils {

    public static String transformTableName(ResultSet resultSet) throws SQLException {
    	String topic = resultSet.getString(1);
        return topic.substring(12);
    }

    public static ConsumerGroupInfo transformConsumerGroup(ResultSet resultSet) {
        return new ConsumerGroupInfo();
    }

    public static CloudEventInfo transformCloudEvent(ResultSet resultSet) throws SQLException {
    	CloudEventInfo cloudEventInfo = new CloudEventInfo();
    	cloudEventInfo.setCloudEventInfoId(resultSet.getLong("cloud_event_info_id"));
    	cloudEventInfo.setCloudEventTopic(resultSet.getString("cloud_event_topic"));
        //cloudEventInfo.setCloudEventId(resultSet.getLong("cloud_event_id"));
    	cloudEventInfo.setCloudEventStorageNodeAdress(resultSet.getString("cloud_event_storage_node_adress"));
    	cloudEventInfo.setCloudEventType(resultSet.getString("cloud_event_type"));
    	cloudEventInfo.setCloudEventProducerGroupName(resultSet.getString("cloud_event_producer_group_name"));
    	cloudEventInfo.setCloudEventSource(resultSet.getString("cloud_event_source"));
    	cloudEventInfo.setCloudEventContentType(resultSet.getString("cloud_event_content_type"));
    	cloudEventInfo.setCloudEventData(resultSet.getString("cloud_event_data"));
    	cloudEventInfo.setCloudEventReplyData(resultSet.getString("cloud_event_reply_data"));
    	return cloudEventInfo;
    }


    public static TopicInfo transformTopicInfo(ResultSet resultSet) throws SQLException {
    	TopicInfo topicInfo = new TopicInfo();
    	topicInfo.setCurrentId(resultSet.getLong("cloud_event_info_id"));
    	topicInfo.setTopicName(resultSet.getString("cloud_event_topic"));
    	topicInfo.setTopicName(topicInfo.getTopicName().substring(12));
    	topicInfo.setDbTablesName(resultSet.getString("cloud_event_topic"));
        return null;
    }
}
