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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic partition→instance assignment (§13.2.3 / §13.2.8).
 *
 * <p>The live instance set is sorted (so every instance computes the same map independently), and
 * partition {@code p} is owned by {@code sortedInstances[p % n]}. This is the degenerate
 * "self-allocated" plan; the production Meta-led path can override it with lease-backed ownership
 * carrying a generation for fencing — both reduce to the same {@code partition → owner} shape that
 * the storage layer's {@code assignPartitions} consumes.</p>
 *
 * <p>Stability property: adding/removing an instance moves only roughly {@code partitions/n}
 * partitions (consistent-hash-ish behaviour modulo the modulo scheme). For the uni runtime's
 * self-managed offset this is acceptable — a moved partition resumes from the persisted offset.</p>
 */
public final class PartitionAssigner {

    private PartitionAssigner() {
    }

    /**
     * @param partitionCount total partitions for the topic
     * @param liveInstances  live instance ids (any order; sorted internally)
     * @return partition → owner instance id
     */
    public static Map<Integer, String> assign(int partitionCount, List<String> liveInstances) {
        if (liveInstances.isEmpty()) {
            throw new IllegalArgumentException("liveInstances must not be empty");
        }
        List<String> sorted = new ArrayList<>(liveInstances);
        Collections.sort(sorted);
        Map<Integer, String> out = new HashMap<>();
        for (int p = 0; p < partitionCount; p++) {
            out.put(p, sorted.get(p % sorted.size()));
        }
        return out;
    }

    /**
     * The partitions a given instance owns under an assignment.
     */
    public static List<Integer> ownedBy(Map<Integer, String> assignment, String instanceId) {
        List<Integer> owned = new ArrayList<>();
        for (Map.Entry<Integer, String> e : assignment.entrySet()) {
            if (e.getValue().equals(instanceId)) {
                owned.add(e.getKey());
            }
        }
        Collections.sort(owned);
        return owned;
    }
}
