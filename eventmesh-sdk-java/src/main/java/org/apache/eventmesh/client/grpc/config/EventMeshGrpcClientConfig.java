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

import org.apache.eventmesh.client.common.ClientConfig;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class EventMeshGrpcClientConfig extends ClientConfig {

    @Builder.Default
    private String serverAddr = "127.0.0.1";

    @Builder.Default
    private int serverPort = 10205;

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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ClientConfig={")
                .append("ServerAddr=").append(this.serverAddr).append(",")
                .append("ServerPort=").append(this.serverPort).append(",")
                .append("env=").append(this.env).append(",")
                .append("idc=").append(this.idc).append(",")
                .append("producerGroup=").append(this.producerGroup).append(",")
                .append("consumerGroup=").append(this.consumerGroup).append(",")
                .append("sys=").append(this.sys).append(",")
                .append("userName=").append(this.userName).append(",")
                .append("password=").append("***").append(",")
                .append("useTls=").append(this.useTls).append("}");
        return sb.toString();
    }
}
