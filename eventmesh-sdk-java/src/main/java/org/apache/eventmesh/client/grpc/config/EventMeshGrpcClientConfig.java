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
    private String serverAddr = "";

    @Builder.Default
    private int serverPort = 10205;

    @Builder.Default
    private String env = "";

    @Builder.Default
    private String consumerGroup = "DefaultConsumerGroup";

    @Builder.Default
    private String producerGroup = "DefaultProducerGroup";

    @Builder.Default
    private String idc = "";

    @Builder.Default
    private String ip = "127.0.0.1";

    @Builder.Default
    private String pid ="0";

    @Builder.Default
    private String sys = "sys123";

    @Builder.Default
    private String userName = "";

    @Builder.Default
    private String password = "";

    @Builder.Default
    private String language = "JAVA";

    @Builder.Default
    private boolean useTls = false;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClientConfig={")
                .append("ServerAddr=").append(serverAddr).append(",")
                .append("ServerPort=").append(serverPort).append(",")
                .append("env=").append(env).append(",")
                .append("idc=").append(idc).append(",")
                .append("producerGroup=").append(producerGroup).append(",")
                .append("consumerGroup=").append(consumerGroup).append(",")
                .append("ip=").append(ip).append(",")
                .append("pid=").append(pid).append(",")
                .append("sys=").append(sys).append(",")
                .append("userName=").append(userName).append(",")
                .append("password=").append("***").append(",")
                .append("useTls=").append(useTls).append("}");
        return sb.toString();
    }
}
