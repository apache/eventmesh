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

package org.apache.eventmesh.connector.kafka.sink.config;

import lombok.Data;

@Data
public class SinkConnectorConfig {
    private String connectorName;
    private String tasksMax;
    private String topic;
    private String bootstrapServers;
    private String groupID;
    private String keyConverter;
    private String valueConverter;
    private String offsetFlushIntervalMS;
    private String offsetStorageTopic;
    private String offsetStorageReplicationFactor;
    private String configStorageTopic;
    private String configStorageReplicationFactor;
    private String statusStorageTopic;
    private String statusStorageReplicationFactor;
    private String offsetCommitTimeoutMS;
    private String offsetCommitIntervalMS;
    private String heartbeatIntervalMS;
    private String sessionTimeoutMS;
}
