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
public class SourceConnectorConfig {

    private String connectorName = "kafkaSource";
    private String topic = "TopicTest";
    private String bootstrapServers = "127.0.0.1:9092";
    private String groupID = "kafkaSource";
    private String keyConverter = "org.apache.kafka.common.serialization.StringDeserializer";
    private String valueConverter = "org.apache.kafka.common.serialization.StringDeserializer";
    private String autoCommitIntervalMS = "1000";
    private String enableAutoCommit = "false";
    private String sessionTimeoutMS = "10000";
    private String maxPollRecords = "1000";
    private int pollTimeOut = 100;
}
