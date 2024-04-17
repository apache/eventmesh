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

import java.util.Collections;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import inet.ipaddr.IPAddress;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Config(prefix = "eventMesh.server")
public class EventMeshAdminConfiguration extends EventMeshHTTPConfiguration {

    @ConfigField(field = "admin.http.port")
    private int eventMeshServerAdminPort = 10106;

    @ConfigField(field = "admin.threads.num")
    private int eventMeshServerAdminThreadNum = 2;

    @ConfigField(field = "admin.useTls.enabled")
    private boolean eventMeshServerUseTls = false;

    @ConfigField(field = "admin.ssl.protocol")
    private String eventMeshServerSSLProtocol = "TLSv1.3";

    @ConfigField(field = "admin.ssl.cer")
    private String eventMeshServerSSLCer = "eventmesh-admin-server.jks";

    @ConfigField(field = "admin.ssl.pass")
    private String eventMeshServerSSLPass = "eventmesh-admin-server";

    @ConfigField(field = "admin.blacklist.ipv4")
    private List<IPAddress> eventMeshIpv4BlackList = Collections.emptyList();

    @ConfigField(field = "admin.blacklist.ipv6")
    private List<IPAddress> eventMeshIpv6BlackList = Collections.emptyList();
}
