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

package org.apache.eventmesh.protocol.meshmessage;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.util.JsonUtils;
import org.apache.eventmesh.common.wire.EventMeshFrame;
import org.apache.eventmesh.protocol.api.FrameAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link FrameAdaptor} for the legacy MeshMessage TCP protocol — converts a {@link Package}
 * (carrying an {@link EventMeshMessage}) directly to/from an {@link EventMeshFrame}, without going
 * through CloudEvent. This replaces the old {@code MeshMessageProtocolAdaptor.toCloudEvent} path
 * that used CloudEvent as an intermediary.
 *
 * <p>Registered under the name {@code meshmessage}; load via
 * {@code EventMeshExtensionFactory.getExtension(FrameAdaptor.class, "meshmessage")}.</p>
 */
public class MeshMessageFrameAdaptor implements FrameAdaptor {

    public static final String PROTOCOL_TYPE = "meshmessage";
    static final String ATTR_ORIGIN_PROTOCOL = "emorigin";

    @Override
    public EventMeshFrame toFrame(ProtocolTransportObject proto) throws ProtocolHandleException {
        if (!(proto instanceof Package)) {
            throw new ProtocolHandleException("expected Package, got " + (proto == null ? "null" : proto.getClass()));
        }
        Package pkg = (Package) proto;
        Object body = pkg.getBody();
        EventMeshMessage message;
        if (body instanceof EventMeshMessage) {
            message = (EventMeshMessage) body;
        } else if (body instanceof String) {
            message = JsonUtils.parseObject((String) body, EventMeshMessage.class);
        } else {
            throw new ProtocolHandleException("Package body is not an EventMeshMessage: " + (body == null ? "null" : body.getClass()));
        }
        if (message == null) {
            throw new ProtocolHandleException("failed to parse EventMeshMessage from Package body");
        }

        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("specversion", "1.0");
        attrs.put("type", "eventmeshmessage");
        attrs.put("source", "/");
        attrs.put(ATTR_ORIGIN_PROTOCOL, PROTOCOL_TYPE);
        Header header = pkg.getHeader();
        if (header != null && header.getSeq() != null) {
            attrs.put("id", header.getSeq());
        }
        if (message.getTopic() != null) {
            attrs.put("subject", message.getTopic());
        }
        if (message.getProperties() != null) {
            attrs.putAll(message.getProperties());
        }
        if (message.getHeaders() != null) {
            attrs.putAll(message.getHeaders());
        }
        byte[] data = message.getBody() == null ? new byte[0]
            : message.getBody().getBytes(Constants.DEFAULT_CHARSET);
        return EventMeshFrame.event(attrs, data);
    }

    @Override
    public ProtocolTransportObject fromFrame(EventMeshFrame frame) throws ProtocolHandleException {
        Map<String, String> attrs = frame.attributes();
        EventMeshMessage message = new EventMeshMessage();
        message.setTopic(attrs.get("subject"));
        message.setBody(frame.data() == null ? "" : new String(frame.data(), StandardCharsets.UTF_8));

        Map<String, String> props = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : attrs.entrySet()) {
            String k = e.getKey();
            if ("type".equals(k) || ATTR_ORIGIN_PROTOCOL.equals(k) || "id".equals(k) || "subject".equals(k)
                || "source".equals(k) || "specversion".equals(k) || "time".equals(k) || "dataschema".equals(k)
                || "datacontenttype".equals(k)) {
                continue;
            }
            props.put(k, e.getValue());
        }
        message.setProperties(props);

        Package pkg = new Package();
        pkg.setBody(message);
        return pkg;
    }

    @Override
    public String getProtocolType() {
        return PROTOCOL_TYPE;
    }
}
