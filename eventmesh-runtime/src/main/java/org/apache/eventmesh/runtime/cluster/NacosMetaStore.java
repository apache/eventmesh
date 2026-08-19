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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.config.NacosConfigService;

import lombok.extern.slf4j.Slf4j;

/**
 * Nacos-backed {@link MetaStore} (§13.2 / §15.5). Uses <em>two</em> Nacos subsystems:
 * <ul>
 *   <li><b>NamingService</b> for the {@code /em/instances/} prefix — Nacos's native service registry
 *       gives a real cluster-wide list of live instances ({@code selectInstances}), which the
 *       ConfigService alone cannot (it has no prefix-scan). This is what makes multi-instance
 *       partition assignment correct: every instance sees the full live set, not just itself.</li>
 *   <li><b>ConfigService</b> for everything else (assignment tables {@code /em/assignments/*},
 *       offsets {@code /em/offsets/*}, ACL rules {@code /em/acl/rules}, rate-limit rules) — these
 *       are single-key get/put or per-key watch, which ConfigService handles fine.</li>
 * </ul>
 *
 * <p><b>Instance record</b>: {@code /em/instances/<instanceId>} → value {@code "<timestamp>|<address>"}.
 * On {@code put} this registers a NamingService instance (ip:port parsed from the address, metadata
 * carries instanceId/timestamp/address); {@code getWithPrefix("/em/instances/")} lists all registered
 * instances and reassembles the {@code timestamp|address} value; {@code delete} deregisters.</p>
 *
 * <p><b>Limitations</b>: ConfigService has no prefix-scan for non-instance prefixes; {@code putIfAbsent}
 * is read-then-write (non-atomic). Partition ownership fencing uses {@link #tryAcquire} (Nacos 2.x
 * {@code publishConfigCas}), which is a true atomic CAS. This impl is compile-verified; runtime
 * verification needs a live Nacos server.</p>
 */
@Slf4j
public class NacosMetaStore implements MetaStore {

    public static final String GROUP = "EVENTMESH-META";
    /** NamingService service name under which EventMesh runtime instances register. */
    public static final String SERVICE = "eventmesh-runtime";
    /** NamingService service name under which cluster-wide subscriptions register (§13.2.6). */
    public static final String SUB_SERVICE = "eventmesh-subs";
    /** Prefix routed to NamingService (instance discovery). Must match ClusterMembership.INSTANCE_PREFIX. */
    private static final String INSTANCE_PREFIX = "/em/instances/";
    /** Prefix routed to NamingService (cluster-wide subscription discovery). Must match ClusterSubscriptionStore.SUB_PREFIX. */
    private static final String SUB_PREFIX = "/em/subs/";
    private static final long GET_TIMEOUT_MS = 3000L;

