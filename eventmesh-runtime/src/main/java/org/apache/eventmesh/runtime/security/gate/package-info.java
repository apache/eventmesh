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

/**
 * Unified multi-tenant / security / quota entrypoint (issue #5304).
 *
 * <p>One {@link gate.RequestContext} — identity (tenant, principal, roles, scopes), intent
 * (operation, topic), transport metadata (source, remote address, trace context) and quota
 * identity — flows through every ingress path (publish / subscribe / ACK / Connector / A2A),
 * and {@link gate.SecurityGate} enforces authentication + ACL (via the existing filter chain),
 * quota ({@link gate.QuotaManager}) and audit ({@link gate.AuditSink}) in one place.</p>
 */
package org.apache.eventmesh.runtime.security.gate;
