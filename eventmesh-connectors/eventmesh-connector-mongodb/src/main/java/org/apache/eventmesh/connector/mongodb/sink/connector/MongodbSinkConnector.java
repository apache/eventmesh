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

package org.apache.eventmesh.connector.mongodb.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.connector.mongodb.sink.client.Impl.MongodbSinkClient;
import org.apache.eventmesh.connector.mongodb.sink.client.MongodbReplicaSetSinkClient;
import org.apache.eventmesh.connector.mongodb.sink.client.MongodbStandaloneSinkClient;
import org.apache.eventmesh.common.config.connector.rdb.mongodb.MongodbSinkConfig;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.List;

import io.cloudevents.CloudEvent;

import com.mongodb.connection.ClusterType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MongodbSinkConnector implements Sink {

    private MongodbSinkConfig sinkConfig;

    private MongodbSinkClient client;

    @Override
    public Class<? extends Config> configClass() {
        return MongodbSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.sinkConfig = (MongodbSinkConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (MongodbSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    private void doInit() {
        String connectorType = sinkConfig.getConnectorConfig().getConnectorType();
        if (connectorType.equals(ClusterType.STANDALONE.name())) {
            this.client = new MongodbStandaloneSinkClient(sinkConfig.getConnectorConfig());
        }
        if (connectorType.equals(ClusterType.REPLICA_SET.name())) {
            this.client = new MongodbReplicaSetSinkClient(sinkConfig.getConnectorConfig());
        }
        client.init();
    }

    @Override
    public void start() throws Exception {
        this.client.start();
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() throws Exception {
        this.client.stop();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        try {
            for (ConnectRecord connectRecord : sinkRecords) {
                CloudEvent event = CloudEventUtil.convertRecordToEvent(connectRecord);
                client.publish(event);
                log.debug("Produced message to event:{}}", event);
            }
        } catch (Exception e) {
            log.error("Failed to produce message:{}", e.getMessage());
        }
    }
}
