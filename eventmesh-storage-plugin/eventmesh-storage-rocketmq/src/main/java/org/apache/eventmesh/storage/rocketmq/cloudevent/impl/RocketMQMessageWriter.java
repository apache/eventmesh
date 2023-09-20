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

package org.apache.eventmesh.storage.rocketmq.cloudevent.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.common.message.Message;

import javax.annotation.Nonnull;

import io.cloudevents.CloudEventData;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.rw.CloudEventContextWriter;
import io.cloudevents.rw.CloudEventRWException;
import io.cloudevents.rw.CloudEventWriter;

public final class RocketMQMessageWriter<R>
    implements
        MessageWriter<CloudEventWriter<Message>, Message>,
        CloudEventWriter<Message> {

    private final Message message = new Message();

    public RocketMQMessageWriter(String topic) {
        message.setTopic(topic);
    }

    public RocketMQMessageWriter(String topic, String keys) {
        message.setTopic(topic);

        if (StringUtils.isNotEmpty(keys)) {
            message.setKeys(keys);
        }
    }

    public RocketMQMessageWriter(String topic, String keys, String tags) {
        message.setTopic(topic);

        if (StringUtils.isNotEmpty(tags)) {
            message.setTags(tags);
        }

        if (StringUtils.isNotEmpty(keys)) {
            message.setKeys(keys);
        }
    }

    @Override
    public CloudEventContextWriter withContextAttribute(@Nonnull String name, @Nonnull String value) throws CloudEventRWException {
        message.putUserProperty(name, value);
        return this;
    }

    @Override
    public RocketMQMessageWriter<R> create(final SpecVersion version) {
        message.putUserProperty(RocketMQHeaders.SPEC_VERSION, version.toString());
        return this;
    }

    @Override
    public Message setEvent(@Nonnull final EventFormat format, @Nonnull final byte[] value) throws CloudEventRWException {
        message.putUserProperty(RocketMQHeaders.CONTENT_TYPE, format.serializedContentType());
        message.setBody(value);
        return message;
    }

    @Override
    public Message end(final CloudEventData data) throws CloudEventRWException {
        message.setBody(data.toBytes());
        return message;
    }

    @Override
    public Message end() {
        return message;
    }
}
