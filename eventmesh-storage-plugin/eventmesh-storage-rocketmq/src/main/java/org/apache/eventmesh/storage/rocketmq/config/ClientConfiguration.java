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

package org.apache.eventmesh.storage.rocketmq.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Config(prefix = "eventMesh.server.rocketmq", path = "classPath://rocketmq-client.properties")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClientConfiguration {

    @ConfigField(field = "namesrvAddr", notEmpty = true)
    @Builder.Default
    private String namesrvAddr = "";

    @ConfigField(field = "username")
    @Builder.Default
    private String clientUserName = "username";

    @ConfigField(field = "password")
    @Builder.Default
    private String clientPass = "password";

    @ConfigField(field = "client.consumeThreadMin")
    @Builder.Default
    private Integer consumeThreadMin = 2;

    @ConfigField(field = "client.consumeThreadMax")
    @Builder.Default
    private Integer consumeThreadMax = 2;

    @ConfigField(field = "client.consumeThreadPoolQueueSize")
    @Builder.Default
    private Integer consumeQueueSize = 10000;

    @ConfigField(field = "client.pullBatchSize")
    @Builder.Default
    private Integer pullBatchSize = 32;

    @ConfigField(field = "client.ackwindow")
    @Builder.Default
    private Integer ackWindow = 1000;

    @ConfigField(field = "client.pubwindow")
    @Builder.Default
    private Integer pubWindow = 100;

    @ConfigField(field = "client.comsumeTimeoutInMin")
    @Builder.Default
    private long consumeTimeout = 0L;

    @ConfigField(field = "client.pollNameServerInterval")
    @Builder.Default
    private Integer pollNameServerInterval = 10 * 1000;

    @ConfigField(field = "client.heartbeatBrokerInterval")
    @Builder.Default
    private Integer heartbeatBrokerInterval = 30 * 1000;

    @ConfigField(field = "client.rebalanceInterval")
    @Builder.Default
    private Integer rebalanceInterval = 20 * 1000;

    @ConfigField(field = "cluster")
    @Builder.Default
    private String clusterName = "";

    @ConfigField(field = "accessKey")
    @Builder.Default
    private String accessKey = "";

    @ConfigField(field = "secretKey")
    @Builder.Default
    private String secretKey = "";
}
