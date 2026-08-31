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

package org.apache.eventmesh.runtime.security;

import org.apache.eventmesh.common.wire.EventMeshFrame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ordered ingress security pipeline (§4.5): AuthFilter → AclFilter → SignatureVerifier, etc.
 *
 * <p>The first {@link IngressFilter} that denies wins (fail-closed); only a request that every
 * filter allows proceeds to the IngressPipeline. Filters are consulted in registration order, so
 * authenticate before authorize.</p>
 */
public class FilterChain {

    private final List<IngressFilter> filters;

    public FilterChain(List<IngressFilter> filters) {
        this.filters = Collections.unmodifiableList(new ArrayList<>(filters));
    }

    public FilterChain(IngressFilter... filters) {
        this(Arrays.asList(filters));
    }

    /**
     * Run every filter; return the first denying verdict, or {@link FilterVerdict#allow()} if all
     * pass.
     *
     * @deprecated since #5299 — call {@link #check(EventMeshFrame, FilterContext)} instead. Kept
     *     as a bridge for the TCP path until sub-PR C migrates.
     */
    @Deprecated
    public FilterVerdict check(io.cloudevents.CloudEvent event, FilterContext ctx) {
        return check(EventMeshFrame.fromCloudEvent(event), ctx);
    }

    /**
     * Run every filter on the runtime's internal frame; return the first denying verdict, or
     * {@link FilterVerdict#allow()} if all pass. This is the primary ingress path since #5299.
     */
    public FilterVerdict check(EventMeshFrame frame, FilterContext ctx) {
        for (IngressFilter filter : filters) {
            FilterVerdict verdict = filter.check(frame, ctx);
            if (!verdict.isAllowed()) {
                return verdict;
            }
        }
        return FilterVerdict.allow();
    }

    public int size() {
        return filters.size();
    }
}
