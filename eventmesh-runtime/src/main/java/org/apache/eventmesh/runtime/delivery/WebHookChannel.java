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

package org.apache.eventmesh.runtime.delivery;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * WebHook push transport (§8.5): an alternative delivery target that POSTs each CloudEvent to a
 * subscriber-supplied URL. It is a {@link PushChannel}, so it plugs into the same reliability layer
 * — a non-2xx response (or thrown exception) {@code nack}s the delivery, triggering the
 * {@code ReliableDispatcher}'s exponential-backoff retry and eventual DLQ.
 *
 * <p>Each POST carries {@code X-Em-Signature} (HMAC-SHA256 of the body, hex), {@code X-Em-Timestamp}
 * (replay protection), and {@code X-Em-Delivery-Id} (dedup). The receiver verifies the signature and
 * must be idempotent on {@code X-Em-Delivery-Id} (at-least-once redelivery).</p>
 */
@Slf4j
public class WebHookChannel implements PushChannel {

    public static final String HEADER_SIGNATURE = "X-Em-Signature";
    public static final String HEADER_TIMESTAMP = "X-Em-Timestamp";
    public static final String HEADER_DELIVERY_ID = "X-Em-Delivery-Id";

    private final String deliveryUrl;
    private final byte[] secret;
    private final HttpCaller http;
    private final CloudEventSerializer serializer;
    private final long timestampMs;

    public WebHookChannel(String deliveryUrl, String secret, HttpCaller http, CloudEventSerializer serializer) {
        this(deliveryUrl, secret, http, serializer, System.currentTimeMillis());
    }

    /** Test constructor with an injectable clock for the timestamp header. */
    public WebHookChannel(String deliveryUrl, String secret, HttpCaller http, CloudEventSerializer serializer,
        long timestampMs) {
        this.deliveryUrl = deliveryUrl;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.http = http;
        this.serializer = serializer;
        this.timestampMs = timestampMs;
    }

    @Override
    public void deliver(String deliveryId, CloudEvent event, AckCallback callback) {
        byte[] body;
        try {
            body = serializer.serialize(event);
        } catch (RuntimeException e) {
            log.warn("webhook serialize failed for delivery={}", deliveryId, e);
            callback.nack(e);
            return;
        }
        Map<String, String> headers = new HashMap<>();
        headers.put(HEADER_SIGNATURE, sign(body));
        headers.put(HEADER_TIMESTAMP, Long.toString(timestampMs));
        headers.put(HEADER_DELIVERY_ID, deliveryId);

        try {
            int status = http.post(deliveryUrl, body, headers);
            if (status >= 200 && status < 300) {
                callback.ack();
            } else {
                callback.nack(new IllegalStateException("webhook non-2xx: " + status));
            }
        } catch (RuntimeException e) {
            callback.nack(e);
        }
    }

    /**
     * Compute the HMAC-SHA256 signature over {@code body} — also used by receivers/tests to verify.
     */
    public String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return toHex(mac.doFinal(body));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }
}