    private final ConfigService config;
    private final NamingService naming;
    private final java.util.Set<String> knownKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Last-observed subscription key→value snapshot, kept fresh by the NamingService watch; drives
     *  the per-key diff fired to {@link #subListeners}. */
    private final java.util.concurrent.ConcurrentHashMap<String, String> subSnapshot = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<MetaListener> subListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public NacosMetaStore(String serverAddr) throws NacosException {
        Properties props = new Properties();
        props.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        this.config = createConfigService(props);
        this.naming = NacosFactory.createNamingService(props);
    }

    /** Separate so tests can substitute mocks. */
    protected ConfigService createConfigService(Properties props) throws NacosException {
        return new NacosConfigService(props);
    }

    @Override
    public void watch(String prefix, MetaListener listener) {
        // Instance-prefix watch isn't needed — ClusterMembership polls liveInstances() each cycle.
        if (SUB_PREFIX.equals(prefix) || prefix != null && prefix.startsWith(SUB_PREFIX)) {
            // Subscriptions → NamingService.subscribe gives a REAL watch (ConfigService is per-dataId,
            // no prefix-watch). One naming listener re-lists + diffs against subSnapshot, firing the
            // per-key onChange contract every registered subListener expects.
            subListeners.add(listener);
            try {
                naming.subscribe(SUB_SERVICE, event -> refreshSubSnapshot());
            } catch (NacosException e) {
                log.warn("naming subscribe failed for {}: {}", SUB_SERVICE, e.toString());
            }
            return;
        }
        // Other prefixes use ConfigService per-key listeners.
        try {
            config.addListener(dataId(prefix), GROUP, new Listener() {

                @Override
                public void receiveConfigInfo(String configInfo) {
                    knownKeys.add(prefix);
                    listener.onChange(prefix, configInfo, false);
                }

                @Override
                public java.util.concurrent.Executor getExecutor() {
                    return null; // notify on nacos's callback thread
                }
            });
            knownKeys.add(prefix);
        } catch (NacosException e) {
            log.warn("nacos addListener failed for {}: {}", prefix, e.toString());
        }
    }

    @Override
    public void put(String key, String value) {
        if (isInstanceKey(key)) {
            namingRegister(key, value);
            knownKeys.add(key);
            return;
        }
        if (isSubKey(key)) {
            namingSubRegister(key, value);
            knownKeys.add(key);
            return;
        }
        try {
            config.publishConfig(dataId(key), GROUP, value, "text");
            knownKeys.add(key);
        } catch (NacosException e) {
            log.warn("nacos put failed for {}: {}", key, e.toString());
        }
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        // ConfigService has no CAS — read-then-write (non-atomic). For atomic acquire use
        // {@link #tryAcquire} (publishConfigCas). putIfAbsent is acceptable for instance/sub keys
        // (NamingService registerInstance is idempotent last-writer-wins).
        if (get(key) != null) {
            return false;
        }
        put(key, value);
        return true;
    }

    @Override
    public String get(String key) {
        if (isInstanceKey(key)) {
            return namingList().get(key);
        }
        if (isSubKey(key)) {
            return namingSubList().get(key);
        }
        try {
            String v = config.getConfig(dataId(key), GROUP, GET_TIMEOUT_MS);
            if (v != null) {
                knownKeys.add(key);
            }
            return v;
        } catch (NacosException e) {
            log.warn("nacos get failed for {}: {}", key, e.toString());
            return null;
        }
    }

    @Override
    public Map<String, String> getWithPrefix(String prefix) {
        if (INSTANCE_PREFIX.equals(prefix)) {
            // Real cluster-wide instance list via NamingService (the whole point of G6).
            Map<String, String> instances = namingList();
            knownKeys.addAll(instances.keySet());
            return instances;
        }
        if (SUB_PREFIX.equals(prefix)) {
            // Real cluster-wide subscription list via NamingService.
            Map<String, String> subs = namingSubList();
            knownKeys.addAll(subs.keySet());
            return subs;
        }
        // ConfigService has no prefix scan — best-effort over locally-observed keys (for non-instance
        // prefixes the consumers use per-key watch, not full-scan, so this is only a fallback).
        Map<String, String> out = new HashMap<>();
        for (String k : knownKeys) {
            if (k.startsWith(prefix)) {
                String v = get(k);
                if (v != null) {
                    out.put(k, v);
                }
            }
        }
        return out;
    }

    @Override
    public boolean delete(String key) {
        if (isInstanceKey(key)) {
            return namingDeregister(key);
        }
        if (isSubKey(key)) {
            return namingSubDeregister(key);
        }
        try {
            boolean ok = config.removeConfig(dataId(key), GROUP);
            knownKeys.remove(key);
            return ok;
        } catch (NacosException e) {
            log.warn("nacos delete failed for {}: {}", key, e.toString());
            return false;
        }
    }

    @Override
    public boolean tryAcquire(String key, String expectedOldValue, String newValue) {
        if (isInstanceKey(key) || isSubKey(key)) {
            // NamingService doesn't support CAS; fall back to plain put (last-writer-wins).
            // Instance/sub keys are heartbeats and registrations — not fencing-critical.
            put(key, newValue);
            return true;
        }
        try {
            // Nacos 2.x publishConfigCas(dataId, group, content, casMd5): the server rejects the
            // publish unless its current content MD5 matches casMd5. casMd5 = MD5 of the expected
            // current content (MD5("") for "key absent"), giving us a true atomic CAS.
            String expectedContent = expectedOldValue == null ? "" : expectedOldValue;
            String casMd5 = md5Hex(expectedContent);
            boolean ok = config.publishConfigCas(dataId(key), GROUP, newValue, casMd5);
            if (ok) {
                knownKeys.add(key);
            }
            return ok;
        } catch (NacosException e) {
            log.warn("nacos tryAcquire (publishConfigCas) failed for {}: {}", key, e.toString());
            return false;
        }
    }

    /** MD5 hex digest (lowercase, 32 chars). Nacos casMd5 is the MD5 of the expected content. */
    private static String md5Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }

