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
import org.apache.eventmesh.runtime.boot.EventMeshApplication;
import org.apache.eventmesh.runtime.cluster.InMemoryMetaStore;
import org.apache.eventmesh.runtime.ingress.UniIngressService;
import org.apache.eventmesh.runtime.offset.InMemoryOffsetStore;
import org.apache.eventmesh.runtime.session.AgentAnchoredStrategy;
import org.apache.eventmesh.runtime.session.AgentRegistrar;
import org.apache.eventmesh.runtime.session.BrokerGroupHealth;
import org.apache.eventmesh.runtime.session.ChannelStrategy;
import org.apache.eventmesh.runtime.session.Matchmaker;
import org.apache.eventmesh.runtime.session.SessionRegistry;
import org.apache.eventmesh.runtime.session.SessionRouter;
import org.apache.eventmesh.storage.rocketmq5.storage.RocketMQ5RemotingStoragePlugin;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Standalone EventMesh Runtime launcher — boots the server (with the mode-1 streaming-call session
 * layer) for the streaming demo. Run this FIRST, then start agent(s) in other terminals, then the
 * demo client.
 *
 * <p>Usage:<pre>
 *   gradle :eventmesh-runtime:startRuntime
 *   # override: -Dit.namesrv5=host:9876 -Dem.agentParent=my-agent -Dem.port=8080
 * </pre>
 */
public class RuntimeLauncher {

    private static final Logger log = LoggerFactory.getLogger(RuntimeLauncher.class);

    public static void main(String[] args) throws Exception {
        String namesrv = System.getProperty("it.namesrv5", E2EConfig.ROCKETMQ5_NAMESRV);
        String agentParent = System.getProperty("em.agentParent", "em-agent");
        String clientParent = System.getProperty("em.clientParent", "em-client");
        int trafficPort = Integer.getInteger("em.port", 8080);

        log.info("=== RuntimeLauncher: broker={} port={} ===", namesrv, trafficPort);
        log.info("  agentParent={} clientParent={}", agentParent, clientParent);

        // --- storage + pre-create lite topics ---
        MeshStoragePlugin storage = new RocketMQ5RemotingStoragePlugin();
        Properties props = new Properties();
        props.setProperty("namesrvAddr", namesrv);
        props.setProperty("eventMesh.server.rocketmq5.namesrvAddr", namesrv);
        storage.init(props);
        ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(agentParent, 1);
        ((RocketMQ5RemotingStoragePlugin) storage).createLiteCapableTopic(clientParent, 1);
        Thread.sleep(3_000L);

        // --- runtime + session layer (mode 1: streaming call) ---
        EventMeshApplication app = new EventMeshApplication(storage, new InMemoryOffsetStore(), trafficPort, 8081);
        app.runtime().withStorageConfig(props);
        UniIngressService ingress = app.runtime().ingress();
        SessionRegistry registry = new SessionRegistry(new InMemoryMetaStore(), 30_000L);
        AgentRegistrar agentRegistrar = new AgentRegistrar(registry,
            (String p) -> ingress.createLiteTopic(p, "init", 1), agentParent, clientParent);
        Matchmaker matchmaker = new Matchmaker(registry, BrokerGroupHealth.alwaysHealthy(), 1_800_000L);
        ChannelStrategy strategy = new AgentAnchoredStrategy(clientParent);
        SessionRouter router = new SessionRouter(ingress, registry, strategy, 120_000L);
        app.withAgentRegistrar(agentRegistrar).withMatchmaker(matchmaker).withSessionRouter(router);
        app.start();
        log.info("=== Runtime ready on http://localhost:{} (traffic) http://localhost:{} (admin) ===",
            trafficPort, 8081);

        // Block until killed (Ctrl+C). Add a shutdown hook so the process exits cleanly.
        Thread main = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("shutting down...");
            app.shutdown();
            main.interrupt();
        }));
        Thread.currentThread().join();
    }
}