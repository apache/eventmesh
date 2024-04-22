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

package org.apache.eventmesh.connector.rocketmq.sink.connector;

import org.apache.eventmesh.connector.rocketmq.sink.config.RocketMQSinkConfig;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RocketMQSinkConnector implements Sink, ConnectorCreateService<Sink> {

    private RocketMQSinkConfig sinkConfig;

    private final DefaultMQProducer producer = new DefaultMQProducer();

    @Override
    public Class<? extends Config> configClass() {
        return RocketMQSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for rocketmq source connector
        this.sinkConfig = (RocketMQSinkConfig) config;
        producer.setProducerGroup(sinkConfig.getPubSubConfig().getGroup());
        producer.setNamesrvAddr(sinkConfig.getConnectorConfig().getNameServer());
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for rocketmq source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (RocketMQSinkConfig) sinkConnectorContext.getSinkConfig();
        producer.setProducerGroup(sinkConfig.getPubSubConfig().getGroup());
        producer.setNamesrvAddr(sinkConfig.getConnectorConfig().getNameServer());
    }

    @Override
    public void start() throws Exception {
        producer.start();
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
        producer.shutdown();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            Message message = convertRecordToMessage(connectRecord);
            try {
                SendResult sendResult = producer.send(message);
            } catch (InterruptedException e) {
                Thread currentThread = Thread.currentThread();
                log.warn("[RocketMQSinkConnector] Interrupting thread {} due to exception {}",
                    currentThread.getName(), e.getMessage());
                currentThread.interrupt();
            } catch (Exception e) {
                log.error("[RocketMQSinkConnector] sendResult has error : ", e);
            }
        }
    }

    public Message convertRecordToMessage(ConnectRecord connectRecord) {
        Message message = new Message();
        message.setTopic(this.sinkConfig.getConnectorConfig().getTopic());
        message.setBody((byte[]) connectRecord.getData());
        for (String key : connectRecord.getExtensions().keySet()) {
            MessageAccessor.putProperty(message, key, connectRecord.getExtension(key));
        }
        return message;
    }

    @Override
    public Sink create() {
        return new RocketMQSinkConnector();
    }
}
