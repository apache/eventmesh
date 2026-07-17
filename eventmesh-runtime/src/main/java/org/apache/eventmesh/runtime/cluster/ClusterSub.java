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

import org.apache.eventmesh.runtime.subscription.CloudEventFilter;
import org.apache.eventmesh.runtime.subscription.DistributionMode;

/**
 * A cluster-wide subscription entry cached from {@link MetaStore}. Serialized pipe-delimited so the
 * store can rebuild the local view from Meta on watch events without a JSON dependency.
 */
public final class ClusterSub {

    private final String clientId;
    private final String instanceId;
    private final DistributionMode mode;
    private final String filterSpec; // "" = accept-all, "type:<value>" = type match

    public ClusterSub(String clientId, String instanceId, DistributionMode mode, String filterSpec) {
        this.clientId = clientId;
        this.instanceId = instanceId;
        this.mode = mode;
        this.filterSpec = filterSpec == null ? "" : filterSpec;
    }

    public String getClientId() {
        return clientId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public DistributionMode getMode() {
        return mode;
    }

    public CloudEventFilter filter() {
        if (filterSpec.isEmpty() || filterSpec.startsWith("type:")) {
            return filterSpec.isEmpty() ? CloudEventFilter.ACCEPT_ALL
                : CloudEventFilter.byType(filterSpec.substring("type:".length()));
        }
        return CloudEventFilter.ACCEPT_ALL;
    }

    /** Serialize as {@code clientId|instanceId|mode|filterSpec}. */
    public String encode() {
        return clientId + "|" + instanceId + "|" + mode.name() + "|" + filterSpec;
    }

    public static ClusterSub decode(String s) {
        String[] parts = s.split("\\|", 4);
        return new ClusterSub(parts[0], parts[1], DistributionMode.valueOf(parts[2]),
            parts.length > 3 ? parts[3] : "");
    }
}
