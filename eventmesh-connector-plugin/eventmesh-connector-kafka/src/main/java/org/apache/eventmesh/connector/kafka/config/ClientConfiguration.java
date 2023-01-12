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

package org.apache.eventmesh.connector.kafka.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;

@Config(prefix = "eventMesh.server.kafka", path = "classPath://kafka-client.properties")
public class ClientConfiguration {

    @ConfigFiled(field = "namesrvAddr", notEmpty = true)
    public String namesrvAddr = "";

    @ConfigFiled(field = "username")
    public String clientUserName = "username";

    @ConfigFiled(field = "password")
    public String clientPass = "password";

    @ConfigFiled(field = "client.consumeThreadMin")
    public Integer consumeThreadMin = 2;

    @ConfigFiled(field = "client.consumeThreadMax")
    public Integer consumeThreadMax = 2;

    @ConfigFiled(field = "client.consumeThreadPoolQueueSize")
    public Integer consumeQueueSize = 10000;

    @ConfigFiled(field = "client.pullBatchSize")
    public Integer pullBatchSize = 32;

    @ConfigFiled(field = "client.ackwindow")
    public Integer ackWindow = 1000;

    @ConfigFiled(field = "client.pubwindow")
    public Integer pubWindow = 100;

    @ConfigFiled(field = "client.comsumeTimeoutInMin")
    public long consumeTimeout = 0L;

    @ConfigFiled(field = "client.pollNameServerInterval")
    public Integer pollNameServerInterval = 10 * 1000;

    @ConfigFiled(field = "client.heartbeatBrokerInterval")
    public Integer heartbeatBrokerInterval = 30 * 1000;

    @ConfigFiled(field = "client.rebalanceInterval")
    public Integer rebalanceInterval = 20 * 1000;

    @ConfigFiled(field = "cluster")
    public String clusterName = "";

    @ConfigFiled(field = "accessKey")
    public String accessKey = "";

    @ConfigFiled(field = "secretKey")
    public String secretKey = "";
}