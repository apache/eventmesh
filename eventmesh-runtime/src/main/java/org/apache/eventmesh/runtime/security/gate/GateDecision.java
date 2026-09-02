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

import org.apache.eventmesh.runtime.security.gate.QuotaManager.Resource;

/**
 * Result of {@link SecurityGate#check}. Mirrors {@link org.apache.eventmesh.runtime.security.FilterVerdict}
 * statuses and adds quota semantics (HTTP 429).
 */
public final class GateDecision {

    /** HTTP 429 Too Many Requests. */
    public static final int STATUS_QUOTA_EXCEEDED = 429;

    private final boolean allowed;
    private final int rejectStatus;
    private final String reason;
    private final Resource quotaResource;

    private GateDecision(boolean allowed, int rejectStatus, String reason, Resource quotaResource) {
        this.allowed = allowed;
        this.rejectStatus = rejectStatus;
        this.reason = reason;
        this.quotaResource = quotaResource;
    }

    static GateDecision allowed() {
        return new GateDecision(true, 0, null, null);
    }

    static GateDecision denied(int httpStatus, String reason) {
        return new GateDecision(false, httpStatus, reason, null);
    }

    static GateDecision quotaExceeded(Resource resource) {
        return new GateDecision(false, STATUS_QUOTA_EXCEEDED,
            "quota exceeded: " + resource.name(), resource);
    }

    public boolean isAllowed() {
        return allowed;
    }

    /** HTTP-equivalent status when denied: 401, 403 or 429. */
    public int getRejectStatus() {
        return rejectStatus;
    }

    public String getReason() {
        return reason;
    }

    /** The exhausted resource when {@link #isQuotaExceeded()}, else {@code null}. */
    public Resource getQuotaResource() {
        return quotaResource;
    }

    public boolean isQuotaExceeded() {
        return rejectStatus == STATUS_QUOTA_EXCEEDED;
    }
}
