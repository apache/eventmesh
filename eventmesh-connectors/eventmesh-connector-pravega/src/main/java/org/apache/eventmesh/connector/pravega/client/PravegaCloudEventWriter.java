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

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.cloudevents.CloudEventData;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.rw.CloudEventContextWriter;
import io.cloudevents.rw.CloudEventRWException;
import io.cloudevents.rw.CloudEventWriter;

public class PravegaCloudEventWriter
    implements MessageWriter<CloudEventWriter<PravegaEvent>, PravegaEvent>, CloudEventWriter<PravegaEvent> {

    private final PravegaEvent pravegaEvent;

    public PravegaCloudEventWriter(String topic) {
        pravegaEvent = new PravegaEvent();
        pravegaEvent.setTopic(topic);
        pravegaEvent.setCreateTimestamp(System.currentTimeMillis());
    }

    @Override
    public PravegaEvent setEvent(@Nullable EventFormat format, @Nonnull byte[] value) throws CloudEventRWException {
        pravegaEvent.setData(new String(value, StandardCharsets.UTF_8));
        return pravegaEvent;
    }

    @Override
    public PravegaEvent end(CloudEventData data) throws CloudEventRWException {
        pravegaEvent.setData(new String(data.toBytes(), StandardCharsets.UTF_8));
        return pravegaEvent;
    }

    @Override
    public PravegaEvent end() throws CloudEventRWException {
        pravegaEvent.setData("");
        return pravegaEvent;
    }

    @Override
    public CloudEventContextWriter withContextAttribute(@Nonnull String name, @Nonnull String value) throws CloudEventRWException {
        pravegaEvent.getExtensions().put(name, value);
        return this;
    }

    @Override
    public CloudEventWriter<PravegaEvent> create(SpecVersion version) throws CloudEventRWException {
        pravegaEvent.setVersion(version);
        return this;
    }
}
