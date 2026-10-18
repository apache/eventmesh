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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

/**
 * Regression test for the graceful-leave bug in {@link NacosMetaStore}: {@code delete()} of an
 * instance key must actually deregister the Nacos registration.
 *
 * <p>With nacos-client 2.1.1, {@code selectInstances(service, false)} returns an EMPTY list on the
 * very client that registered the instance (verified against a live 2.1.1 server — the
 * all-instances query never populates the client's local ServiceInfoHolder). The original
 * lookup-based deregister therefore matched nothing and {@link ClusterMembership#leave()} became a
 * silent no-op: the departed instance stayed in the live set until the server's 15s beat timeout,
 * so {@code /session/recommend} kept recommending a dead instance and the partition assigner kept
 * spreading partitions over it.
 *
 * <p>The fake {@link NamingService} below mirrors that quirk exactly: {@code selectInstances} is
 * hard-wired to return an empty list, so the local-registration test can only pass if
 * deregistration replays the registered {@link Instance} instead of looking it up.
 */
public class NacosMetaStoreDeregisterTest {

    private static final String INSTANCE_KEY = "/em/instances/lb-A-r123";
    private static final String HEARTBEAT_VALUE = "1792000000000|localhost:20105|0|0|0|0.01";

    @Test
    @DisplayName("delete() deregisters the exact Instance this client registered (no listing lookup)")
    void deleteDeregistersRegisteredInstanceWithoutListingLookup() throws Exception {
        Fixture fx = Fixture.create();

        fx.store.put(INSTANCE_KEY, HEARTBEAT_VALUE);

        // The registration carried the full identity the deregistration needs.
        assertEquals(1, fx.naming.registered.size(), "put() must register exactly one instance");
        Instance registered = fx.naming.registered.get(0);
        assertEquals("lb-A-r123", registered.getInstanceId());
        assertEquals("localhost", registered.getIp());
        assertEquals(20105, registered.getPort());

        assertTrue(fx.store.delete(INSTANCE_KEY),
            "delete() must succeed for a locally registered instance");

        // The whole point: deregister happened even though selectInstances() returns nothing.
        assertEquals(1, fx.naming.deregistered.size(),
            "delete() must deregister even when the instance listing is empty (nacos-client 2.1.1 quirk)");
        Instance gone = fx.naming.deregistered.get(0);
        assertEquals("lb-A-r123", gone.getInstanceId());
        assertEquals("localhost", gone.getIp());
        assertEquals(20105, gone.getPort());
        assertEquals("eventmesh-runtime", fx.naming.deregisteredServices.get(0));
    }

    @Test
    @DisplayName("delete() falls back to the listing for instances registered by a previous JVM")
    void deleteFallsBackToListingForForeignRegistrations() throws Exception {
        final Fixture fx = Fixture.create();
        // A previous JVM registered lb-old (this store never saw the registration): only the
        // server-side listing knows about it.
        Instance foreign = new Instance();
        foreign.setIp("localhost");
        foreign.setPort(20105);
        foreign.setInstanceId("lb-old");
        Map<String, String> meta = new HashMap<>();
        meta.put("instanceId", "lb-old");
        foreign.setMetadata(meta);
        fx.naming.listable.add(foreign);

        assertTrue(fx.store.delete("/em/instances/lb-old"));
        assertEquals(1, fx.naming.deregistered.size(),
            "listing fallback must deregister the foreign instance");
        assertEquals("lb-old", fx.naming.deregistered.get(0).getInstanceId());
    }

    @Test
    @DisplayName("delete() is a no-op (false) for an instance that is neither local nor listed")
    void deleteReturnsFalseForUnknownInstance() throws Exception {
        Fixture fx = Fixture.create();
        assertTrue(!fx.store.delete("/em/instances/never-registered"));
        assertTrue(fx.naming.deregistered.isEmpty(),
            "unknown instance must not trigger a deregister call");
    }

    // ---------------------------------------------------------------------------------------------
    // test doubles
    // ---------------------------------------------------------------------------------------------

    /** A NacosMetaStore wired to in-process fakes + the recorder to assert against. */
    private static final class Fixture {

        final NacosMetaStore store;
        final RecordingNamingService naming;

        private Fixture(NacosMetaStore store, RecordingNamingService naming) {
            this.store = store;
            this.naming = naming;
        }

        static Fixture create() throws Exception {
            RecordingNamingService recorder = new RecordingNamingService();
            NamingService proxy = (NamingService) Proxy.newProxyInstance(
                NamingService.class.getClassLoader(),
                new Class<?>[] {NamingService.class},
                recorder);
            NacosMetaStore store = new NacosMetaStore("test-server:8848") {

                @Override
                protected ConfigService createConfigService(Properties props) {
                    return fakeConfigService();
                }

                @Override
                protected NamingService createNamingService(Properties props) {
                    return proxy;
                }
            };
            return new Fixture(store, recorder);
        }
    }

    /**
     * Records register/deregister calls. {@code selectInstances} ALWAYS returns {@link #listable}
     * (empty unless seeded) — mirroring nacos-client 2.1.1, where the registering client's own
     * all-instances listing comes back empty.
     */
    private static final class RecordingNamingService implements InvocationHandler {

        private final List<Instance> registered = new CopyOnWriteArrayList<>();
        private final List<Instance> deregistered = new CopyOnWriteArrayList<>();
        private final List<String> deregisteredServices = new CopyOnWriteArrayList<>();
        private final List<Instance> listable = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "registerInstance":
                    if (args != null && args.length == 2 && args[1] instanceof Instance) {
                        registered.add((Instance) args[1]);
                    }
                    return null;
                case "deregisterInstance":
                    deregisteredServices.add((String) args[0]);
                    deregistered.add((Instance) args[1]);
                    return null;
                case "selectInstances":
                case "getAllInstances":
                    return new ArrayList<>(listable);
                case "toString":
                    return "RecordingNamingService";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return defaultFor(method.getReturnType());
            }
        }

        private static Object defaultFor(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == void.class) {
                return null;
            }
            if (!type.isPrimitive()) {
                return null;
            }
            throw new IllegalStateException("unexpected primitive return type: " + type);
        }
    }

    /** ConfigService fake: the instance-key paths under test never touch the config service. */
    private static ConfigService fakeConfigService() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "toString":
                    return "FakeConfigService";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
            }
        };
        return (ConfigService) Proxy.newProxyInstance(
            ConfigService.class.getClassLoader(),
            new Class<?>[] {ConfigService.class},
            handler);
    }
}
