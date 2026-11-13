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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * Legacy subscribe bodies in every topic-list shape old clients send (#5205).
 */
class EventMeshMessageHttpCodecTest {

    private final EventMeshMessageHttpCodec codec = new EventMeshMessageHttpCodec();

    private LegacyHttpCodec.LegacySubscribeRequest parse(String json) {
        return codec.parseSubscribe(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesPlainTopicNames() {
        LegacyHttpCodec.LegacySubscribeRequest req =
            parse("{\"consumerGroup\":\"c1\",\"url\":\"http://h/cb\",\"topics\":[\"orders\",\"payments\"]}");
        assertEquals("c1", req.getClientId());
        assertEquals("http://h/cb", req.getUrl());
        assertEquals(Arrays.asList("orders", "payments"), req.getTopics());
        assertEquals(DistributionMode.BROADCAST, req.getMode());
    }

    @Test
    void parsesLegacySubscriptionItemArray() {
        LegacyHttpCodec.LegacySubscribeRequest req = parse("{\"consumerGroup\":\"c1\",\"url\":\"http://h/cb\","
            + "\"topic\":[{\"topic\":\"orders\",\"mode\":\"CLUSTERING\",\"type\":\"ASYNC\"},"
            + "{\"topic\":\"payments\",\"mode\":\"BROADCASTING\",\"type\":\"SYNC\"}]}");
        assertEquals(Arrays.asList("orders", "payments"), req.getTopics());
    }

    @Test
    void parsesLegacySubscriptionItemArrayEncodedAsString() {
        // 1.x SubscribeRequestBody#toMap() puts the list in "topic" as a JSON string.
        LegacyHttpCodec.LegacySubscribeRequest req = parse("{\"consumerGroup\":\"c1\",\"url\":\"http://h/cb\","
            + "\"topic\":\"[{\\\"topic\\\":\\\"orders\\\",\\\"mode\\\":\\\"CLUSTERING\\\",\\\"type\\\":\\\"ASYNC\\\"}]\"}");
        assertEquals(Collections.singletonList("orders"), req.getTopics());
    }

    @Test
    void parsesSingleTopicNameAndSingleItem() {
        assertEquals(Collections.singletonList("orders"),
            parse("{\"consumerGroup\":\"c1\",\"url\":\"http://h/cb\",\"topic\":\"orders\"}").getTopics());
        assertEquals(Collections.singletonList("orders"),
            parse("{\"consumerGroup\":\"c1\",\"url\":\"http://h/cb\",\"topic\":{\"topic\":\"orders\"}}").getTopics());
    }

    @Test
    void dropsBlankAndDuplicateTopics() {
        LegacyHttpCodec.LegacySubscribeRequest req = parse("{\"consumerGroup\":\"c1\",\"url\":\"http://h/cb\","
            + "\"topic\":[{\"topic\":\"orders\"},{\"topic\":\" \"},{\"mode\":\"CLUSTERING\"},\"orders\"]}");
        assertEquals(Collections.singletonList("orders"), req.getTopics());
    }

    @Test
    void missingTopicsYieldsEmptyList() {
        assertTrue(parse("{\"consumerGroup\":\"c1\",\"url\":\"http://h/cb\"}").getTopics().isEmpty());
    }

    @Test
    void malformedJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"consumerGroup\":"));
        assertThrows(IllegalArgumentException.class,
            () -> parse("{\"consumerGroup\":\"c1\",\"topic\":\"[{\\\"topic\\\":\"}"));
    }
}
