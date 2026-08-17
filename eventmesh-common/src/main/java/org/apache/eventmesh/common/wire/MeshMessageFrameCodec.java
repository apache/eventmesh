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

package org.apache.eventmesh.common.wire;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Direct {@link EventMeshFrame} ↔ {@link EventMeshMessage} conversion — the legacy TCP
 * {@code MeshMessage} protocol maps straight to the internal frame, without going through
 * CloudEvent as an intermediary (CloudEvent and MeshMessage are peer external protocols; neither
 * converts to the other). Used by the legacy TCP ingress/egress ({@code MeshMessagePackageRouter},
 * {@code NettyTcpPushChannel}) so a MeshMessage client's path is MeshMessage → Frame (ingress) and
 * Frame → MeshMessage (egress), mirroring how the CloudEvents path is CloudEvents → Frame directly.
 *
 * <p>Field mapping:</p>
 * <ul>
 *   <li>{@code message.topic} ↔ frame attribute {@code subject} (the routing key both protocols use)</li>
 *   <li>{@code message.body} (content string) ↔ frame {@code data} (UTF-8 bytes)</li>
 *   <li>{@code message.properties} + {@code message.headers} ↔ frame KV attributes (merged)</li>
 *   <li>{@code header.seq} ↔ frame attribute {@code id} (the message id)</li>
 *   <li>frame {@code type} = {@code eventmeshmessage} (marks the origin protocol)</li>
 * </ul>
 */
public final class MeshMessageFrameCodec {

    /** Attribute marking a frame built from a MeshMessage (so egress knows to convert back to MeshMessage). */
    public static final String ATTR_ORIGIN_PROTOCOL = "emorigin";
    public static final String ORIGIN_MESH_MESSAGE = "meshmessage";

    private MeshMessageFrameCodec() {
    }

    /** Build an EventMeshFrame from a legacy TCP MeshMessage + its header. */
    public static EventMeshFrame fromMeshMessage(Header header, EventMeshMessage message) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("specversion", "1.0");
        attrs.put("type", "eventmeshmessage");
        attrs.put("source", "/"); // required CE field (CloudEventBuilder needs it if anyone calls toCloudEvent)
        attrs.put(ATTR_ORIGIN_PROTOCOL, ORIGIN_MESH_MESSAGE);
        if (header != null && header.getSeq() != null) {
            attrs.put("id", header.getSeq());
        }
        if (message.getTopic() != null) {
            attrs.put("subject", message.getTopic());
        }
        // message-level properties + headers merge into the frame's KV section.
        if (message.getProperties() != null) {
            attrs.putAll(message.getProperties());
        }
        if (message.getHeaders() != null) {
            attrs.putAll(message.getHeaders());
        }
        byte[] data = message.getBody() == null ? new byte[0]
            : message.getBody().getBytes(Constants.DEFAULT_CHARSET);
        return new EventMeshFrame(EventMeshFrame.TYPE_EVENT, 0, 0, attrs, data);
    }

    /** Reconstruct a legacy TCP MeshMessage (in a {@link org.apache.eventmesh.common.protocol.tcp.Package})
     *  from an EventMeshFrame that originated as a MeshMessage. */
    public static org.apache.eventmesh.common.protocol.tcp.Package toMeshMessagePackage(EventMeshFrame frame) {
        Map<String, String> attrs = frame.attributes();
        EventMeshMessage message = new EventMeshMessage();
        message.setTopic(attrs.get("subject"));
        message.setBody(frame.data() == null ? "" : new String(frame.data(), StandardCharsets.UTF_8));

        Map<String, String> props = new LinkedHashMap<>();
        // Carry the remaining attributes as message properties (skip the CE-shaped + origin markers).
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

        org.apache.eventmesh.common.protocol.tcp.Package pkg = new org.apache.eventmesh.common.protocol.tcp.Package();
        pkg.setBody(message);
        return pkg;
    }
}
