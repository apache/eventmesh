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
 * UniIngressService: routes CloudEvents to the right delivery/push path.
 *
 * <p>Depends on: Depends on common.protocol, common.wire, runtime.delivery, runtime.push, runtime.subscription.
 *
 * <p>Policy: Public facade -- boot wires ingress into UniRuntime, but no engine sub-package may bypass it.
 *
 * <p>Broker-ACK barrier (RocketMQ 5.x POP, PR #5316 by zhang-arvin,
 * fixes #5295): when the ingress frame carries a POP check key (the
 * {@code empopck} attribute), deliveries to all matched subscriptions
 * share an {@link java.util.concurrent.atomic.AtomicInteger} counter
 * initialized to the target count; the broker is ACKed (via
 * {@code storage.ackPulledMessage}) only when the last required delivery
 * ACKs. This restores at-least-once semantics across LOAD_BALANCE,
 * BROADCAST, and MULTICAST distribution modes. Frames without
 * {@code empopck} bypass the barrier (no broker ACK to defer).
 *
 * <p>Marked {@link org.apache.eventmesh.common.Internal @Internal} as a
 * whole package; public types must carry {@link org.apache.eventmesh.common.Public @Public}.
 */
@org.apache.eventmesh.common.Internal
package org.apache.eventmesh.runtime.ingress;
