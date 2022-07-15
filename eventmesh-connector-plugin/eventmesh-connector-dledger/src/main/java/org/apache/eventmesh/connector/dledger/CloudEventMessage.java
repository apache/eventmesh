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

package org.apache.eventmesh.connector.dledger;

import org.apache.eventmesh.common.utils.JsonUtils;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;

import lombok.Data;
import lombok.NoArgsConstructor;

// TODO improve serialize/deserialize
@Data
@NoArgsConstructor
public class CloudEventMessage implements Serializable {
    private String topic;
    private String message;
    private long createTimestamp;

    public CloudEventMessage(String topic, CloudEvent cloudEvent) {
        if (cloudEvent == null) {
            throw new IllegalArgumentException();
        }
        this.topic = topic;
        this.message = new String(EventFormatProvider.getInstance()
                                                     .resolveFormat("application/cloudevents+json")
                                                     .serialize(cloudEvent), StandardCharsets.UTF_8);
        this.createTimestamp = System.currentTimeMillis();
    }

    public static byte[] toByteArray(CloudEventMessage message) {
        return JsonUtils.serialize(message).getBytes(StandardCharsets.UTF_8);
    }

    public static CloudEventMessage getFromByteArray(byte[] body) {
        return JsonUtils.deserialize(new String(body, StandardCharsets.UTF_8), CloudEventMessage.class);
    }
}
