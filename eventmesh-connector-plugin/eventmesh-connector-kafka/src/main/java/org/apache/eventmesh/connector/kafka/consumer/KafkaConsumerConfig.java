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

package org.apache.eventmesh.connector.kafka.consumer;

import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;

public class KafkaConsumerConfig {

    private String boosStrapServer;
    private String groupId;
    private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
    private String valueDeserializer = "org.apache.eventmesh.connector.kafka.common.OpenMessageDeserializer";
    private Integer fetchMinBytes;
    private Integer fetchMaxWaitMs;
    private Integer maxPartitionFetchBytes;
    private Integer sessionTimeoutMs;
    private String autoOffsetReset;
    private Boolean enableAutoCommit = false;
    private Integer maxPollRecords;

    public KafkaConsumerConfig(ConfigurationWrapper configurationWrapper) {
        init(configurationWrapper);
    }

    public void init(ConfigurationWrapper configurationWrapper) {
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.boosStrapServer) != null) {
            this.boosStrapServer = configurationWrapper.getProperty(KafkaConsumerConfigKey.boosStrapServer);
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.groupId) != null) {
            this.groupId = configurationWrapper.getProperty(KafkaConsumerConfigKey.groupId);
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.keyDeserializer) != null) {
            this.keyDeserializer = configurationWrapper.getProperty(KafkaConsumerConfigKey.keyDeserializer);
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.valueDeserializer) != null) {
            this.valueDeserializer = configurationWrapper.getProperty(KafkaConsumerConfigKey.valueDeserializer);
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.fetchMinBytes) != null) {
            this.fetchMinBytes = Integer.valueOf(configurationWrapper.getProperty(KafkaConsumerConfigKey.fetchMinBytes));
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.fetchMaxWaitMs) != null) {
            this.fetchMaxWaitMs = Integer.valueOf(configurationWrapper.getProperty(KafkaConsumerConfigKey.fetchMaxWaitMs));
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.maxPartitionFetchBytes) != null) {
            this.maxPartitionFetchBytes = Integer.valueOf(configurationWrapper.getProperty(KafkaConsumerConfigKey.maxPartitionFetchBytes));
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.sessionTimeoutMs) != null) {
            this.sessionTimeoutMs = Integer.valueOf(configurationWrapper.getProperty(KafkaConsumerConfigKey.sessionTimeoutMs));
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.autoOffsetReset) != null) {
            this.autoOffsetReset = configurationWrapper.getProperty(KafkaConsumerConfigKey.autoOffsetReset);
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.enableAutoCommit) != null) {
            this.enableAutoCommit = Boolean.valueOf(configurationWrapper.getProperty(KafkaConsumerConfigKey.enableAutoCommit));
        }
        if (configurationWrapper.getProperty(KafkaConsumerConfigKey.maxPollRecords) != null) {
            this.maxPollRecords = Integer.valueOf(configurationWrapper.getProperty(KafkaConsumerConfigKey.maxPollRecords));
        }
    }


    public String getBoosStrapServer() {
        return boosStrapServer;
    }

    public void setBoosStrapServer(String boosStrapServer) {
        this.boosStrapServer = boosStrapServer;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getKeyDeserializer() {
        return keyDeserializer;
    }

    public void setKeyDeserializer(String keyDeserializer) {
        this.keyDeserializer = keyDeserializer;
    }

    public String getValueDeserializer() {
        return valueDeserializer;
    }

    public void setValueDeserializer(String valueDeserializer) {
        this.valueDeserializer = valueDeserializer;
    }

    public Integer getFetchMinBytes() {
        return fetchMinBytes;
    }

    public void setFetchMinBytes(Integer fetchMinBytes) {
        this.fetchMinBytes = fetchMinBytes;
    }

    public Integer getFetchMaxWaitMs() {
        return fetchMaxWaitMs;
    }

    public void setFetchMaxWaitMs(Integer fetchMaxWaitMs) {
        this.fetchMaxWaitMs = fetchMaxWaitMs;
    }

    public Integer getMaxPartitionFetchBytes() {
        return maxPartitionFetchBytes;
    }

    public void setMaxPartitionFetchBytes(Integer maxPartitionFetchBytes) {
        this.maxPartitionFetchBytes = maxPartitionFetchBytes;
    }

    public Integer getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(Integer sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public void setAutoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
    }

    public Boolean getEnableAutoCommit() {
        return enableAutoCommit;
    }

    public void setEnableAutoCommit(Boolean enableAutoCommit) {
        this.enableAutoCommit = enableAutoCommit;
    }

    public Integer getMaxPollRecords() {
        return maxPollRecords;
    }

    public void setMaxPollRecords(Integer maxPollRecords) {
        this.maxPollRecords = maxPollRecords;
    }

    public static class KafkaConsumerConfigKey {
        public static String boosStrapServer = "bootstrap.servers";
        public static String groupId = "group.id";
        public static String keyDeserializer = "key.deserializer";
        public static String valueDeserializer = "value.deserializer";
        public static String fetchMinBytes = "fetch.min.bytes";
        public static String fetchMaxWaitMs = "fetch.max.wait.ms";
        public static String maxPartitionFetchBytes = "max.partition.fetch.bytes";
        public static String sessionTimeoutMs = "session.timeout.ms";
        public static String autoOffsetReset = "auto.offset.reset";
        public static String enableAutoCommit = "enable.auto.commit";
        public static String maxPollRecords = "max.poll.records";
    }

}