    /**
     * Nacos ConfigService dataIds reject {@code /}, {@code #} and several other path chars with
     * "dataId invalid". The MetaStore key space uses slash-delimited paths
     * ({@code /em/subs/<topic>/<clientId>}, {@code /em/assignments/<topic>#<partition>}, etc.), so
     * encode both {@code /} and {@code #} → {@code .} for the dataId. {@code knownKeys} keeps the
     * original key, so {@code getWithPrefix} matching is unaffected.
     */
    private static String dataId(String key) {
        return key == null ? null : key.replace('/', '.').replace('#', '.');
    }

    // ---- NamingService routing for /em/instances/* ----

    private static boolean isInstanceKey(String key) {
        return key != null && key.startsWith(INSTANCE_PREFIX);
    }

    private static boolean isSubKey(String key) {
        return key != null && key.startsWith(SUB_PREFIX);
    }

    // ---- NamingService routing for /em/subs/* (cluster-wide subscription discovery) ----

    /**
     * Register one subscription as a NamingService instance under {@link #SUB_SERVICE}. The full
     * MetaStore key + encoded value ride in metadata so {@link #namingSubList()} can reassemble them
     * exactly. instanceId (unique per sub) = the key with {@code /}→{@code #} (NamingService
     * instances are matched by ip:port, which is a shared placeholder here, so the instance-based
     * {@code deregisterInstance} keyed on the Instance is used for delete).
     */
    private void namingSubRegister(String key, String value) {
        Instance inst = subInstance(key, value);
        try {
            naming.registerInstance(SUB_SERVICE, inst);
            subSnapshot.put(key, value);
        } catch (NacosException e) {
            log.warn("naming sub register failed for {}: {}", key, e.toString());
        }
    }

    private boolean namingSubDeregister(String key) {
        try {
            // Reconstruct the same instance (instanceId + ip + port) for an instance-based deregister.
            naming.deregisterInstance(SUB_SERVICE, subInstance(key, null));
            subSnapshot.remove(key);
            knownKeys.remove(key);
            return true;
        } catch (NacosException e) {
            log.warn("naming sub deregister failed for {}: {}", key, e.toString());
            return false;
        }
    }

    /** List all subscriptions and reassemble {@code key→value} from each instance's metadata. */
    private Map<String, String> namingSubList() {
        Map<String, String> out = new HashMap<>();
        try {
            for (Instance inst : naming.selectInstances(SUB_SERVICE, true)) {
                Map<String, String> meta = inst.getMetadata();
                if (meta == null) {
                    continue;
                }
                String key = meta.get("key");
                String value = meta.get("value");
                if (key != null && value != null) {
                    out.put(key, value);
                }
            }
        } catch (NacosException e) {
            log.warn("naming sub selectInstances failed: {}", e.toString());
        }
        return out;
    }

    /**
     * Re-list subscriptions, diff against {@link #subSnapshot}, and fire per-key
     * {@link MetaListener#onChange} to every registered subListener. This is the bridge between
     * NamingService's "the instance set changed" event and the MetaStore per-key watch contract.
     */
    private void refreshSubSnapshot() {
        Map<String, String> latest = namingSubList();
        // Added / changed.
        for (Map.Entry<String, String> e : latest.entrySet()) {
            String prev = subSnapshot.put(e.getKey(), e.getValue());
            if (prev == null || !prev.equals(e.getValue())) {
                fireSubChange(e.getKey(), e.getValue(), false);
            }
        }
        // Removed.
        for (String k : subSnapshot.keySet()) {
            if (!latest.containsKey(k)) {
                subSnapshot.remove(k);
                fireSubChange(k, null, true);
            }
        }
    }

