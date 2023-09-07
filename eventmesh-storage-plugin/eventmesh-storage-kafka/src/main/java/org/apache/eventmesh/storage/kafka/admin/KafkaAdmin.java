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

package org.apache.eventmesh.storage.kafka.admin;

import org.apache.eventmesh.api.admin.AbstractAdmin;
import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.storage.kafka.config.ClientConfiguration;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KafkaAdmin extends AbstractAdmin {

    // properties for kafka admin client
    private static final Properties kafkaProps = new Properties();

    // properties for topic management
    private static final Map<String, Integer> topicProps = new HashMap<>();

    public KafkaAdmin() {
        super(new AtomicBoolean(false));

        ConfigService configService = ConfigService.getInstance();
        ClientConfiguration clientConfiguration = configService.buildConfigInstance(ClientConfiguration.class);

        kafkaProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, clientConfiguration.getNamesrvAddr());
        topicProps.put("partitionNum", clientConfiguration.getPartitions());
        topicProps.put("replicationFactorNum", (int) clientConfiguration.getReplicationFactors());
    }

    @Override
    public List<TopicProperties> getTopic() throws Exception {
        Admin client = Admin.create(kafkaProps);
        Set<String> topicList = client.listTopics().names().get();
        try {
            List<TopicProperties> result = new ArrayList<>();
            for (String topic : topicList) {
                DescribeTopicsResult describeTopicsResult = client.describeTopics(Collections.singletonList(topic));
                TopicDescription topicDescription = describeTopicsResult.values().get(topic).get();

                long messageCount = topicDescription.partitions().stream()
                    .mapToInt(TopicPartitionInfo::partition)
                    .mapToLong(partition -> {
                        try {
                            return getMsgCount(topic, partition, client);
                        } catch (ExecutionException | InterruptedException e) {
                            log.error("Failed to get last offset", e);
                            throw new RuntimeException(e);
                        }
                    })
                    .sum();

                result.add(new TopicProperties(topic, messageCount));
            }
            result.sort(Comparator.comparing(t -> t.name));
            return result;
        } finally {
            client.close();
        }
    }

    private long getMsgCount(String topic, int partition, Admin client) throws ExecutionException, InterruptedException {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        long earliestOffset = getOffset(topicPartition, OffsetSpec.earliest(), client);
        long latestOffset = getOffset(topicPartition, OffsetSpec.latest(), client);
        return latestOffset - earliestOffset;
    }

    private long getOffset(TopicPartition topicPartition, OffsetSpec offsetSpec, Admin client) throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetSpec> offsetSpecMap = Collections.singletonMap(topicPartition, offsetSpec);
        Map<TopicPartition, ListOffsetsResultInfo> offsetResultMap = client.listOffsets(offsetSpecMap).all().get();
        return offsetResultMap.get(topicPartition).offset();
    }

    @Override
    public void createTopic(String topicName) {
        Admin client = Admin.create(kafkaProps);
        NewTopic newTopic = new NewTopic(topicName, topicProps.get("partitionNum"), topicProps.get("replicationFactorNum").shortValue());

        Collection<NewTopic> newTopicList = new ArrayList<>();
        newTopicList.add(newTopic);
        try {
            client.createTopics(newTopicList).all().get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to create topic", e);
        } finally {
            client.close();
        }
    }

    @Override
    public void deleteTopic(String topicName) {
    }

    @Override
    public List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception {
        return null;
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
    }

}
