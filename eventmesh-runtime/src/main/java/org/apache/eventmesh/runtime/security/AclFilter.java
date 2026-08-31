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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.eventmesh.common.wire.EventMeshFrame;

/**
 * Topic-level authorization (§13.4.2). Holds an {@link AclRule} set sorted by priority (descending,
 * DENY wins ties) and matches each request against it: the first matching rule's effect applies,
 * and no match means default-deny (whitelist model).
 *
 * <p>Rules are mutable at runtime via {@link #setRules} so a Meta watcher (§13.4.2 "rules via Meta
 * watch") can hot-swap them with zero RTT on the hot path — matching reads the volatile list with no
 * Meta lookup. Principal is the tenant (or clientId when no tenant); resource is the topic.</p>
 */
public class AclFilter implements IngressFilter {

    private volatile List<AclRule> rules = Collections.emptyList();

    /** Empty ACL — denies everything (default-deny). Add rules via {@link #setRules}. */
    public AclFilter() {
    }

    public AclFilter(List<AclRule> initialRules) {
        setRules(initialRules);
    }

    /** Hot-swap the rule set (called by a Meta watcher on rule changes). Thread-safe. */
    public synchronized void setRules(List<AclRule> newRules) {
        List<AclRule> sorted = new ArrayList<>(newRules == null ? Collections.emptyList() : newRules);
        // Priority descending; on tie DENY first so a same-priority deny beats allow.
        sorted.sort((a, b) -> {
            int c = Integer.compare(b.getPriority(), a.getPriority());
            if (c != 0) {
                return c;
            }
            return a.getEffect() == AclRule.Effect.DENY ? -1 : (b.getEffect() == AclRule.Effect.DENY ? 1 : 0);
        });
        this.rules = sorted;
    }

    /**
     * Tenant / clientId come from {@code ctx} (set by the HTTP handler before the filter chain).
     * We no longer read them from the event because that path was CloudEvent-specific; sub-PR
     * B keeps the contract simple by reading the principal from the context and only using the
     * frame to confirm an event-shaped payload arrived.
     */
    @Override
    public FilterVerdict check(EventMeshFrame frame, FilterContext ctx) {
        String principal = ctx.getTenant() != null ? ctx.getTenant() : ctx.getClientId();
        String resource = ctx.getTopic();
        if (principal == null || resource == null) {
            return FilterVerdict.deny(FilterVerdict.STATUS_FORBIDDEN, "no principal/resource for ACL");
        }
        if (frame != null && !frame.isEvent()) {
            return FilterVerdict.deny(FilterVerdict.STATUS_FORBIDDEN,
                "ACL applies to EVENT frames only (got msgType=" + frame.msgType() + ")");
        }
        // action not yet carried in FilterContext — pass null so rule action doesn't restrict (any matches).
        for (AclRule rule : rules) {
            if (rule.matches(principal, resource, null)) {
                return rule.getEffect() == AclRule.Effect.ALLOW
                    ? FilterVerdict.allow()
                    : FilterVerdict.deny(FilterVerdict.STATUS_FORBIDDEN,
                        "denied by ACL rule " + rule);
            }
        }
        return FilterVerdict.deny(FilterVerdict.STATUS_FORBIDDEN,
            "principal " + principal + " not permitted on " + resource + " (default deny)");
    }
}
