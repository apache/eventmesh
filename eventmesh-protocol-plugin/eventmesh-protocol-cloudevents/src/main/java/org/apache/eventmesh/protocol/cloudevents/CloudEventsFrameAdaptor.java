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

package org.apache.eventmesh.protocol.cloudevents;

import org.apache.eventmesh.common.protocol.ByteTransport;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.protocol.api.FrameAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

/**
 * {@link FrameAdaptor} for the CloudEvents 1.0 protocol (structured-JSON on the wire). Converts
 * directly between CloudEvents-JSON bytes and {@link EventMeshFrame}.
 *
 * <p>Registered under the name {@code cloudevents}; the default external protocol for HTTP/SSE/WS
 * clients. Load via {@code FrameAdaptors.get("cloudevents")}.</p>
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public class CloudEventsFrameAdaptor implements FrameAdaptor {

    public static final String PROTOCOL_TYPE = "cloudevents";

    private static io.cloudevents.core.format.EventFormat jsonFormat() {
        return EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE);
    }

    @Override
    public EventMeshFrame toFrame(ProtocolTransportObject proto) throws ProtocolHandleException {
        if (!(proto instanceof ByteTransport)) {
            throw new ProtocolHandleException("expected ByteTransport (CloudEvents JSON bytes), got "
                + (proto == null ? "null" : proto.getClass()));
        }
        byte[] ceJsonBytes = ((ByteTransport) proto).getBytes();
        if (ceJsonBytes == null || ceJsonBytes.length == 0) {
            throw new ProtocolHandleException("empty CloudEvents body");
        }
        try {
            CloudEvent ce = jsonFormat().deserialize(ceJsonBytes);
            if (ce == null) {
                throw new ProtocolHandleException("failed to deserialize CloudEvents JSON");
            }
            return EventMeshFrame.fromCloudEvent(ce);
        } catch (ProtocolHandleException e) {
            throw e;
        } catch (Exception e) {
            throw new ProtocolHandleException("CloudEvents → EventMeshFrame conversion failed", e);
        }
    }

    @Override
    public ProtocolTransportObject fromFrame(EventMeshFrame frame) throws ProtocolHandleException {
        try {
            CloudEvent ce = frame.toCloudEvent();
            byte[] json = jsonFormat().serialize(ce);
            return new ByteTransport(json);
        } catch (Exception e) {
            throw new ProtocolHandleException("EventMeshFrame → CloudEvents conversion failed", e);
        }
    }

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }
}
