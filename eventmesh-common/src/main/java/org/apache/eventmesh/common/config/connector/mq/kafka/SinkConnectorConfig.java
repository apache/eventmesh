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

package org.apache.eventmesh.common.config.connector.mq.kafka;

import lombok.Data;

@Data
public class SinkConnectorConfig {

    private String connectorName = "kafkaSink";
    private String topic = "TopicTest";
    private String ack = "all";
    private String bootstrapServers = "127.0.0.1:9092";
    private String keyConverter = "org.apache.kafka.common.serialization.StringSerializer";
    private String valueConverter = "org.apache.kafka.common.serialization.StringSerializer";
    private String maxRequestSize = "1048576";
    private String bufferMemory = "33554432";
    private String batchSize = "16384";
    private String lingerMs = "0";
    private String requestTimeoutMs = "30000";
    private String maxInFightRequestsPerConnection = "5";
    private String retries = "0";
    private String compressionType = "none";
}
