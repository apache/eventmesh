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

package org.apache.eventmesh.connector.kafka.producer;

import org.apache.eventmesh.connector.kafka.config.ConfigurationWrapper;

public class KafkaProducerConfig {

    private String servers;
    private Integer acks;
    private Integer bufferMemory;
    private Integer retries;
    private Integer batchSize;
    private String clientId;
    private String keySerialize = "org.apache.kafka.common.serialization.StringSerializer";
    private String valueSerialize = "org.apache.eventmesh.connector.kafka.common.OpenMessageSerializer";


    public KafkaProducerConfig(ConfigurationWrapper configurationWrapper) {
        init(configurationWrapper);
    }


    public String getServers() {
        return servers;
    }

    public Integer getAcks() {
        return acks;
    }

    public Integer getBufferMemory() {
        return bufferMemory;
    }

    public Integer getRetries() {
        return retries;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public String getClientId() {
        return clientId;
    }

    public String getKeySerialize() {
        return keySerialize;
    }

    public String getValueSerialize() {
        return valueSerialize;
    }

    private void init(ConfigurationWrapper configurationWrapper) {
        if (configurationWrapper.getProperty(ProducerConfigKey.servers) != null) {
            servers = configurationWrapper.getProperty(ProducerConfigKey.servers);
        }
        if (configurationWrapper.getProperty(ProducerConfigKey.acks) != null) {
            acks = Integer.valueOf(configurationWrapper.getProperty(ProducerConfigKey.acks));
        }
        if (configurationWrapper.getProperty(ProducerConfigKey.bufferMemory) != null) {
            bufferMemory = Integer.valueOf(configurationWrapper.getProperty(ProducerConfigKey.bufferMemory));
        }
        if (configurationWrapper.getProperty(ProducerConfigKey.retries) != null) {
            retries = Integer.valueOf(configurationWrapper.getProperty(ProducerConfigKey.retries));
        }
        if (configurationWrapper.getProperty(ProducerConfigKey.batchSize) != null) {
            batchSize = Integer.valueOf(configurationWrapper.getProperty(ProducerConfigKey.batchSize));
        }
        if (configurationWrapper.getProperty(ProducerConfigKey.clientId) != null) {
            clientId = configurationWrapper.getProperty(ProducerConfigKey.clientId);
        }
        if (configurationWrapper.getProperty(ProducerConfigKey.keySerializer) != null) {
            keySerialize = configurationWrapper.getProperty(ProducerConfigKey.keySerializer);
        }
        if (configurationWrapper.getProperty(ProducerConfigKey.valueSerializer) != null) {
            valueSerialize = configurationWrapper.getProperty(ProducerConfigKey.valueSerializer);
        }
    }


    public static class ProducerConfigKey {
        public final static String servers = "bootstrap.servers";
        public final static String acks = "acks";
        public final static String bufferMemory = "buffer.memory";
        public final static String retries = "retries";
        public final static String batchSize = "batch.size";
        public final static String clientId = "client.id";
        public final static String keySerializer = "key.serializer";
        public final static String valueSerializer = "value.serializer";
    }
}
