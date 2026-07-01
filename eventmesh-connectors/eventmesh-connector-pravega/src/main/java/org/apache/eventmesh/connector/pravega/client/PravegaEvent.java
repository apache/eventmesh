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

package org.apache.eventmesh.connector.pravega.client;

import org.apache.eventmesh.common.utils.JsonUtils;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PravegaEvent implements Serializable {

    private static final long serialVersionUID = 0L;

    private SpecVersion version;
    private String topic;
    private String data;
    private Map<String, String> extensions = new HashMap<>();
    private long createTimestamp;

    public static byte[] toByteArray(PravegaEvent pravegaEvent) {
        return JsonUtils.toJSONString(pravegaEvent).getBytes(StandardCharsets.UTF_8);
    }

    public static PravegaEvent getFromByteArray(byte[] body) {
        return JsonUtils.parseObject(new String(body, StandardCharsets.UTF_8), PravegaEvent.class);
    }

    public CloudEvent convertToCloudEvent() {
        CloudEventBuilder builder = CloudEventBuilder.fromSpecVersion(version);
        builder.withData(data.getBytes(StandardCharsets.UTF_8))
            .withId(extensions.remove("id"))
            .withSource(URI.create(extensions.remove("source")))
            .withType(extensions.remove("type"))
            .withDataContentType(extensions.remove("datacontenttype"))
            .withSubject(extensions.remove("subject"));
        extensions.forEach(builder::withExtension);
        return builder.build();
    }
}
