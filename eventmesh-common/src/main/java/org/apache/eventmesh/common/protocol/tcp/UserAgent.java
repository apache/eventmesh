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

package org.apache.eventmesh.common.protocol.tcp;

import lombok.Builder;
import lombok.Data;

import java.util.Objects;

@Data
@Builder
public class UserAgent {

    private String env;
    private String subsystem;
    private String path;
    private int    pid;
    private String host;
    private int    port;
    private String version;
    private String username;
    private String password;
    private String idc;
    private String producerGroup;
    private String consumerGroup;
    private String purpose;
    @Builder.Default
    private int    unack = 0;

    public UserAgent() {
    }

    public UserAgent(String env, String subsystem, String path, int pid, String host, int port, String version,
                     String username, String password, String idc, String producerGroup, String consumerGroup,
                     String purpose, int unack) {
        this.env = env;
        this.subsystem = subsystem;
        this.path = path;
        this.pid = pid;
        this.host = host;
        this.port = port;
        this.version = version;
        this.username = username;
        this.password = password;
        this.idc = idc;
        this.producerGroup = producerGroup;
        this.consumerGroup = consumerGroup;
        this.purpose = purpose;
        this.unack = unack;
    }

    @Override
    public String toString() {
        return String.format(
            "UserAgent{env='%s', subsystem='%s', path='%s', pid=%d, host='%s', port=%d, version='%s', idc='%s', purpose='%s', unack='%d'}",
            env, subsystem, path, pid, host, port, version, idc, purpose, unack);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserAgent userAgent = (UserAgent) o;

        if (pid != userAgent.pid) return false;
        if (port != userAgent.port) return false;
        if (unack != userAgent.unack) return false;
        if (!Objects.equals(subsystem, userAgent.subsystem)) return false;
        if (!Objects.equals(path, userAgent.path)) return false;
        if (!Objects.equals(host, userAgent.host)) return false;
        if (!Objects.equals(purpose, userAgent.purpose)) return false;
        if (!Objects.equals(version, userAgent.version)) return false;
        if (!Objects.equals(username, userAgent.username)) return false;
        if (!Objects.equals(password, userAgent.password)) return false;
        if (!Objects.equals(env, userAgent.env)) return false;
        return Objects.equals(idc, userAgent.idc);
    }

    @Override
    public int hashCode() {
        int result = subsystem != null ? subsystem.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + pid;
        result = 31 * result + (host != null ? host.hashCode() : 0);
        result = 31 * result + (purpose != null ? purpose.hashCode() : 0);
        result = 31 * result + port;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (idc != null ? idc.hashCode() : 0);
        result = 31 * result + (env != null ? env.hashCode() : 0);
        result = 31 * result + unack;
        return result;
    }
}
