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

package org.apache.eventmesh.connector.dledger.broker;

import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.dledger.exception.DLedgerConnectorException;

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
public class CloudEventMessage implements Serializable {
    private SpecVersion version;
    private String topic;
    private String data;
    private Map<String, String> extensions = new HashMap<>();
    private long createTimestamp;

    public static byte[] toByteArray(CloudEventMessage cloudEventMessage) {
        return JsonUtils.serialize(cloudEventMessage).getBytes(StandardCharsets.UTF_8);
    }

    public static CloudEventMessage getFromByteArray(byte[] body) {
        return JsonUtils.deserialize(new String(body, StandardCharsets.UTF_8), CloudEventMessage.class);
    }

    public CloudEvent convertToCloudEvent() {
        CloudEventBuilder builder;
        switch (version) {
            case V03:
                builder = CloudEventBuilder.v03();
                break;
            case V1:
                builder = CloudEventBuilder.v1();
                break;
            default:
                throw new DLedgerConnectorException(String.format("CloudEvent version %s does not support.", version));
        }
        builder.withData(data.getBytes());

        builder.withId(extensions.remove("id"));
        builder.withSource(URI.create(extensions.remove("source")));
        builder.withType(extensions.remove("type"));
        builder.withDataContentType(extensions.remove("datacontenttype"));
        builder.withSubject(extensions.remove("subject"));

        for (Map.Entry<String, String> extension : extensions.entrySet()) {
            builder.withExtension(extension.getKey(), extension.getValue());
        }

        return builder.build();
    }
}
