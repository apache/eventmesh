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

package org.apache.eventmesh.http.demo;

import static org.apache.eventmesh.common.Constants.CLOUD_EVENTS_PROTOCOL_NAME;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.util.Utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

public class HttpAbstractDemo {

    protected static EventMeshHttpClientConfig initEventMeshHttpClientConfig(final String groupName)
        throws IOException {
        final Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);
        final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
        final String eventMeshHttpPort = properties.getProperty(ExampleConstants.EVENTMESH_HTTP_PORT);

        // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
        String eventMeshIPPort = ExampleConstants.DEFAULT_EVENTMESH_IP_PORT;
        if (StringUtils.isNotBlank(eventMeshIp) || StringUtils.isNotBlank(eventMeshHttpPort)) {
            eventMeshIPPort = eventMeshIp + ":" + eventMeshHttpPort;
        }

        // Both the producer and consumer require an instance of EventMeshHttpClientConfig class
        // that specifies the configuration of EventMesh HTTP client.
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

    protected static CloudEvent buildCloudEvent(final Map<String, String> content) {
        return CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withSubject(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC)
            .withSource(URI.create("/"))
            .withDataContentType(ExampleConstants.CLOUDEVENT_CONTENT_TYPE)
            .withType(CLOUD_EVENTS_PROTOCOL_NAME)
            .withData(JsonUtils.toJSONString(content).getBytes(StandardCharsets.UTF_8))
            .withExtension(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000))
            .build();
    }

    protected static EventMeshMessage buildMessage(final Map<String, String> content) {
        return EventMeshMessage.builder()
            .bizSeqNo(RandomStringUtils.generateNum(30))
            .content(JsonUtils.toJSONString(content))
            .topic(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC)
            .uniqueId(RandomStringUtils.generateNum(30))
            .build()
            .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4_000));

    }
}
