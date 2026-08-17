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

package org.apache.eventmesh.runtime.session;

/**
 * Whether a broker-group shard (an {@code agent-parent-<i>}) is healthy enough to route new agents
 * onto. The matchmaker skips agents whose parent is unhealthy.
 *
 * <p>Phase 1 ships {@link #alwaysHealthy()} (all groups healthy). Phase 6 wires a real impl backed by
 * the storage plugin's reachability probes + route-cache invalidation on master failover (§4.4/§15).</p>
 */
@FunctionalInterface
public interface BrokerGroupHealth {

    /**
     * @return true if the given parent shard ({@code agent-parent-<i>}) is reachable/healthy.
     */
    boolean healthy(String parent);

    /** Default: every shard is healthy. */
    static BrokerGroupHealth alwaysHealthy() {
        return parent -> true;
    }
}
