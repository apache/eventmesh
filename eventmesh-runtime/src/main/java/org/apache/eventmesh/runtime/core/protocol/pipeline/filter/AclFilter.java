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

package org.apache.eventmesh.runtime.core.protocol.pipeline.filter;

import org.apache.eventmesh.common.protocol.pipeline.PipelineContext;
import org.apache.eventmesh.common.protocol.pipeline.PipelineResult;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.core.protocol.pipeline.PipelineFilter;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Access Control List filter — enforces IP/client/topic-level access control.
 *
 * <p>This is the 5th filter in the chain and CANNOT be bypassed.
 * Delegates to existing {@link Acl} infrastructure for per-protocol checks.
 */
@Slf4j
public class AclFilter implements PipelineFilter {

    public static final String NAME = "AclFilter";

    private final Acl acl;
    private final Set<String> ipAllowlist;
    private final Set<String> ipDenylist;

    public AclFilter(Acl acl) {
        this.acl = acl;
        this.ipAllowlist = ConcurrentHashMap.newKeySet();
        this.ipDenylist = ConcurrentHashMap.newKeySet();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public boolean isBypassable() {
        return false; // Security-critical — never bypassable
    }

    @Override
    public PipelineResult filter(CloudEvent event, PipelineContext ctx) {
        String topic = event.getSubject();
        String clientIp = getClientIp(ctx);

        // IP denylist takes precedence
        if (clientIp != null && ipDenylist.contains(clientIp)) {
            log.warn("[AclFilter] Request from denied IP {} for event {}", clientIp, event.getId());
            return PipelineResult.drop(event);
        }

        // If IP allowlist is configured, only allowed IPs pass
        if (!ipAllowlist.isEmpty()) {
            if (clientIp == null || !ipAllowlist.contains(clientIp)) {
                log.warn("[AclFilter] Request from IP {} not in allowlist for event {}", clientIp, event.getId());
                return PipelineResult.drop(event);
            }
        }

        // Delegate to existing ACL infrastructure (if available)
        if (acl != null && topic != null) {
            try {
                acl.doAclCheckInHttpSend(
                    clientIp != null ? clientIp : "unknown",
                    getClientUser(ctx),
                    "",
                    getClientSubsystem(ctx),
                    topic,
                    0
                );
            } catch (Exception e) {
                log.warn("[AclFilter] ACL check failed for event {}: {}", event.getId(), e.getMessage());
                return PipelineResult.drop(event);
            }
        }

        return PipelineResult.cont(event);
    }

    // ---- helpers ----

    private static String getClientIp(PipelineContext ctx) {
        Object ip = ctx.getAttribute("clientIp");
        return ip != null ? ip.toString() : null;
    }

    private static String getClientUser(PipelineContext ctx) {
        Object user = ctx.getAttribute("clientUser");
        return user != null ? user.toString() : "";
    }

    private static String getClientSubsystem(PipelineContext ctx) {
        Object sub = ctx.getAttribute("clientSubsystem");
        return sub != null ? sub.toString() : "";
    }

    // ---- IP list management ----

    public void addAllowedIp(String ip) {
        ipAllowlist.add(ip);
    }

    public void removeAllowedIp(String ip) {
        ipAllowlist.remove(ip);
    }

    public void addDeniedIp(String ip) {
        ipDenylist.add(ip);
    }

    public void removeDeniedIp(String ip) {
        ipDenylist.remove(ip);
    }

    public Set<String> getIpAllowlist() {
        return Collections.unmodifiableSet(ipAllowlist);
    }

    public Set<String> getIpDenylist() {
        return Collections.unmodifiableSet(ipDenylist);
    }
}
