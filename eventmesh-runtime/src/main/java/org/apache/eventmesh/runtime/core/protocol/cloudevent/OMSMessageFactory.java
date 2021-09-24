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

package org.apache.eventmesh.runtime.core.protocol.cloudevent;

import io.cloudevents.core.message.MessageReader;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.core.message.impl.GenericStructuredMessageReader;
import io.cloudevents.core.message.impl.MessageUtils;
import io.cloudevents.lang.Nullable;
import io.cloudevents.rw.CloudEventRWException;
import io.cloudevents.rw.CloudEventWriter;
import io.openmessaging.api.Message;
import org.apache.eventmesh.runtime.core.protocol.cloudevent.impl.OMSBinaryMessageReader;
import org.apache.eventmesh.runtime.core.protocol.cloudevent.impl.OMSHeaders;
import org.apache.eventmesh.runtime.core.protocol.cloudevent.impl.OMSMessageWriter;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Properties;

/**
 * This class provides a collection of methods to create {@link io.cloudevents.core.message.MessageReader}
 * and {@link io.cloudevents.core.message.MessageWriter}
 * manually serialize/deserialize {@link io.cloudevents.CloudEvent} messages.
 */
@ParametersAreNonnullByDefault
public final class OMSMessageFactory {

    private OMSMessageFactory() {
        // prevent instantiation
    }

    /**
     * create reader by message
     * @param message
     * @return
     * @throws CloudEventRWException
     */
    public static MessageReader createReader(final Message message) throws CloudEventRWException {
        return createReader(message.getUserProperties(), message.getBody());
    }


    public static MessageReader createReader(final Properties props, @Nullable final byte[] body) throws CloudEventRWException {

        return MessageUtils.parseStructuredOrBinaryMessage(
            () -> props.getOrDefault(OMSHeaders.CONTENT_TYPE,"").toString(),
            format -> new GenericStructuredMessageReader(format, body),
            () -> props.getOrDefault(OMSHeaders.SPEC_VERSION,"").toString(),
            sv -> new OMSBinaryMessageReader(sv, props, body)
        );
    }


    /**
     * create writer by topic
     * @param topic
     * @return
     */
    public static MessageWriter<CloudEventWriter<Message>, Message> createWriter(String topic) {
        return new OMSMessageWriter<>(topic);
    }

    /**
     * create writer by topic,keys
     * @param topic
     * @param keys
     * @return
     */
    public static MessageWriter<CloudEventWriter<Message>, Message> createWriter(String topic, String keys) {
        return new OMSMessageWriter<>(topic, keys);
    }

    /**
     * create writer by topic,keys,tags
     * @param topic
     * @param keys
     * @param tags
     * @return
     */
    public static MessageWriter<CloudEventWriter<Message>, Message> createWriter(String topic, String keys, String tags) {
        return new OMSMessageWriter<>(topic, keys, tags);
    }

}
