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

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.connector.storage.CloudEventUtils;
import org.apache.eventmesh.api.connector.storage.StorageConnector;
import org.apache.eventmesh.api.connector.storage.data.CloudEventInfo;
import org.apache.eventmesh.api.connector.storage.data.PullRequest;
import org.apache.eventmesh.api.connector.storage.reply.ReplyOperation;
import org.apache.eventmesh.api.connector.storage.reply.ReplyRequest;

import java.util.ArrayList;
import java.util.List;

import io.cloudevents.CloudEvent;

public class JDBCStorageConnector extends AbstractJDBCStorageConnectorMetadata implements StorageConnector, ReplyOperation {

    private String getTableName(CloudEvent cloudEvent) {
        return cloudEvent.getType();
    }

    @Override
    public void publish(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        String topic = this.getTableName(cloudEvent);
        String sql = this.cloudEventSQLOperation.insertCloudEventSQL(topic);
        List<Object> parameterList = new ArrayList<>();
        this.execute(sql, parameterList);
    }

    @Override
    public void request(CloudEvent cloudEvent, RequestReplyCallback rrCallback, long timeout) throws Exception {
        String topic = this.getTableName(cloudEvent);
        String sql = this.cloudEventSQLOperation.insertCloudEventSQL(topic);
        List<Object> parameterList = new ArrayList<>();
        this.execute(sql, parameterList);
    }

    @Override
    public List<CloudEvent> pull(PullRequest pullRequest) throws Exception {
        String locationEventSQL = this.cloudEventSQLOperation.locationEventSQL(pullRequest.getTopicName());
        //TODO 1. consumerGroup  2.  example_id 3. id 4.consumerGroup
        List<Object> parameter = new ArrayList<>();

        parameter.add(pullRequest.getConsumerGroupName());
        parameter.add(pullRequest.getProcessSign());
        parameter.add(pullRequest.getNextId());
        parameter.add(pullRequest.getConsumerGroupName());
        parameter.clear();
        parameter.add(pullRequest.getNextId());
        parameter.add(pullRequest.getProcessSign());

        long num = this.execute(locationEventSQL, parameter);
        if (num == 0) {
            return null;
        }
        String queryLocationEventSQL = this.cloudEventSQLOperation.queryLocationEventSQL(pullRequest.getTopicName());

        this.query(queryLocationEventSQL, parameter, ResultSetTransformUtils::transformCloudEvent);
        return null;
    }

    @Override
    public void updateOffset(List<CloudEvent> cloudEvents, AbstractContext context) {
        List<Object> parameterList = new ArrayList<>(cloudEvents.size());
        for (CloudEvent cloudEvent : cloudEvents) {
            try {
                String topic = this.getTableName(cloudEvent);
                String sql = this.cloudEventSQLOperation.updateCloudEventOffsetSQL(topic);
                parameterList.add(cloudEvent.getExtension("cloudEventInfoId"));
                long i = this.execute(sql, parameterList);
                if (i != cloudEvents.size()) {
                    messageLogger.warn("");
                }
                parameterList.clear();
            } catch (Exception e) {
                messageLogger.error(e.getMessage(), e);
            }
        }
    }


    @Override
    public boolean reply(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
        String sql = this.cloudEventSQLOperation.updateCloudEventReplySQL(CloudEventUtils.getTopic(cloudEvent));
        List<Object> parameterList = new ArrayList<>();
        parameterList.add(CloudEventUtils.serializeReplyData(cloudEvent));
        parameterList.add(CloudEventUtils.getId(cloudEvent));
        return this.execute(sql, parameterList) == 1;
    }


    @Override
    public void start() {

    }

    @Override
    public void shutdown() {
        druidDataSource.close();
    }

    @Override
    public List<CloudEventInfo> queryReplyCloudEvent(ReplyRequest replyRequest) throws Exception {
        List<Object> parameter = new ArrayList<>();
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 1; i < replyRequest.getIdList().size(); i++) {
            stringBuffer.append('?');
            if (i++ != replyRequest.getIdList().size()) {
                stringBuffer.append(',');
            }
        }
        String sql = this.cloudEventSQLOperation.selectCloudEventByReplySQL(replyRequest.getTopic(), stringBuffer.toString());
        return this.query(sql, parameter, ResultSetTransformUtils::transformCloudEvent);
    }
}
