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

import org.apache.eventmesh.runtime.ratelimit.RateLimitedException;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/**
 * Real-broker version of {@link RateLimitIntegrationTest}. {@code UniIngressService.setTopicRateLimit}
 * is invoked against the live runtime (not the in-memory harness), the configured limit is consumed
 * by a burst of publishes, and the rejection is observed via {@code RateLimitedException} on the
 * publish futures plus the {@code rateLimited} metric counter.
 *
 * <p>This is the canonical production failure mode an operator reaches when they set a too-tight
 * rate on a hot topic — the request returns an exception synchronously and the {@code eventmesh_rate_limited}
 * counter on the Prometheus scrape climbs. Both must hold end-to-end, including the broker storage
 * call, because an over-broad publish must not silently drop.</p>
 *
 * <p>Run: {@code -Dit.feature.storage=rocketmq4 -Dit.namesrv=host:9876}. Skipped otherwise.</p>
 */
@EnabledIfSystemProperty(named = "it.feature.storage", matches = "rocketmq|rocketmq4|rocketmq5")
class RateLimitOverBrokerTest {

    @Test
    void burstOverCapacityIsRateLimited() throws Exception {
        try (FeatureBrokerHarness fx = FeatureBrokerHarness.of("rocketmq4")) {
            fx.start();

            String topic = "em-it-ratelimit-" + System.nanoTime();
            String clientId = "ratelimit-broker-client";
            String namesrv = System.getProperty("it.namesrv",
                System.getProperty("it.namesrv5", "localhost:9876"));
            BrokerDiscoverer.ensureTopicOnReachableBroker(namesrv, topic, 4);
            fx.runtime().ingress().subscribe(topic, clientId, DistributionMode.BROADCAST, null);
            Thread.sleep(2_000L);

            // 2-token bucket, refill rate 0/s — the bucket exhausts after 2 publishes and never
            // refills during this test window. The first 2 publishes succeed, the rest fail.
            fx.runtime().ingress().setTopicRateLimit(topic, 2L, 0.0d);

            long before = fx.runtime().ingress().getMetrics().getRateLimited();
            int success = 0;
            int rejected = 0;
            for (int i = 0; i < 5; i++) {
                CloudEvent e = CloudEventBuilder.v1()
                    .withId("rl-" + System.nanoTime() + "-" + i)
                    .withSource(URI.create("broker://rl"))
                    .withType("broker.rl")
                    .withDataContentType("text/plain")
                    .withData(("m" + i).getBytes(StandardCharsets.UTF_8))
                    .build();
                CompletableFuture<Void> f = fx.runtime().ingress().publish(topic, e);
                try {
                    f.get(10, TimeUnit.SECONDS);
                    success++;
                } catch (ExecutionException ex) {
                    if (ex.getCause() instanceof RateLimitedException) {
                        rejected++;
                    } else {
                        throw ex; // a non-rate-limit failure is a real test failure
                    }
                }
            }
            long after = fx.runtime().ingress().getMetrics().getRateLimited();
            long rejectedCounter = after - before;

            if (success != 2) {
                throw new AssertionError("expected 2 successful publishes (bucket=2), got " + success);
            }
            if (rejected != 3) {
                throw new AssertionError("expected 3 rejected publishes, got " + rejected);
            }
            if (rejectedCounter != 3) {
                throw new AssertionError("rateLimited metric expected +3, delta was " + rejectedCounter);
            }
        }
    }
}
