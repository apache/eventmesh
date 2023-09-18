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

package org.apache.eventmesh.client.grpc.config;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventMeshGrpcClientConfig {

    @Builder.Default
    private String serverAddr = "localhost";

    @Builder.Default
    private int serverPort = 10_205;

    @Builder.Default
    private String env = "env";

    @Builder.Default
    private String consumerGroup = "DefaultConsumerGroup";

    @Builder.Default
    private String producerGroup = "DefaultProducerGroup";

    @Builder.Default
    private String idc = "default";

    @Builder.Default
    private String sys = "sys123";

    @Builder.Default
    private String userName = "username";

    @Builder.Default
    private String password = "passwd";

    @Builder.Default
    private String language = "JAVA";

    @Builder.Default
    private boolean useTls = false;

    @Builder.Default
    private long timeOut = 5_000;

    @Override
    public String toString() {
        return "ClientConfig={ServerAddr="
                + serverAddr
                + ",ServerPort="
                + serverPort
                + ",env="
                + env
                + ",idc="
                + idc
                + ",producerGroup="
                + producerGroup
                + ",consumerGroup="
                + consumerGroup
                + ",sys="
                + sys
                + ",userName="
                + userName
                + ",password=***"
                + ",useTls="
                + useTls
                + ",timeOut="
                + timeOut
                + "}";
    }
}
