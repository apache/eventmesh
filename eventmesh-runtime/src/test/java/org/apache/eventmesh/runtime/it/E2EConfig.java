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

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Centralised real-infrastructure addresses for ALL integration tests. E2E tests auto-detect
 * availability via TCP probe and run against real brokers when reachable; they skip (via
 * {@code assumeTrue}) when the infra is down — no {@code -D} flags needed.
 *
 * <p>Override any value via {@code -D} system properties for CI or custom environments.</p>
 *
 * <ul>
 *   <li><b>RocketMQ 5.x</b> ({@value #ROCKETMQ5_NAMESRV}) — lite-topic streaming (v2 session)</li>
 *   <li><b>RocketMQ 4.x</b> ({@value #ROCKETMQ4_NAMESRV}) — 11-broker cluster (v1 pipeline)</li>
 *   <li><b>Nacos</b> ({@value #NACOS_ADDR}) — MetaStore / cluster coordination</li>
 *   <li><b>Kafka</b> ({@value #KAFKA_BOOTSTRAP}) — 3-broker SASL PLAINTEXT cluster</li>
 * </ul>
 */
public final class E2EConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(E2EConfig.class);

    // -------------------- addresses (override via -D; defaults are local-loopback placeholders) --------------------

    public static final String ROCKETMQ5_NAMESRV = System.getProperty("it.namesrv5", "127.0.0.1:9876");
    public static final String ROCKETMQ4_NAMESRV = System.getProperty("it.namesrv", "127.0.0.1:9876");
    public static final String NACOS_ADDR = System.getProperty("it.nacos", "127.0.0.1:8848");

    /** Kafka bootstrap servers (SASL PLAINTEXT). Pass the real cluster via -Dit.kafka.bootstrap. */
    public static final String KAFKA_BOOTSTRAP = System.getProperty("it.kafka.bootstrap",
        "127.0.0.1:9092");
    public static final String KAFKA_USER = System.getProperty("it.kafka.user", "");
    public static final String KAFKA_PASSWORD = System.getProperty("it.kafka.password", "");
    public static final String KAFKA_SASL_MECHANISM = "PLAIN";
    public static final String KAFKA_SECURITY_PROTOCOL = "SASL_PLAINTEXT";

    /** LLM gateway (OpenAI-compatible, no /v1 suffix). Pass via -Dllm.base.url. */
    public static final String LLM_BASE_URL = System.getProperty("llm.base.url", "https://api.openai.com");
    public static final String LLM_API_KEY = System.getProperty("llm.api.key",
        System.getenv().getOrDefault("LLM_API_KEY", ""));
    public static final String LLM_MODEL = System.getProperty("llm.model", "gpt-4o-mini");

    private E2EConfig() {
    }

    // -------------------- availability probes --------------------

    public static boolean rocketmq5Available() {
        return portOpen(firstHostPort(ROCKETMQ5_NAMESRV));
    }

    public static boolean rocketmq4Available() {
        return portOpen(firstHostPort(ROCKETMQ4_NAMESRV));
    }

    public static boolean nacosAvailable() {
        return portOpen(NACOS_ADDR);
    }

    public static boolean kafkaAvailable() {
        return portOpen(firstHostPort(KAFKA_BOOTSTRAP));
    }

    /** True when the LLM gateway is reachable AND an API key is configured (via -D or env). */
    public static boolean llmAvailable() {
        return !LLM_API_KEY.isEmpty() && portOpen(urlToHostPort(LLM_BASE_URL));
    }

    // -------------------- helpers --------------------

    /** Extract the first host:port from a comma-separated list. */
    private static String firstHostPort(String csv) {
        int comma = csv.indexOf(',');
        return comma < 0 ? csv : csv.substring(0, comma).trim();
    }

    /** Parse an http(s)://host[:port] URL into a host:port string for TCP probing. */
    private static String urlToHostPort(String url) {
        String s = url.replaceAll("^https?://", "");
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        if (!s.contains(":")) {
            s += url.startsWith("https://") ? ":443" : ":80";
        }
        return s;
    }

    /** TCP-probe a host:port with a 5s timeout (generous: dev env has unstable broker latency). */
    public static boolean portOpen(String hostPort) {
        try {
            int colon = hostPort.indexOf(':');
            String host = hostPort.substring(0, colon);
            int port = Integer.parseInt(hostPort.substring(colon + 1));
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), 5000);
                return true;
            }
        } catch (Exception e) {
            log.debug("E2EConfig probe {} -> CLOSED ({})", hostPort, e.toString());
            return false;
        }
    }

    /** Log the detected environment (call once at test-class init for visibility). */
    public static void logStatus() {
        log.info("E2EConfig: rocketmq5={}({}) rocketmq4={}({}) nacos={}({}) kafka={}({}) llm={}({})",
            ROCKETMQ5_NAMESRV, rocketmq5Available() ? "UP" : "DOWN",
            ROCKETMQ4_NAMESRV, rocketmq4Available() ? "UP" : "DOWN",
            NACOS_ADDR, nacosAvailable() ? "UP" : "DOWN",
            KAFKA_BOOTSTRAP, kafkaAvailable() ? "UP" : "DOWN",
            LLM_BASE_URL, llmAvailable() ? "UP" : "DOWN");
    }
}
