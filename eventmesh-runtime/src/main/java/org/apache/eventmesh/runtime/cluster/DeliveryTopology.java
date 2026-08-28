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

/**
 * Delivery topology for the uni runtime (§13.2). Two modes:
 *
 * <ul>
 *   <li>{@link #LOCAL_STICKY_PULL} (default): every instance polls every partition of each
 *       subscribed topic. The in-process {@link PartitionOwnership} is not started; the pull
 *       loop falls back to poll-all ({@code ownedPartitions} returns {@code null}). This is
 *       the single-instance / low-throughput default — simple, no Meta dependency.</li>
 *   <li>{@link #PARTITION_OWNED_PULL}: each instance acquires ownership of a strict subset of
 *       topic partitions via {@link MetaStore} CAS + {@link FencingToken} fencing (§13.2.8).
 *       The pull loop polls only owned partitions, enabling horizontal scale-out with no
 *       duplicate consumption. Requires a {@link MetaStore} backend (Nacos / etcd / ZK).</li>
 * </ul>
 *
 * <p>Selected via {@code eventmesh.delivery.topology} property. Missing / null →
 * {@link #LOCAL_STICKY_PULL} (backward compatible). Unknown value → {@link IllegalArgumentException}
 * (fail-fast — a typo must not silently degrade to single-instance mode).</p>
 */
public enum DeliveryTopology {

    LOCAL_STICKY_PULL,
    PARTITION_OWNED_PULL;

    /**
     * Parse the topology from a config string.
     *
     * @param config raw value from {@code eventmesh.delivery.topology}; {@code null} or blank →
     *               {@link #LOCAL_STICKY_PULL} (default, backward compatible)
     * @return the resolved topology
     * @throws IllegalArgumentException if the value is non-blank but does not match a known name
     */
    public static DeliveryTopology fromConfig(String config) {
        if (config == null || config.isBlank()) {
            return LOCAL_STICKY_PULL;
        }
        try {
            return DeliveryTopology.valueOf(config.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Unknown delivery topology: '" + config + "'. Valid values: LOCAL_STICKY_PULL, PARTITION_OWNED_PULL");
        }
    }
}
