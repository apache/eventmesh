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

import java.nio.charset.StandardCharsets;

import io.cloudevents.CloudEventData;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.rw.CloudEventContextWriter;
import io.cloudevents.rw.CloudEventRWException;
import io.cloudevents.rw.CloudEventWriter;

public class DLedgerMessageWriter<R>
    implements MessageWriter<CloudEventWriter<CloudEventMessage>, CloudEventMessage>, CloudEventWriter<CloudEventMessage> {

    private final CloudEventMessage message;

    public DLedgerMessageWriter(String topic) {
        message = new CloudEventMessage();
        message.setTopic(topic);
        message.setCreateTimestamp(System.currentTimeMillis());
    }

    @Override
    public CloudEventMessage setEvent(EventFormat format, byte[] value) throws CloudEventRWException {
        message.setData(new String(value, StandardCharsets.UTF_8));
        return message;
    }

    @Override
    public CloudEventMessage end(CloudEventData data) throws CloudEventRWException {
        message.setData(new String(data.toBytes(), StandardCharsets.UTF_8));
        return message;
    }

    @Override
    public CloudEventMessage end() throws CloudEventRWException {
        message.setData("");
        return message;
    }

    @Override
    public CloudEventContextWriter withContextAttribute(String name, String value) throws CloudEventRWException {
        message.getExtensions().put(name, value);
        return this;
    }

    @Override
    public CloudEventWriter<CloudEventMessage> create(SpecVersion version) throws CloudEventRWException {
        message.setVersion(version);
        return this;
    }
}
