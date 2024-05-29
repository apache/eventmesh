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

package org.apache.eventmesh.connector.pulsar.source.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mq.pulsar.PulsarSourceConfig;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.common.remote.offset.pulsar.PulsarRecordPartition;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.Messages;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PulsarSourceConnector implements Source {

    private PulsarSourceConfig sourceConfig;

    private Consumer consumer;

    @Override
    public Class<? extends Config> configClass() {
        return PulsarSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for rocketmq source connector
        this.sourceConfig = (PulsarSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (PulsarSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    private void doInit() throws Exception {
        PulsarClient client = PulsarClient.builder()
            .serviceUrl(sourceConfig.getConnectorConfig().getServiceUrl())
            .build();
        consumer = client.newConsumer()
            .topic(sourceConfig.connectorConfig.getTopic())
            .subscriptionName(sourceConfig.getPubSubConfig().getGroup())
            .subscribe();
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        try {
            consumer.close();
        } catch (PulsarClientException e) {
            log.error("close pulsar consumer failed", e);
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>();
        try {
            Messages messages = consumer.batchReceive();
            for (Object msg : messages) {
                Long timestamp = System.currentTimeMillis();
                Message message = (Message) msg;
                byte[] body = message.getData();
                String bodyStr = new String(body, StandardCharsets.UTF_8);
                PulsarRecordPartition partition = new PulsarRecordPartition();
                partition.setTopic(consumer.getTopic());
                partition.setQueueId(message.getSequenceId());
                partition.setClazz(partition.getRecordPartitionClass());
                ConnectRecord connectRecord = new ConnectRecord(partition, null, timestamp, bodyStr);
                connectRecord.addExtension("topic", consumer.getTopic());
                connectRecords.add(connectRecord);
            }
            consumer.acknowledge(messages);
        } catch (PulsarClientException e) {
            log.error("consumer msg from pulsar failed", e);
        }
        return connectRecords;
    }

}
