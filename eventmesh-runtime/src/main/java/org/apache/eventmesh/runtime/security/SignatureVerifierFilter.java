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

package org.apache.eventmesh.runtime.security;

import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies an HMAC-SHA256 signature over a canonical projection of the CloudEvent (§13.4.4), so the
 * receiver can detect tampering and assert provenance. The signature travels in the
 * {@code emsignature} extension (no hyphens — CloudEvents extension-name rules) and covers
 * {@code id|source|type}. A missing or mismatched signature → 401.
 */
public class SignatureVerifierFilter implements IngressFilter {

    /** CloudEvents extension carrying the hex-encoded HMAC-SHA256 signature. */
    public static final String EXT_SIGNATURE = "emsignature";

    private final byte[] secret;

    public SignatureVerifierFilter(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public FilterVerdict check(EventMeshFrame frame, FilterContext ctx) {
        // The signature travels in the frame attributes under the same key the legacy CloudEvent
        // extension used, so a signed CE-JSON payload becomes a signed frame automatically after
        // the cloudevents FrameAdaptor round-trip.
        String provided = frame.attributes().get(EXT_SIGNATURE);
        if (provided == null) {
            return FilterVerdict.deny(FilterVerdict.STATUS_UNAUTHENTICATED, "missing signature");
        }
        String expected = sign(canonical(frame));
        if (constantTimeEquals(expected, provided)) {
            return FilterVerdict.allow();
        }
        return FilterVerdict.deny(FilterVerdict.STATUS_UNAUTHENTICATED, "signature mismatch");
    }

    /**
     * Compute the signature over {@code message} — also used by clients/tests to produce the value
     * placed in the {@code emsignature} extension/attribute.
     */
    public String sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return toHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    static String canonical(EventMeshFrame frame) {
        // Same projection as before (#5299 sub-PR B): id|source|type, all read from frame
        // attributes. For non-EVENT frames the canonical string still computes (we may want to
        // tighten to isEvent() in a follow-up if streaming chunks ever need sigs).
        Map<String, String> a = frame.attributes();
        return a.getOrDefault("id", "") + "|" + a.getOrDefault("source", "") + "|" + a.getOrDefault("type", "");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
