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

package org.apache.eventmesh.runtime.admin.response.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetClientResponse {

    private String env;
    private String subsystem;
    private String url;
    private String pid;
    private String host;
    private int port;
    private String version;
    private String idc;
    private String group;
    private String purpose;
    private String protocol;

    @JsonCreator
    public GetClientResponse(
        @JsonProperty("env") String env,
        @JsonProperty("subsystem") String subsystem,
        @JsonProperty("url") String url,
        @JsonProperty("pid") String pid,
        @JsonProperty("host") String host,
        @JsonProperty("port") int port,
        @JsonProperty("version") String version,
        @JsonProperty("idc") String idc,
        @JsonProperty("group") String group,
        @JsonProperty("purpose") String purpose,
        @JsonProperty("protocol") String protocol) {

        super();
        this.env = env;
        this.subsystem = subsystem;
        this.url = url;
        this.pid = pid;
        this.host = host;
        this.port = port;
        this.idc = idc;
        this.group = group;
        this.purpose = purpose;
        this.version = version;
        this.protocol = protocol;
    }
}
