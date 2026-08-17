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

package org.apache.eventmesh.runtime.cluster;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Cross-instance forwarding over HTTP (§13.2.5 / §17.6). When the partition owner pulls a message
 * whose subscriber lives on another instance, this forwards it via {@code POST /internal/forward};
 * a late reply whose requestor lives elsewhere is forwarded via {@code POST /internal/reply-forward}.
 * Target instance addresses come from {@link ClusterMembership#addressOf} (the heartbeat value).
 *
 * <p>Synchronous HttpURLConnection — forwarding is on the dispatch hot path but each forward is one
 * short HTTP POST; the caller (ClusterCoordinator) treats a failed forward as a non-delivery and
 * the reliability layer retries/redelivers as needed.</p>
 */
@Slf4j
public class HttpForwarder implements Forwarder {

    private final ClusterMembership membership;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpForwarder(ClusterMembership membership) {
        this.membership = membership;
    }

    @Override
    public boolean forward(String targetInstance, String clientId, String topic, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        String address = membership.addressOf(targetInstance);
        if (address == null) {
            log.warn("forward: no address for instance {}, dropping", targetInstance);
            return false;
        }
        return post("http://" + address + "/internal/forward", buildForwardBody(clientId, topic, event));
    }

    /** Forward a reply to the instance that issued the request (§17.6 self-addressed routing). */
    public boolean forwardReply(String targetInstance, String correlationId, CloudEvent replyEvent) {
        String address = membership.addressOf(targetInstance);
        if (address == null) {
            log.warn("forwardReply: no address for instance {}, dropping", targetInstance);
            return false;
        }
        return post("http://" + address + "/internal/reply-forward", buildReplyBody(correlationId, replyEvent));
    }

    private byte[] buildForwardBody(String clientId, String topic, org.apache.eventmesh.common.wire.EventMeshFrame event) {
        try {
            // Egress: forward body is CloudEvents-JSON over HTTP; Frame → CE-JSON via FrameAdaptor SPI.
            byte[] eventJson = org.apache.eventmesh.protocol.api.FrameAdaptors.toCloudEventsJson(event);
            ObjectNode body = mapper.createObjectNode();
            body.put("clientId", clientId);
            body.put("topic", topic);
            body.set("event", mapper.readTree(eventJson));
            return mapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new RuntimeException("build forward body failed", e);
        }
    }

    private byte[] buildReplyBody(String correlationId, CloudEvent replyEvent) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("correlationId", correlationId);
            byte[] eventJson = EventFormatProvider.getInstance().resolveFormat(JsonFormat.CONTENT_TYPE).serialize(replyEvent);
            body.set("event", mapper.readTree(eventJson));
            return mapper.writeValueAsBytes(body);
        } catch (Exception e) {
            throw new RuntimeException("build reply body failed", e);
        }
    }

    private boolean post(String url, byte[] payload) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(payload);
            }
            int status = conn.getResponseCode();
            try {
                conn.getInputStream().close();
            } catch (Exception ignored) {
                // best-effort drain
            }
            return status >= 200 && status < 300;
        } catch (Exception e) {
            log.warn("POST {} failed: {}", url, e.toString());
            return false;
        }
    }
}
