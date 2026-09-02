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

import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link AuditSink}: one structured INFO line per authorized operation, one WARN line
 * per denial / quota rejection — cheap enough for every deployment, greppable for ops.
 *
 * <p>Deployments needing SIEM streaming implement {@link AuditSink} and inject it into
 * {@link SecurityGate} instead.</p>
 */
@Slf4j
public final class LoggingAuditSink implements AuditSink {

    public static final LoggingAuditSink INSTANCE = new LoggingAuditSink();

    private LoggingAuditSink() {
    }

    @Override
    public void emit(RequestContext context, Outcome outcome, String detail) {
        switch (outcome) {
            case ALLOWED:
                log.info("AUDIT allowed op={} principal={} topic={} quotaKey={} source={} remote={}",
                    context.getOperation(), context.getPrincipal(), context.getTopic(),
                    context.getQuotaKey(), context.getSource(), context.getRemoteAddress());
                break;
            case DENIED:
                log.warn("AUDIT denied op={} principal={} topic={} source={} reason={}",
                    context.getOperation(), context.getPrincipal(), context.getTopic(),
                    context.getSource(), detail);
                break;
            case QUOTA_EXCEEDED:
                log.warn("AUDIT quota op={} quotaKey={} resource={} source={}",
                    context.getOperation(), context.getQuotaKey(), detail, context.getSource());
                break;
            default:
                break;
        }
    }
}
