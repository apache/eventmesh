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

package org.apache.eventmesh.connector.rocketmq.source.connector;

import org.apache.eventmesh.connector.rocketmq.source.config.RocketMQSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RocketMQSourceConnector implements Source {

    private RocketMQSourceConfig sourceConfig;

    private final DefaultLitePullConsumer consumer = new DefaultLitePullConsumer();

    @Override
    public Class<? extends Config> configClass() {
        return RocketMQSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for rocketmq source connector
        this.sourceConfig = (RocketMQSourceConfig) config;
        consumer.setConsumerGroup(sourceConfig.getPubSubConfig().getGroup());
        consumer.setNamesrvAddr(sourceConfig.getConnectorConfig().getNameserver());
    }

    @Override
    public void start() throws Exception {
        consumer.subscribe(sourceConfig.getConnectorConfig().getTopic(), "*");
        consumer.start();
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
        consumer.unsubscribe(sourceConfig.getConnectorConfig().getTopic());
        consumer.shutdown();
    }

    @Override
    public List<ConnectRecord> poll() {
        List<MessageExt> messageExts = consumer.poll();
        List<ConnectRecord> connectRecords = new ArrayList<>(messageExts.size());
        for (MessageExt messageExt : messageExts) {
            Long timestamp = System.currentTimeMillis();
            byte[] body = messageExt.getBody();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            RecordPartition recordPartition = convertToRecordPartition(messageExt.getTopic(),
                messageExt.getBrokerName(), messageExt.getQueueId());
            RecordOffset recordOffset = convertToRecordOffset(messageExt.getQueueOffset());
            ConnectRecord connectRecord = new ConnectRecord(recordPartition, recordOffset, timestamp, bodyStr);
            connectRecord.addExtension("topic", messageExt.getTopic());
            connectRecords.add(connectRecord);
        }
        return connectRecords;
    }

    public static RecordOffset convertToRecordOffset(Long offset) {
        Map<String, String> offsetMap = new HashMap<>();
        offsetMap.put("queueOffset", offset + "");
        return new RecordOffset(offsetMap);
    }

    public static RecordPartition convertToRecordPartition(String topic, String brokerName, int queueId) {
        Map<String, String> map = new HashMap<>();
        map.put("topic", topic);
        map.put("brokerName", brokerName);
        map.put("queueId", queueId + "");
        return new RecordPartition(map);
    }
}
