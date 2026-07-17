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

package org.apache.eventmesh.runtime.transport.tcp;

import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;

import java.nio.charset.StandardCharsets;

import io.cloudevents.CloudEvent;

/**
 * Egress encoder for the TCP push frame: builds the legacy {@code EventMeshMessage} body the client
 * expects (topic ← CloudEvent subject, content ← CloudEvent data). The {@link NettyTcpPushChannel}
 * wraps this body in an {@code ASYNC_MESSAGE_TO_CLIENT} Package; the netty {@code Codec} serializes
 * it onto the wire.
 *
 * <p>This builds the body directly rather than routing through {@code MeshMessageProtocolAdaptor}.
 * fromCloudEvent, because that adaptor's protocol-desc switching requires CloudEvents to carry
 * legacy protocol metadata extensions that push frames don't naturally have; the wire payload a
 * legacy TCP subscriber receives is the {@code EventMeshMessage} anyway.</p>
 */
public class MeshEventToPackageBody implements CloudEventToPackageBody {

    @Override
    public Object toBody(CloudEvent event) {
        EventMeshMessage message = new EventMeshMessage();
        if (event.getSubject() != null) {
            message.setTopic(event.getSubject());
        }
        if (event.getData() != null) {
            message.setBody(new String(event.getData().toBytes(), StandardCharsets.UTF_8));
        }
        return message;
    }
}
