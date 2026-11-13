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

package org.apache.eventmesh.runtime.transport.http;

import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link LegacyHttpCodec} for the legacy {@code EventMeshMessage} JSON wire format — what the old
 * {@code EventMeshHttpClient} posts to {@code /eventmesh/publish} and {@code /eventmesh/subscribe}.
 *
 * <p>This is a faithful, self-contained codec (no legacy runtime dependency): it maps the legacy
 * {@code EventMeshMessage} fields ({@code topic/bizSeqNo/uniqueId/content}) onto a CloudEvent, and
 * the legacy subscribe fields ({@code url/consumerGroup/topics}, or the 1.x {@code topic} list)
 * onto a webhook-push subscription.
 * A production deployment can instead delegate to the existing {@code HttpRequestProtocolResolver}
 * + {@code HttpProtocolAdaptor} for full fidelity with the old envelope; both produce a CloudEvent
 * that the new core consumes unchanged.</p>
 */
@Slf4j
public class EventMeshMessageHttpCodec implements LegacyHttpCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public LegacyPublishRequest parsePublish(byte[] body) {
        try {
            JsonNode msg = firstMessage(mapper.readTree(body));
            String topic = text(msg, "topic");
            String bizSeqNo = text(msg, "bizSeqNo");
            String uniqueId = text(msg, "uniqueId");
            String content = text(msg, "content");

            CloudEvent event = CloudEventBuilder.v1()
                .withId(bizSeqNo != null ? bizSeqNo : (uniqueId != null ? uniqueId : java.util.UUID.randomUUID().toString()))
                .withSource(URI.create("legacy:" + (uniqueId != null ? uniqueId : "eventmesh")))
                .withType("eventmesh.message")
                .withSubject(topic)
                .withDataContentType("application/json")
                .withData(content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0])
                .build();
            return new LegacyPublishRequest(topic, event);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid legacy EventMeshMessage: " + e.getMessage(), e);
        }
    }

    @Override
    public LegacySubscribeRequest parseSubscribe(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            String url = text(root, "url");
            if (url == null) {
                url = text(root, "callbackUrl"); // alternate field name used by some SDK versions
            }
            String consumerGroup = text(root, "consumerGroup");
            if (consumerGroup == null) {
                consumerGroup = text(root, "groupName");
            }
            String secret = text(root, "secret");
            List<String> topics = parseTopics(root);
            // Legacy HTTP subscribe is broadcast-to-url by default (each subscriber gets every message).
            return new LegacySubscribeRequest(consumerGroup, url, secret, topics, DistributionMode.BROADCAST);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid legacy subscribe body: " + e.getMessage(), e);
        }
    }

    /**
     * Extract the subscribed topic names from a legacy subscribe body (#5205).
     *
     * <p>Legacy clients have sent the topic list in several shapes, all of which are accepted:</p>
     * <ul>
     *   <li>{@code "topics": ["a", "b"]} — plain topic names;</li>
     *   <li>{@code "topic": [{"topic": "a", "mode": "CLUSTERING", "type": "ASYNC"}]} — the 1.x
     *       {@code SubscribeRequestBody} list of {@code SubscriptionItem}s;</li>
     *   <li>either of the above JSON-encoded as a string, e.g. {@code "topic": "[{\"topic\":\"a\"}]"},
     *       which is how the 1.x {@code SubscribeRequestBody#toMap()} serializes it;</li>
     *   <li>a single topic, as a name or a {@code SubscriptionItem} object.</li>
     * </ul>
     * Blank and duplicate topic names are dropped.
     */
    private List<String> parseTopics(JsonNode root) throws IOException {
        JsonNode node = root.get("topics");
        if (node == null || node.isNull()) {
            node = root.get("topic");
        }
        Set<String> topics = new LinkedHashSet<>();
        collectTopics(node, topics, true);
        return new ArrayList<>(topics);
    }

    private void collectTopics(JsonNode node, Set<String> topics, boolean allowEncoded) throws IOException {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectTopics(item, topics, false);
            }
        } else if (node.isObject()) {
            addTopic(text(node, "topic"), topics);
        } else if (node.isTextual()) {
            String value = node.asText().trim();
            if (allowEncoded && (value.startsWith("[") || value.startsWith("{"))) {
                collectTopics(mapper.readTree(value), topics, false);
            } else {
                addTopic(value, topics);
            }
        }
    }

    private static void addTopic(String topic, Set<String> topics) {
        if (topic != null && !topic.trim().isEmpty()) {
            topics.add(topic.trim());
        }
    }

    /** Accept either a single {@code EventMeshMessage} or a {@code /publish} batch (use the first). */
    private JsonNode firstMessage(JsonNode root) {
        if (root.isArray()) {
            return root.get(0);
        }
        return root;
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.has(field) && !node.get(field).isNull() ? node.get(field).asText() : null;
    }
}
