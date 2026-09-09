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

import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import lombok.extern.slf4j.Slf4j;

/**
 * Production {@link Forwarder} (issue #5376): the partition-owning instance POSTs the event to
 * the subscriber's instance {@code POST /internal/forward} so the target delivers through its
 * local push path. Addressing goes through {@link ClusterMembership#addressOf(String)} so the
 * routing follows the live membership table, not a static config.
 *
 * <p>The wire body is the frame's binary encoding base64-wrapped in a tiny JSON envelope
 * ({@code {"clientId":..,"topic":..,"frameB64":..}}); the target decodes and delivers locally.
 * Delivery failures return {@code false} so the coordinator's caller can account for them
 * (the at-least-once bound rides on the backend's redelivery, as with any dispatch failure).</p>
 */
@Slf4j
public class HttpForwarder implements Forwarder {

    private final ClusterMembership membership;
    private final HttpClient client;
    private final String bearerToken;

    public HttpForwarder(ClusterMembership membership) {
        this(membership, null);
    }

    /**
     * @param bearerToken optional bearer for the internal endpoint
     *                    ({@code eventmesh.admin.token}); null/empty sends none
     */
    public HttpForwarder(ClusterMembership membership, String bearerToken) {
        this.membership = membership;
        this.bearerToken = bearerToken;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    }

    @Override
    public boolean forward(String targetInstance, String clientId, String topic, EventMeshFrame event) {
        String address = membership.addressOf(targetInstance);
        if (address == null || address.isEmpty()) {
            log.warn("forward failed: no live address for instance {} (membership churn?)", targetInstance);
            return false;
        }
        String url = "http://" + address + "/internal/forward";
        String body = "{\"clientId\":\"" + clientId + "\",\"topic\":\"" + topic
            + "\",\"frameB64\":\"" + Base64.getEncoder().encodeToString(event.encode()) + "\"}";
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (bearerToken != null && !bearerToken.isEmpty()) {
                req.header("Authorization", "Bearer " + bearerToken);
            }
            HttpResponse<String> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return true;
            }
            log.warn("forward to {} for client {} on {} returned {}", targetInstance, clientId, topic, resp.statusCode());
            return false;
        } catch (Exception e) {
            log.warn("forward to {} for client {} on {} failed: {}", targetInstance, clientId, topic, e.toString());
            return false;
        }
    }
}
