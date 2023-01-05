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

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

public class ClientConfiguration {

    public String namesrvAddr = "";
    public String clientUserName = "username";
    public String clientPass = "password";
    public Integer consumeThreadMin = 2;
    public Integer consumeThreadMax = 2;
    public Integer consumeQueueSize = 10000;
    public Integer pullBatchSize = 32;
    public Integer ackWindow = 1000;
    public Integer pubWindow = 100;
    public long consumeTimeout = 0L;
    public Integer pollNameServerInterval = 10 * 1000;
    public Integer heartbeatBrokerInterval = 30 * 1000;
    public Integer rebalanceInterval = 20 * 1000;
    public String clusterName = "";
    public String accessKey = "";
    public String secretKey = "";

    public void init() {
        String namesrvAddrStr = ConfigurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_KAFKA_SERVER_PORT);
        Preconditions.checkState(StringUtils.isNotEmpty(namesrvAddrStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_KAFKA_SERVER_PORT));
        namesrvAddr = StringUtils.trim(namesrvAddrStr);
    }

    static class ConfKeys {

        public static String KEYS_EVENTMESH_KAFKA_SERVER_PORT = "eventMesh.server.kafka.port";

    }
}