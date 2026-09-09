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

package org.apache.eventmesh.connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages {@link ConnectorRuntime} instances, keyed by connector id. Supports both:
 * <ul>
 *   <li><b>dynamic</b> — {@link #startConnector(String, ConnectorDef)} / {@link #stopConnector(String)},
 *       driven by the runtime's scheduler over {@code /control/*} (§8). The connector classes are
 *       loaded via {@code Class.forName} from the fat image's startup classpath.</li>
 *   <li><b>static</b> — {@link #add} + {@link #start()}, driven by {@code -D} flags at process start
 *       (backward-compatible fallback when not registered with a runtime).</li>
 * </ul>
 */
@Slf4j
public class ConnectorManager {

    private final EventMeshEndpoint endpoint;
    private final ConnectorOffsetStore offsetStore;
    private final Map<String, ConnectorRuntime> runtimes = new ConcurrentHashMap<>();
    private final AtomicLong staticIdGen = new AtomicLong();

    public ConnectorManager(EventMeshEndpoint endpoint, ConnectorOffsetStore offsetStore) {
        this.endpoint = endpoint;
        this.offsetStore = offsetStore;
    }

    // ---- dynamic (runtime-driven) ----

    /** #5382: the generation each connector currently runs at (fencing). */
    private final ConcurrentHashMap<String, Long> runningGenerations = new ConcurrentHashMap<>();

    /**
     * #5382: the fencing decision, isolated for testability. Accepts a start iff its
     * generation is >= the currently running one (equal = idempotent re-push, greater =
     * superseding assignment). A stale delayed start (< running) is rejected.
     */
    boolean shouldAcceptStart(String id, long incomingGen) {
        Long runningGen = runningGenerations.get(id);
        return runningGen == null || incomingGen >= runningGen;
    }

    /** Records the generation a connector now runs at (fencing bookkeeping, #5382). */
    void recordRunningGeneration(String id, long generation) {
        runningGenerations.put(id, generation);
    }

    /** Test accessor: the running generation of a connector (fencing, #5382). */
    public long runningGenerationForTest(String id) {
        Long g = runningGenerations.get(id);
        return g == null ? -1L : g;
    }

    /**
     * Build + start a connector by id. Idempotent: a no-op if {@code id} is already running (the
     * runtime re-pushes start on its own restart; the worker must not restart a healthy connector).
     * Throws if the connector class cannot be loaded/initialised.
     */
    public synchronized void startConnector(String id, ConnectorDef def) {
        // #5382: generation fencing — a delayed /control/start from a superseded assignment
        // (worker restart, membership change, def update) must NOT take over a connector that
        // already runs at a newer generation. The stale worker keeps its copy only until its
        // stop arrives; exactly one generation is authoritative.
        long incomingGen = def.getGeneration();
        if (!shouldAcceptStart(id, incomingGen)) {
            log.warn("rejecting stale start for connector {}: gen {} < running gen {}"
                + " (superseded assignment)", id, incomingGen, runningGenerations.get(id));
            return;
        }
        ConnectorRuntime existing = runtimes.get(id);
        if (existing != null && existing.isRunning()) {
            Long runningGen = runningGenerations.get(id);
            if (runningGen != null && incomingGen == runningGen) {
                log.debug("connector {} already running at gen {} — start no-op", id, incomingGen);
                return;
            }
            // newer generation: fall through and stop the old runtime before rebuild
        }
        if (existing != null) {
            try {
                existing.stop();
            } catch (Exception ignored) {
                // best-effort cleanup before rebuild
            }
        }
        runningGenerations.put(id, incomingGen);
        try {
            ConnectorRuntime rt = buildRuntime(def);
            rt.setOffsetStore(offsetStore);
            runtimes.put(id, rt);
            rt.start();
            log.info("connector started: id={} mode={} topic={}", id, def.getMode(), def.getTopic());
        } catch (Exception e) {
            log.error("failed to start connector {}: {}", id, e.toString(), e);
            throw new RuntimeException("start connector '" + id + "' failed: " + e.getMessage(), e);
        }
    }

    public synchronized void stopConnector(String id) {
        runningGenerations.remove(id);
        ConnectorRuntime rt = runtimes.remove(id);
        if (rt != null) {
            try {
                rt.stop();
            } catch (Exception ignored) {
                // best-effort
            }
            log.info("connector stopped: id={}", id);
        }
    }

    public List<Map<String, Object>> status() {
        List<Map<String, Object>> out = new ArrayList<>();
        runtimes.forEach((id, rt) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", id);
            e.put("running", rt.isRunning());
            e.put("source", rt.hasSource());
            e.put("sink", rt.hasSink());
            e.put("sourcePublished", rt.getSourcePublishedCount());
            e.put("sinkProcessed", rt.getSinkProcessedCount());
            out.add(e);
        });
        return out;
    }

    /** Build a {@link ConnectorRuntime} from a def: {@code Class.forName} source/sink + {@code init}. */
    @SuppressWarnings("unchecked")
    private ConnectorRuntime buildRuntime(ConnectorDef def) throws Exception {
        String mode = def.getMode() == null ? "source" : def.getMode();
        boolean isSource = "source".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);
        boolean isSink = "sink".equalsIgnoreCase(mode) || "both".equalsIgnoreCase(mode);

        Properties props = new Properties();
        if (def.getConfig() != null) {
            props.putAll(def.getConfig());
        }

        SourceConnector source = null;
        SinkConnector sink = null;
        if (isSource && def.getClassName() != null) {
            source = (SourceConnector) Class.forName(def.getClassName()).getDeclaredConstructor().newInstance();
            source.init(props);
        }
        if (isSink) {
            String sinkClass = def.getSinkClass() != null ? def.getSinkClass() : def.getClassName();
            if (sinkClass != null) {
                sink = (SinkConnector) Class.forName(sinkClass).getDeclaredConstructor().newInstance();
                sink.init(props);
            }
        }
        if (source == null && sink == null) {
            throw new IllegalArgumentException("no source or sink created for mode=" + mode
                + " sourceClass=" + def.getClassName() + " sinkClass=" + def.getSinkClass());
        }

        String topic = def.getTopic() != null ? def.getTopic() : "default-topic";
        String clientId = def.getClientId() != null ? def.getClientId() : def.getId();
        if (source != null && sink != null) {
            return new ConnectorRuntime(source, sink, endpoint, topic, clientId, 100, 1000L);
        } else if (source != null) {
            return new ConnectorRuntime(source, endpoint, topic);
        } else {
            return new ConnectorRuntime(sink, endpoint, clientId, 100, 1000L);
        }
    }

    // ---- static (-D driven, backward compatible) ----

    public ConnectorManager add(String id, ConnectorRuntime runtime) {
        runtimes.put(id, runtime);
        return this;
    }

    public ConnectorManager add(ConnectorRuntime runtime) {
        return add("static-" + staticIdGen.incrementAndGet(), runtime);
    }

    /** Start all statically-registered runtimes. */
    public void start() {
        runtimes.values().forEach(rt -> {
            try {
                rt.start();
            } catch (Exception e) {
                log.error("failed to start connector runtime", e);
            }
        });
        log.info("connector manager started: {} runtime(s)", runtimes.size());
    }

    /** Stop all runtimes (static + dynamic). */
    public void stop() {
        runtimes.values().forEach(rt -> {
            try {
                rt.stop();
            } catch (Exception e) {
                log.warn("error stopping connector runtime", e);
            }
        });
        log.info("connector manager stopped");
    }

    public int size() {
        return runtimes.size();
    }

    public Collection<ConnectorRuntime> getRuntimes() {
        return runtimes.values();
    }
}
