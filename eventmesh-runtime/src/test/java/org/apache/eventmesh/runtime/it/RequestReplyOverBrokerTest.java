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

package org.apache.eventmesh.runtime.it;

import org.apache.eventmesh.client.cloudevents.CloudEventsClient;
import org.apache.eventmesh.runtime.admin.UniAdminService;
import org.apache.eventmesh.runtime.http.UniHttpServer;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Real-broker request-reply end-to-end: responder subscribes via HTTP long-poll, broker-side
 * pull-loop dispatches, requester calls {@code /req} and waits for the reply. Pairs with the
 * in-process {@link RequestReplyHttpIntegrationTest}; the value here is verifying the request
 * survives the full store → broker → topic → rebalance → subscriber path on at least one round-trip.
 *
 * <p>Run: {@code -Dit.feature.storage=rocketmq4 -Dit.namesrv=host:9876}. Skipped otherwise.</p>
 */
@EnabledIfSystemProperty(named = "it.feature.storage", matches = "rocketmq|rocketmq4|rocketmq5")
class RequestReplyOverBrokerTest {

    @Test
    void requestReplyRoundTripOverBroker() throws Exception {
        try (FeatureBrokerHarness fx = FeatureBrokerHarness.of("rocketmq4")) {
            fx.start();

            // Build a paired HTTP server using the same UniIngressService, so request/reply flows
            // through the live runtime — not a side-channel stub.
            UniAdminService admin = new UniAdminService(fx.runtime().ingress());
            UniHttpServer http = new UniHttpServer(fx.runtime().ingress(), admin);
            int httpPort = http.start(0);
            try {
                String topic = "em-it-req-" + System.nanoTime();
                String namesrv = System.getProperty("it.namesrv",
                    System.getProperty("it.namesrv5", "localhost:9876"));
                BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topic, 4);

                // Both ends register the same subscription so the broker treats them as broadcast
                // receivers; the server-side long-poll is how the /req handler picks up the request
                // and returns once the responder's reply comes back through the runtime.
                fx.runtime().ingress().subscribe(topic, "req-rr-broker", DistributionMode.BROADCAST, null);

                String runtimeUrl = "http://localhost:" + httpPort;
CloudEventsClient responder = CloudEventsClient.builder()
                        .runtimeUrl(runtimeUrl).clientId("rr-responder-" + UUID.randomUUID()).pollIntervalMs(200L).build();
                try {
                    responder.subscribe(topic, "BROADCAST", event -> {
                        Object corr = event.getExtension("emcorrelationid");
                        if (corr != null && !corr.toString().isEmpty()) {
                            CloudEvent reply = CloudEventsClient.event(
                                "rr-reply-" + System.nanoTime(),
                                "responder",
                                "rr.reply",
                                "reply-payload".getBytes(StandardCharsets.UTF_8));
                            responder.reply(corr.toString(), reply);
                        }
                    });
                    Thread.sleep(2_000L); // let subscribe + first rebalance settle

                    CloudEventsClient requester = CloudEventsClient.builder()
                        .runtimeUrl(runtimeUrl).clientId("rr-requester-" + UUID.randomUUID()).build();
                    try {
                        CloudEvent req = CloudEventsClient.event(
                            "rr-req-" + System.nanoTime(),
                            "requester",
                            "rr.request",
                            "req-payload".getBytes(StandardCharsets.UTF_8));
                        CloudEvent reply = requester.request(topic, req, 25_000L);
                        assertNotNull(reply, "request should be answered over real broker before timeout");
                        assertEquals("rr.reply", reply.getType(), "reply type should match responder");
                    } finally {
                        requester.shutdown();
                    }
                } finally {
                    responder.shutdown();
                }
            } finally {
                http.stop();
            }
        }
    }
}
