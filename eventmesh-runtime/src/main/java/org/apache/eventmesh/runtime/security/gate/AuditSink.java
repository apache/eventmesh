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

/**
 * Audit sink SPI (issue #5304): every authorized operation passing {@link SecurityGate} is
 * reported here, giving deployments a single hook to stream audit events to a log, SIEM or
 * Meta-backed store.
 *
 * <p>Implementations must be non-throwing and fast — the audit path must not fail the request.
 * The default {@link #disabled()} drops events.</p>
 */
public interface AuditSink {

    /** Outcome of the operation being audited. */
    enum Outcome {
        ALLOWED,
        DENIED,
        QUOTA_EXCEEDED
    }

    /**
     * Record one authorization decision. Called after {@link SecurityGate#check} decided;
     * exceptions are swallowed by the caller, so implementations should guard their own I/O.
     *
     * @param context the request context that was checked
     * @param outcome the decision
     * @param detail  optional reason (deny reason, quota resource, ...)
     */
    void emit(RequestContext context, Outcome outcome, String detail);

    /** A sink that drops everything (auditing off). */
    static AuditSink disabled() {
        return DisabledAuditSink.INSTANCE;
    }
}
