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

import static org.apache.eventmesh.storage.kafka.common.EventMeshConstants.DEFAULT_TIMEOUT_IN_SECONDS;

import org.apache.eventmesh.api.admin.AbstractAdmin;
import org.apache.eventmesh.api.admin.TopicProperties;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.storage.kafka.config.ClientConfiguration;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public List<TopicProperties> getTopic() {
        try (Admin client = Admin.create(kafkaProps)) {
            Set<String> topicList = client.listTopics().names().get(DEFAULT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
            Map<String, KafkaFuture<TopicDescription>> topicDescriptionFutures = client.describeTopics(topicList).values();
            List<TopicProperties> result = new ArrayList<>();
            for (Entry<String, KafkaFuture<TopicDescription>> entry : topicDescriptionFutures.entrySet()) {
                String topicName = entry.getKey();
                TopicDescription topicDescription = entry.getValue().get(DEFAULT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

                long messageCount = topicDescription.partitions().stream()
                        .mapToInt(TopicPartitionInfo::partition)
                        .mapToLong(partition -> {
                            try {
                                return getMsgCount(topicName, partition, client);
                            } catch (TimeoutException e) {
                                log.error("Failed to get msg offset when listing topics. Kafka response timed out in {} seconds.",
                                        DEFAULT_TIMEOUT_IN_SECONDS);
                                throw new RuntimeException(e);
                            } catch (ExecutionException | InterruptedException e) {
                                log.error("Failed to get msg offset when listing topics.", e);
                                throw new RuntimeException(e);
                            }
                        })
                        .sum();

                result.add(new TopicProperties(topicName, messageCount));
            }
            result.sort(Comparator.comparing(t -> t.name));
            return result;
        } catch (TimeoutException e) {
            log.error("Failed to list topics. Kafka response timed out in {} seconds.", DEFAULT_TIMEOUT_IN_SECONDS);
            throw new RuntimeException(e);
        } catch (Exception e) {
            log.error("Failed to list topics.", e);
            throw new RuntimeException(e);
        }
    }

    private long getMsgCount(String topicName, int partition, Admin client) throws ExecutionException, InterruptedException, TimeoutException {
        TopicPartition topicPartition = new TopicPartition(topicName, partition);
        long earliestOffset = getOffset(topicPartition, OffsetSpec.earliest(), client);
        long latestOffset = getOffset(topicPartition, OffsetSpec.latest(), client);
        return latestOffset - earliestOffset;
    }

    private long getOffset(TopicPartition topicPartition, OffsetSpec offsetSpec,
                           Admin client) throws ExecutionException, InterruptedException, TimeoutException {
        Map<TopicPartition, OffsetSpec> offsetSpecMap = Collections.singletonMap(topicPartition, offsetSpec);
        Map<TopicPartition, ListOffsetsResultInfo> offsetResultMap =
                client.listOffsets(offsetSpecMap).all().get(DEFAULT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        return offsetResultMap.get(topicPartition).offset();
    }

    @Override
    public void createTopic(String topicName) {
        try (Admin client = Admin.create(kafkaProps)) {
            NewTopic newTopic = new NewTopic(topicName, topicProps.get("partitionNum"), topicProps.get("replicationFactorNum").shortValue());
            client.createTopics(Collections.singletonList(newTopic)).all().get(DEFAULT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Failed to create topic. Kafka response timed out in {} seconds.", DEFAULT_TIMEOUT_IN_SECONDS);
        } catch (Exception e) {
            log.error("Failed to create topic.", e);
        }
    }

    @Override
    public void deleteTopic(String topicName) {
        try (Admin client = Admin.create(kafkaProps)) {
            client.deleteTopics(Collections.singletonList(topicName)).all().get(DEFAULT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Failed to delete topic. Kafka response timed out in {} seconds.", DEFAULT_TIMEOUT_IN_SECONDS);
        } catch (Exception e) {
            log.error("Failed to delete topic.", e);
        }
    }

}
