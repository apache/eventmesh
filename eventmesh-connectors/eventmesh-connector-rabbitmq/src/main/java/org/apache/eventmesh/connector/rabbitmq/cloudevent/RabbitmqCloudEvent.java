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

package org.apache.eventmesh.connector.rabbitmq.cloudevent;

import com.fasterxml.jackson.core.type.TypeReference;
import io.cloudevents.CloudEvent;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.connector.rabbitmq.exception.RabbitmqConnectorException;
import org.apache.eventmesh.connector.rabbitmq.utils.ByteArrayUtils;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Data
@NoArgsConstructor
public class RabbitmqCloudEvent implements Serializable {

    private SpecVersion version;
    private String data;
    private Map<String, String> extensions = new HashMap<>();

    public CloudEvent convertToCloudEvent() throws Exception {
        CloudEventBuilder builder;
        switch (version) {
            case V03:
                builder = CloudEventBuilder.v03();
                break;
            case V1:
                builder = CloudEventBuilder.v1();
                break;
            default:
                throw new RabbitmqConnectorException(String.format("CloudEvent version %s does not support.", version));
        }
        builder.withData(data.getBytes(StandardCharsets.UTF_8))
            .withId(extensions.remove("id"))
            .withSource(URI.create(extensions.remove("source")))
            .withType(extensions.remove("type"))
            .withDataContentType(extensions.remove("datacontenttype"))
            .withSubject(extensions.remove("subject"));
        extensions.forEach(builder::withExtension);

        return builder.build();
    }

    public static byte[] toByteArray(RabbitmqCloudEvent rabbitmqCloudEvent) throws Exception {
        Optional<byte[]> optionalBytes = ByteArrayUtils.objectToBytes(rabbitmqCloudEvent);
        return optionalBytes.orElseGet(() -> new byte[]{});
    }

    public static RabbitmqCloudEvent getFromByteArray(byte[] body) {
        return JsonUtils.parseTypeReferenceObject(new String(body, Constants.DEFAULT_CHARSET), new TypeReference<RabbitmqCloudEvent>() {
        });
    }
}
