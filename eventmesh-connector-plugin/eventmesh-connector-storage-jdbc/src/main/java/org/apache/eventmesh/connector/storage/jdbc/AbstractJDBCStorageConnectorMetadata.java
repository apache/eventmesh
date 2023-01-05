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

import org.apache.eventmesh.api.connector.storage.CloudEventUtils;
import org.apache.eventmesh.api.connector.storage.StorageConnectorMetedata;
import org.apache.eventmesh.api.connector.storage.data.ConsumerGroupInfo;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.data.TopicInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AbstractJDBCStorageConnectorMetadata extends AbstractJDBCStorageConnector
    implements StorageConnectorMetedata {

    @Override
    public Set<String> getTopic() throws Exception {
        List<String> tableNames = this.query(this.baseSQLOperation.queryCloudEventTablesSQL(), null,
            ResultSetTransformUtils::transformTableName);
        return new HashSet<>(tableNames);
    }

    @Override
    public List<ConsumerGroupInfo> getConsumerGroupInfo() throws Exception {
        return this.query(this.consumerGroupSQLOperation.selectConsumerGroupSQL(), ResultSetTransformUtils::transformConsumerGroup);
    }

    @Override
    public List<TopicInfo> geTopicInfos(List<PullRequest> pullRequests) throws Exception {
        StringBuffer sqlsb = new StringBuffer();
        int index = 1;
        for (PullRequest pullRequest : pullRequests) {
            String sql = this.cloudEventSQLOperation.selectNoConsumptionMessageSQL(pullRequest.getTopicName(),pullRequest.getConsumerGroupName());
            sqlsb.append(sql);
            if (index++ < pullRequests.size()) {
                sqlsb.append(" union all ");
            }
        }
        return this.query(sqlsb.toString(), null, ResultSetTransformUtils::transformTopicInfo);
    }
    
    public List<TopicInfo> geTopicInfos(Set<String> topics,String key) throws Exception {
    	key = CloudEventUtils.checkConsumerGroupName(key);
        StringBuffer sqlsb = new StringBuffer();
        int index = 1;
        for (String topic : topics) {
        	if(topic.startsWith("cloud_event_")) {
        		topic = topic.substring(12);
        	}
            String sql = this.cloudEventSQLOperation.selectNoConsumptionMessageSQL(topic,key);
            sqlsb.append(sql);
            if (index++ < topics.size()) {
                sqlsb.append(" union all ");
            }
        }
        return this.query(sqlsb.toString(), null, ResultSetTransformUtils::transformTopicInfo);
    }

    @Override
    public int createTopic(TopicInfo topicInfo) throws Exception {
        return (int) this.execute(this.cloudEventSQLOperation.createCloudEventSQL(topicInfo.getTopicName()), null);
    }

    @Override
    public int createConsumerGroupInfo(ConsumerGroupInfo consumerGroupInfo) throws Exception {
        return (int) this.execute(this.consumerGroupSQLOperation.insertConsumerGroupSQL(), null);
    }

}
