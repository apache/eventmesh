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

package org.apache.eventmesh.runtime.core.protocol.cloudevent.impl;

import io.cloudevents.CloudEventData;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.rw.CloudEventContextWriter;
import io.cloudevents.rw.CloudEventRWException;
import io.cloudevents.rw.CloudEventWriter;
import io.openmessaging.api.Message;
import org.apache.commons.lang3.StringUtils;

/**
 * write ce to ons
 * @param <R>
 */
public final class OMSMessageWriter<R> implements MessageWriter<CloudEventWriter<Message>, Message>, CloudEventWriter<Message> {

    private Message message;


    public OMSMessageWriter(String topic) {
        message = new Message();
        message.setTopic(topic);
    }

    public OMSMessageWriter(String topic, String key) {
        message = new Message();
        message.setTopic(topic);
        if (key != null && key.length() > 0) {
            message.setKey(key);
        }
    }

    public OMSMessageWriter(String topic, String key, String tag) {
        message = new Message();
        message.setTopic(topic);
        if (StringUtils.isNotEmpty(tag)) {
            message.setTag(tag);
        }

        if (StringUtils.isNotEmpty(key)) {
            message.setKey(key);
        }
    }


    @Override
    public CloudEventContextWriter withContextAttribute(String name, String value) throws CloudEventRWException {

        String propName = OMSHeaders.ATTRIBUTES_TO_HEADERS.get(name);
        if (propName == null) {
            propName = OMSHeaders.CE_PREFIX + name;
        }
        message.putUserProperties(propName, value);
        return this;
    }

    @Override
    public OMSMessageWriter<R> create(final SpecVersion version) {
        message.putUserProperties(OMSHeaders.SPEC_VERSION, version.toString());
        return this;
    }

    @Override
    public Message setEvent(final EventFormat format, final byte[] value) throws CloudEventRWException {
        message.putUserProperties(OMSHeaders.CONTENT_TYPE, format.serializedContentType());
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
        message.setBody(null);
        return message;
    }
}
