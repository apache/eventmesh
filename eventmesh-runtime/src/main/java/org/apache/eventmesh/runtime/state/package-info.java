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

package org.apache.eventmesh.runtime.state;

/**
 * Root package for the unified state control-plane (issue #5301).
 *
 * <p>Six stores live in this package (or its siblings in the existing module layout):</p>
 *
 * <ul>
 *   <li>{@link org.apache.eventmesh.runtime.offset.OffsetStore} — per-subscriber distribution offset (RocksDB local, Meta async flush)</li>
 *   <li>{@link org.apache.eventmesh.runtime.state.SubscriptionStore} — cluster-shared subscription registry (Meta prefix-watch)</li>
 *   <li>{@link org.apache.eventmesh.runtime.state.SessionStore} — cluster-shared agent/session/binding registry (Meta prefix-watch)</li>
 *   <li>{@link org.apache.eventmesh.runtime.state.DeadLetterStore} — durable ledger of dead-lettered deliveries (Meta CAS)</li>
 *   <li>{@link org.apache.eventmesh.runtime.state.TaskStore} — A2A task state, persisted via Meta (issue #5301 Sub-PR C)</li>
 *   <li>{@link org.apache.eventmesh.runtime.state.DeliveryStateStore} — in-flight delivery state,
 *   persistent via RocksDB (issue #5301 Sub-PR B ✓)</li>
 * </ul>
 *
 * <p>Sub-PR A (this package's initial commit) introduces the 4 interfaces that did not exist as
 * public contracts ({@code SubscriptionStore}, {@code SessionStore}, {@code DeadLetterStore},
 * {@code TaskStore}). The other two ({@code OffsetStore}, {@code DeliveryStateStore}) are added
 * by their respective sub-PRs.</p>
 */