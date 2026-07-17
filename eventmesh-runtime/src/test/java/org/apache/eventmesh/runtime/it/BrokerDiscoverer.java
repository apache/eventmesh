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

/**
 * Test helper for broker ITs. Creates the test topic cluster-wide (so the RocketMQ storage plugin's
 * poll/partition model is unchanged) and waits for the namesrv route to propagate before returning,
 * so the first publish doesn't race the route and fail with "no broker for topic queue 0".
 *
 * <p>Resilience to a partially-unreachable cluster (e.g. one of 11 brokers has a down data port) is
 * handled in the storage plugin itself: {@code RocketMQRemotingStoragePlugin} probes each broker's
 * data port when building its route and excludes unreachable brokers from the send/poll queue space,
 * so tests automatically route around a dead broker without test-side topic scoping (which would
 * change the partition count and break the multi-instance assignment model).</p>
 */
final class BrokerDiscoverer {

    private BrokerDiscoverer() {
    }

    /**
     * Create {@code topic} (rocketmq only — no-op otherwise) cluster-wide and wait for its route.
     *
     * @param namesrv  nameserver address (host:9876)
     * @param topic    topic to create
     * @param queueNum read/write queue count per broker
     */
    static void ensureTopicOnReachableBroker(String namesrv, String topic, int queueNum) throws Exception {
        if (!"rocketmq".equalsIgnoreCase(System.getProperty("it.storage", "rocketmq"))) {
            return; // kafka auto-creates topics
        }
        org.apache.rocketmq.tools.admin.DefaultMQAdminExt admin =
            new org.apache.rocketmq.tools.admin.DefaultMQAdminExt();
        admin.setNamesrvAddr(namesrv);
        admin.start();
        try {
            try {
                org.apache.rocketmq.common.protocol.body.ClusterInfo info = admin.examineBrokerClusterInfo();
                String cluster = info.getClusterAddrTable().keySet().iterator().next();
                admin.createTopic(cluster, topic, queueNum);
            } catch (org.apache.rocketmq.client.exception.MQClientException e) {
                // already exists — safe to ignore
            }
            waitForRoute(admin, topic);
        } finally {
            admin.shutdown();
        }
    }

    /**
     * Wait for the namesrv route of {@code topic} to appear (the broker registers a newly-created
     * topic on its next heartbeat, ~5s). Without this, a publish immediately after {@code createTopic}
     * races the route and fails with "no broker for topic queue 0".
     */
    private static void waitForRoute(org.apache.rocketmq.tools.admin.DefaultMQAdminExt admin, String topic)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                org.apache.rocketmq.common.protocol.route.TopicRouteData route = admin.examineTopicRouteInfo(topic);
                if (route != null && route.getBrokerDatas() != null && !route.getBrokerDatas().isEmpty()
                    && route.getQueueDatas() != null && !route.getQueueDatas().isEmpty()) {
                    return;
                }
            } catch (Exception ignored) {
                // route not propagated yet — retry
            }
            Thread.sleep(500);
        }
    }
}

