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

package org.apache.eventmesh.connector.pulsar.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mq.pulsar.PulsarSinkConfig;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PulsarSinkConnector implements Sink {

    private PulsarSinkConfig sinkConfig;

    private Producer<byte[]> producer;

    @Override
    public Class<? extends Config> configClass() {
        return PulsarSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for pulsar source connector
        this.sinkConfig = (PulsarSinkConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for pulsar source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (PulsarSinkConfig) sinkConnectorContext.getSinkConfig();
        doInit();
    }

    private void doInit() throws Exception {
        PulsarClient client = PulsarClient.builder()
            .serviceUrl(sinkConfig.getConnectorConfig().getServiceUrl())
            .build();
        producer = client.newProducer()
            .topic(sinkConfig.getConnectorConfig().getTopic())
            .create();
    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        try {
            producer.close();
        } catch (PulsarClientException e) {
            log.error("close pulsar producer failed", e);
        }
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            try {
                Map props = new HashMap<String, Object>();
                for (String key : connectRecord.getExtensions().keySet()) {
                    props.put(key, connectRecord.getExtension(key));
                }
                MessageId messageId = producer.newMessage()
                    .value((byte[]) connectRecord.getData())
                    .properties(props)
                    .send();
            } catch (Exception e) {
                log.error("put records to pulsar failed", e);
            }
        }
    }

}
