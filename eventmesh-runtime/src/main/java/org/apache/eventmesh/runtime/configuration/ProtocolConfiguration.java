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

package org.apache.eventmesh.runtime.configuration;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh.server.protocol")
public class ProtocolConfiguration {

    @ConfigField(field = "unified.port", notNull = true, beNumber = true)
    private int unifiedPort = 10000;

    @ConfigField(field = "http.enabled")
    private boolean httpEnabled = true;

    @ConfigField(field = "grpc.enabled")
    private boolean grpcEnabled = true;

    @ConfigField(field = "tcp.enabled")
    private boolean tcpEnabled = true;

    public int getHttpPort() {
        return unifiedPort;
    }

    public int getGrpcPort() {
        return unifiedPort;
    }

    public int getTcpPort() {
        return unifiedPort;
    }

    public void setHttpPort(int port) {
        this.unifiedPort = port;
    }

    public void setGrpcPort(int port) {
        this.unifiedPort = port;
    }

    public void setTcpPort(int port) {
        this.unifiedPort = port;
    }
}
