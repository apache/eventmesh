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

package org.apache.eventmesh.runtime.security.gate;

import org.apache.eventmesh.runtime.security.FilterContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Unified identity + policy context flowing through every ingress path (issue #5304):
 * publish, subscribe, ACK, Connector and A2A. Built once at the protocol edge (HTTP handler,
 * TCP bridge, A2A gateway handler, connector scheduler) and passed to
 * {@link SecurityGate#check}, so authentication, authorization (ACL), quota enforcement and
 * audit emission all read the <b>same</b> context instead of each protocol re-deriving its own.
 *
 * <p>Replaces ad-hoc {@link FilterContext} construction scattered across handlers. The legacy
 * {@link FilterContext} is retained (filters still consume it); {@link #toFilterContext()}
 * bridges losslessly.</p>
 *
 * <h2>Field semantics</h2>
 * <ul>
 *   <li>{@code tenantId} — the multi-tenant namespace the caller belongs to. Topics are
 *       implicitly namespaced under it for ACL purposes ({@code tenantA.*} rules).</li>
 *   <li>{@code principal} — the authenticated identity used by ACL rules. Falls back to
 *       tenantId, then clientId (mirrors the legacy AclFilter choice).</li>
 *   <li>{@code roles} / {@code scopes} — optional RBAC / OAuth-style grants from the auth
 *       layer; future filters may match on them, core only carries them.</li>
 *   <li>{@code quotaKey} — the accounting identity for {@link QuotaManager}. Defaults to
 *       {@code tenantId} when present, else {@code clientId}, else {@code "anonymous"} so
 *       unauthenticated traffic shares one bucket instead of bypassing quota.</li>
 *   <li>{@code operation} — what the caller is trying to do; drives ACL action matching
 *       (the legacy path passed {@code null} and let any action rule match).</li>
 * </ul>
 *
 * <p>Immutable; build via {@link Builder}. None of the getters return {@code null} maps.</p>
 */
public final class RequestContext {

    /** What the caller is attempting; maps onto {@code AclRule.Action} for authorization. */
    public enum Operation {
        PUBLISH,
        SUBSCRIBE,
        ACK,
        /** Connector source/sink operations (ConnectorScheduler paths). */
        CONNECTOR,
        /** A2A task submit / get / cancel / stream. */
        A2A,
        /** Admin endpoints (agent register/unregister, session management). */
        ADMIN
    }

    private final Operation operation;
    private final String topic;
    private final String clientId;
    private final String tenantId;
    private final String principal;
    private final Set<String> roles;
    private final Set<String> scopes;
    private final String credential;
    private final String remoteAddress;
    /** Where the request entered: "http", "tcp", "a2a", "connector", "admin". */
    private final String source;
    private final String quotaKey;
    /** Opaque trace propagation headers (traceparent, baggage, ...). */
    private final Map<String, String> traceContext;

    private RequestContext(Builder b) {
        this.operation = Objects.requireNonNull(b.operation, "operation");
        this.topic = b.topic;
        this.clientId = b.clientId;
        this.tenantId = b.tenantId;
        this.principal = b.principal != null ? b.principal
            : (b.tenantId != null ? b.tenantId : b.clientId);
        this.roles = b.roles == null ? Collections.emptySet() : Collections.unmodifiableSet(b.roles);
        this.scopes = b.scopes == null ? Collections.emptySet() : Collections.unmodifiableSet(b.scopes);
        this.credential = b.credential;
        this.remoteAddress = b.remoteAddress;
        this.source = b.source == null ? "unknown" : b.source;
        this.quotaKey = b.quotaKey != null ? b.quotaKey
            : (b.tenantId != null ? b.tenantId : (b.clientId != null ? b.clientId : "anonymous"));
        this.traceContext = b.traceContext == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(b.traceContext));
    }

    public static Builder builder(Operation operation) {
        return new Builder(operation);
    }

    public Operation getOperation() {
        return operation;
    }

    public String getTopic() {
        return topic;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getPrincipal() {
        return principal;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public String getCredential() {
        return credential;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getSource() {
        return source;
    }

    public String getQuotaKey() {
        return quotaKey;
    }

    public Map<String, String> getTraceContext() {
        return traceContext;
    }

    /**
     * Bridge to the legacy {@link FilterContext} consumed by existing
     * {@link org.apache.eventmesh.runtime.security.IngressFilter}s. Lossless for the fields the
     * current filters read (topic, clientId, tenant, credential, remoteAddress).
     */
    public FilterContext toFilterContext() {
        return new FilterContext(topic, clientId, tenantId, credential, remoteAddress);
    }

    /** Maps this request's {@link Operation} onto the ACL rule action, or {@code null} for ADMIN. */
    org.apache.eventmesh.runtime.security.AclRule.Action aclAction() {
        switch (operation) {
            case PUBLISH:
                return org.apache.eventmesh.runtime.security.AclRule.Action.PUBLISH;
            case SUBSCRIBE:
            case ACK:
                return org.apache.eventmesh.runtime.security.AclRule.Action.SUBSCRIBE;
            case A2A:
                return org.apache.eventmesh.runtime.security.AclRule.Action.REQUEST;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        return "RequestContext{op=" + operation + ", topic=" + topic + ", principal=" + principal
            + ", quotaKey=" + quotaKey + ", source=" + source + "}";
    }

    /** Fluent builder. Only {@code operation} is required. */
    public static final class Builder {

        private final Operation operation;
        private String topic;
        private String clientId;
        private String tenantId;
        private String principal;
        private Set<String> roles;
        private Set<String> scopes;
        private String credential;
        private String remoteAddress;
        private String source;
        private String quotaKey;
        private Map<String, String> traceContext;

        private Builder(Operation operation) {
            this.operation = operation;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder principal(String principal) {
            this.principal = principal;
            return this;
        }

        public Builder roles(Set<String> roles) {
            this.roles = roles;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder credential(String credential) {
            this.credential = credential;
            return this;
        }

        public Builder remoteAddress(String remoteAddress) {
            this.remoteAddress = remoteAddress;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder quotaKey(String quotaKey) {
            this.quotaKey = quotaKey;
            return this;
        }

        public Builder traceContext(Map<String, String> traceContext) {
            this.traceContext = traceContext;
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }
}
