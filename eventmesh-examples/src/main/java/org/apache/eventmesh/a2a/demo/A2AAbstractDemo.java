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

package org.apache.eventmesh.a2a.demo;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.util.Utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Properties;

public class A2AAbstractDemo {

    protected static EventMeshHttpClientConfig initEventMeshHttpClientConfig(final String groupName)
        throws IOException {
        final Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
        final String eventMeshHttpPort = properties.getProperty(ExampleConstants.EVENTMESH_HTTP_PORT);

        String eventMeshIPPort = ExampleConstants.DEFAULT_EVENTMESH_IP_PORT;
        if (StringUtils.isNotBlank(eventMeshIp) || StringUtils.isNotBlank(eventMeshHttpPort)) {
            eventMeshIPPort = eventMeshIp + ":" + eventMeshHttpPort;
        }

        return EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr(eventMeshIPPort)
            .producerGroup(groupName)
            .env("env")
            .idc("idc")
            .ip(IPUtils.getLocalAddress())
            .sys("1234")
            .pid(String.valueOf(ThreadUtils.getPID()))
            .userName("eventmesh")
            .password("pass")
            .build();
    }
}
