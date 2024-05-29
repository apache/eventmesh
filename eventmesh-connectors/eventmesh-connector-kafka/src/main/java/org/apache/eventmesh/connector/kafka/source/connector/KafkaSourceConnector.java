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

package org.apache.eventmesh.connector.kafka.source.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.mq.kafka.KafkaSourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordOffset;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.common.remote.offset.kafka.KafkaRecordOffset;
import org.apache.eventmesh.common.remote.offset.kafka.KafkaRecordPartition;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public class KafkaSourceConnector implements Source {

    private KafkaSourceConfig sourceConfig;

    private KafkaConsumer<String, String> kafkaConsumer;

    private int pollTimeOut = 100;

    @Override
    public Class<? extends Config> configClass() {
        return KafkaSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        this.sourceConfig = (KafkaSourceConfig) config;
        doInit();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (KafkaSourceConfig) sourceConnectorContext.getSourceConfig();
        doInit();
    }

    private void doInit() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, sourceConfig.getConnectorConfig().getBootstrapServers());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, sourceConfig.getConnectorConfig().getKeyConverter());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, sourceConfig.getConnectorConfig().getValueConverter());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, sourceConfig.getConnectorConfig().getGroupID());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, sourceConfig.getConnectorConfig().getEnableAutoCommit());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, sourceConfig.getConnectorConfig().getMaxPollRecords());
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, sourceConfig.getConnectorConfig().getAutoCommitIntervalMS());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sourceConfig.getConnectorConfig().getSessionTimeoutMS());
        this.pollTimeOut = sourceConfig.getConnectorConfig().getPollTimeOut();
        this.kafkaConsumer = new KafkaConsumer<>(props);
    }

    @Override
    public void start() throws Exception {
        kafkaConsumer.subscribe(Collections.singleton(sourceConfig.getConnectorConfig().getTopic()));
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
        kafkaConsumer.unsubscribe();
    }

    @Override
    public List<ConnectRecord> poll() {
        ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(pollTimeOut));
        List<ConnectRecord> connectRecords = new ArrayList<>(records.count());
        for (ConsumerRecord<String, String> record : records) {
            Long timestamp = System.currentTimeMillis();
            String key = record.key();
            String value = record.value();
            RecordPartition recordPartition = convertToRecordPartition(record.topic(), record.partition());
            RecordOffset recordOffset = convertToRecordOffset(record.offset());
            ConnectRecord connectRecord = new ConnectRecord(recordPartition, recordOffset, timestamp, value);
            connectRecord.addExtension("key", key);
            connectRecords.add(connectRecord);
        }
        kafkaConsumer.commitAsync();
        return connectRecords;
    }

    public static RecordOffset convertToRecordOffset(Long offset) {
        KafkaRecordOffset recordOffset = new KafkaRecordOffset();
        recordOffset.setOffset(offset);
        recordOffset.setClazz(recordOffset.getRecordOffsetClass());
        return recordOffset;
    }

    public static RecordPartition convertToRecordPartition(String topic, int partition) {
        KafkaRecordPartition recordPartition = new KafkaRecordPartition();
        recordPartition.setTopic(topic);
        recordPartition.setPartition(partition);
        recordPartition.setClazz(recordPartition.getRecordPartitionClass());
        return recordPartition;
    }
}
