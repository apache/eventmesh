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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class WebHookChannelTest {

    private static final String SECRET = "s3cret";

    @Test
    void successfulPostAcksAndCarriesSignature() {
        RecordingCallback cb = new RecordingCallback();
        CapturingHttp http = new CapturingHttp(200);
        WebHookChannel hook = new WebHookChannel("https://svc/hook", SECRET, http, WebHookChannelTest::idBody, 1_000L);

        hook.deliver("d-1", EventMeshFrame.fromCloudEvent(event("e-1")), cb);

        assertEquals(1, cb.acks.get());
        assertEquals(0, cb.nacks.get());
        assertEquals("https://svc/hook", http.lastUrl);
        assertEquals("d-1", http.lastHeaders.get(WebHookChannel.HEADER_DELIVERY_ID));
        assertEquals("1000", http.lastHeaders.get(WebHookChannel.HEADER_TIMESTAMP));
        // Signature matches HMAC over the posted body.
        assertEquals(hook.sign(http.lastBody), http.lastHeaders.get(WebHookChannel.HEADER_SIGNATURE));
    }

    @Test
    void non2xxNacksSoDispatcherRetries() {
        RecordingCallback cb = new RecordingCallback();
        WebHookChannel hook = new WebHookChannel("https://svc/hook", SECRET, new CapturingHttp(503),
            WebHookChannelTest::idBody, 1_000L);

        hook.deliver("d-1", EventMeshFrame.fromCloudEvent(event("e-1")), cb);

        assertEquals(0, cb.acks.get());
        assertEquals(1, cb.nacks.get(), "non-2xx must nack so ReliableDispatcher retries");
    }

    @Test
    void httpExceptionNacks() {
        RecordingCallback cb = new RecordingCallback();
        WebHookChannel hook = new WebHookChannel("https://svc/hook", SECRET,
            (url, body, headers) -> {
                throw new RuntimeException("connection refused");
            }, WebHookChannelTest::idBody, 1_000L);

        hook.deliver("d-1", EventMeshFrame.fromCloudEvent(event("e-1")), cb);

        assertTrue(cb.nacks.get() == 1 && cb.acks.get() == 0);
    }

    private static byte[] idBody(CloudEvent event) {
        return event.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static CloudEvent event(String id) {
        return CloudEventBuilder.v1().withId(id).withSource(URI.create("src")).withType("t").build();
    }

    private static final class CapturingHttp implements HttpCaller {

        final int status;
        String lastUrl;
        byte[] lastBody;
        Map<String, String> lastHeaders;

        CapturingHttp(int status) {
            this.status = status;
        }

        @Override
        public int post(String url, byte[] body, Map<String, String> headers) {
            lastUrl = url;
            lastBody = body;
            lastHeaders = headers;
            return status;
        }
    }

    private static final class RecordingCallback implements AckCallback {

        final AtomicInteger acks = new AtomicInteger();
        final AtomicInteger nacks = new AtomicInteger();

        @Override
        public void ack() {
            acks.incrementAndGet();
        }

        @Override
        public void nack(Throwable reason) {
            nacks.incrementAndGet();
        }
    }
}
