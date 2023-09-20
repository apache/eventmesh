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

public class RabbitmqCloudEventWriter
    implements
        MessageWriter<CloudEventWriter<RabbitmqCloudEvent>, RabbitmqCloudEvent>,
        CloudEventWriter<RabbitmqCloudEvent> {

    private final RabbitmqCloudEvent rabbitmqCloudEvent;

    public RabbitmqCloudEventWriter() {
        rabbitmqCloudEvent = new RabbitmqCloudEvent();
    }

    @Override
    public RabbitmqCloudEvent setEvent(@Nullable EventFormat format, @Nonnull byte[] value) throws CloudEventRWException {
        rabbitmqCloudEvent.setData(new String(value, StandardCharsets.UTF_8));
        return rabbitmqCloudEvent;
    }

    @Override
    public RabbitmqCloudEvent end(CloudEventData data) throws CloudEventRWException {
        rabbitmqCloudEvent.setData(new String(data.toBytes(), StandardCharsets.UTF_8));
        return rabbitmqCloudEvent;
    }

    @Override
    public RabbitmqCloudEvent end() throws CloudEventRWException {
        rabbitmqCloudEvent.setData("");
        return rabbitmqCloudEvent;
    }

    @Override
    public CloudEventContextWriter withContextAttribute(@Nonnull String name, @Nonnull String value) throws CloudEventRWException {
        rabbitmqCloudEvent.getExtensions().put(name, value);
        return this;
    }

    @Override
    public CloudEventWriter<RabbitmqCloudEvent> create(SpecVersion version) throws CloudEventRWException {
        rabbitmqCloudEvent.setVersion(version);
        return this;
    }
}
