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

import org.apache.eventmesh.api.storage.MeshStoragePlugin;
import org.apache.eventmesh.runtime.boot.UniRuntime;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Properties;

import org.junit.jupiter.api.Assumptions;

/**
 * Shared bootstrap for "feature" e2e tests that exercise the uni runtime against a REAL message
 * broker. Picks the right backend per system property, skips cleanly when the broker is not
 * configured so the test never fails spuriously in CI without an environment.
 *
 * <p>Usage:
 * <pre>
 *   {@code
 *   @EnabledIfSystemProperty(named = "it.feature.storage", matches = "rocketmq4")
 *   class FooOverBrokerTest {
 *       @Test
 *       void foo() throws Exception {
 *           try (var fx = FeatureBrokerHarness.of("rocketmq4")) { fx.start(); ... }
 *       }
 *   }
 *   }
 * </pre>
 *
 * <p>Backends:
 * <ul>
 *   <li>{@code rocketmq} — RocketMQ 4.9.x namesrv (SPI key {@code rocketmq}, system property
 *       {@code it.namesrv}, default {@code localhost:9876}).</li>
 *   <li>{@code rocketmq5} — RocketMQ 5.x namesrv (SPI key {@code rocketmq5}, system property
 *       {@code it.namesrv5}, default {@code localhost:9876}).</li>
 * </ul>
 *
 * <p>Skipped when neither backend is configured — the test reports as ASSUMED and the build
 * proceeds with the rest of the suite.
 */
final class FeatureBrokerHarness implements AutoCloseable {

    private final UniRuntime runtime;

    private FeatureBrokerHarness(UniRuntime runtime) {
        this.runtime = runtime;
    }

    UniRuntime runtime() {
        return runtime;
    }

    /** Resolve backend from {@code -Dit.feature.storage}, or skip the test. */
    static FeatureBrokerHarness of(String defaultBackend) {
        String backend = System.getProperty("it.feature.storage", defaultBackend);
        if (backend == null || backend.isEmpty()) {
            throw newAssumed("no it.feature.storage set — skipping real-broker feature test");
        }
        // Backwards-compatible alias: 'rocketmq4' still means the 4.9 plugin (SPI key 'rocketmq').
        String spiKey = "rocketmq4".equals(backend) ? "rocketmq" : backend;
        return switch (spiKey) {
            case "rocketmq" -> rocketMq4();
            case "rocketmq5" -> rocketMq5();
            default -> throw newAssumed("unsupported feature backend: " + backend);
        };
    }

    private static FeatureBrokerHarness rocketMq4() {
        String namesrv = System.getProperty("it.namesrv", "localhost:9876");
        // SPI key is "rocketmq" — the storage plugin file registers with that name, not "rocketmq4".
        return build("rocketmq", namesrv);
    }

    private static FeatureBrokerHarness rocketMq5() {
        String namesrv = System.getProperty("it.namesrv5", "localhost:9876");
        return build("rocketmq5", namesrv);
    }

    private static FeatureBrokerHarness build(String storageType, String namesrv) {
        MeshStoragePlugin storage = lookup(storageType);
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq.namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.kafka.namesrvAddr", namesrv);
        // BrokerDiscoverer.ensureTopicOnReachableBroker() keys off `it.storage` (the legacy
        // system property used by every other broker IT), so propagate our backend name into
        // that slot before the test method touches it.
        if (System.getProperty("it.storage") == null) {
            System.setProperty("it.storage", storageType);
        }
        UniRuntime rt = new UniRuntime(storage, new InMemoryOffsetStore(), 200L, 500L, 100, 500L);
        rt.withStorageConfig(props);
        return new FeatureBrokerHarness(rt);
    }

    private static MeshStoragePlugin lookup(String type) {
        MeshStoragePlugin p = EventMeshExtensionFactory.getExtension(MeshStoragePlugin.class, type);
        if (p == null) {
            throw newAssumed("no MeshStoragePlugin registered for '" + type
                + "' — declare the storage plugin on the runtime test classpath");
        }
        return p;
    }

    /** Boot the runtime and settle the broker connection (the pull consumer needs ~3s for rebalance). */
    void start() throws Exception {
        runtime.start();
        Thread.sleep(3_000L);
    }

    @Override
    public void close() {
        try {
            runtime.shutdown();
        } catch (Exception ignored) {
            // best-effort: a leaked plugin close after a failed assertion is preferable to test crashes
        }
    }

    private static RuntimeException newAssumed(String message) {
        Assumptions.abort(message);
        // unreachable — Assumptions.abort throws TestAbortedException, but the compiler doesn't know
        return new IllegalStateException(message);
    }
}