    private void fireSubChange(String key, String value, boolean deleted) {
        for (MetaListener l : subListeners) {
            try {
                l.onChange(key, value, deleted);
            } catch (Exception e) {
                log.warn("sub listener error for {}: {}", key, e.toString());
            }
        }
    }

    private static Instance subInstance(String key, String value) {
        Instance inst = new Instance();
        inst.setIp("0.0.0.0");
        inst.setPort(0);
        inst.setInstanceId(key.replace('/', '#'));
        Map<String, String> meta = new HashMap<>();
        meta.put("key", key);
        if (value != null) {
            meta.put("value", value);
        }
        inst.setMetadata(meta);
        return inst;
    }

    /** Register (or refresh) one instance: value is "&lt;timestamp&gt;|&lt;address&gt;". */
    private void namingRegister(String key, String value) {
        String instanceId = key.substring(INSTANCE_PREFIX.length());
        String[] parts = value.split("\\|", 2);
        String timestamp = parts.length > 0 ? parts[0] : "0";
        String address = parts.length > 1 ? parts[1] : "";
        String[] hp = address.split(":", 2);
        String ip = hp.length > 0 && !hp[0].isEmpty() ? hp[0] : "0.0.0.0";
        int port = hp.length > 1 ? parsePort(hp[1]) : 0;
        try {
            Instance inst = new Instance();
            inst.setIp(ip);
            inst.setPort(port);
            inst.setInstanceId(instanceId);
            Map<String, String> meta = new HashMap<>();
            meta.put("instanceId", instanceId);
            meta.put("timestamp", timestamp);
            meta.put("address", address);
            inst.setMetadata(meta);
            naming.registerInstance(SERVICE, inst);
        } catch (NacosException e) {
            // Re-throw so ClusterMembership.heartbeat() can detect the Meta outage and the
            // PartitionOwnership lease gate can stop polling (split-brain prevention). Swallowing
            // here would let a partitioned instance keep polling with a stale "self owns all" view.
            throw new RuntimeException("naming register failed for " + instanceId, e);
        }
    }

    /** List all instances and reassemble the "&lt;timestamp&gt;|&lt;address&gt;" value ClusterMembership expects. */
    private Map<String, String> namingList() {
        Map<String, String> out = new HashMap<>();
        try {
            for (Instance inst : naming.selectInstances(SERVICE, true)) {
                Map<String, String> meta = inst.getMetadata();
                String instanceId = meta != null ? meta.get("instanceId") : null;
                if (instanceId == null) {
                    instanceId = inst.getInstanceId();
                }
                if (instanceId == null) {
                    continue;
                }
                String timestamp = meta != null ? meta.getOrDefault("timestamp", "0") : "0";
                String address = meta != null ? meta.getOrDefault("address", inst.getIp() + ":" + inst.getPort())
                    : inst.getIp() + ":" + inst.getPort();
                out.put(INSTANCE_PREFIX + instanceId, timestamp + "|" + address);
            }
        } catch (NacosException e) {
            log.warn("naming selectInstances failed: {}", e.toString());
        }
        return out;
    }

    /** Deregister by instanceId (look up ip:port first). */
    private boolean namingDeregister(String key) {
        String instanceId = key.substring(INSTANCE_PREFIX.length());
        try {
            for (Instance inst : naming.selectInstances(SERVICE, false)) {
                Map<String, String> meta = inst.getMetadata();
                String iid = meta != null ? meta.get("instanceId") : null;
                if (instanceId.equals(iid) || instanceId.equals(inst.getInstanceId())) {
                    naming.deregisterInstance(SERVICE, inst.getIp(), inst.getPort());
                    knownKeys.remove(key);
                    return true;
                }
            }
            return false;
        } catch (NacosException e) {
            log.warn("naming deregister failed for {}: {}", instanceId, e.toString());
            return false;
        }
    }

    private static int parsePort(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
