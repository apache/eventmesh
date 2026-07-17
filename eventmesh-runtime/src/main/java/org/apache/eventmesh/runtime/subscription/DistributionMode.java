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

package org.apache.eventmesh.runtime.subscription;

/**
 * Distribution modes for EventMesh's self-managed subscription model.
 *
 * <p>EventMesh owns the distribution logic (the MQ exposes no consumer-group semantics); see
 * {@code docs/eventmesh-uni-architecture-redesign.md} §4.2.</p>
 */
public enum DistributionMode {

    /**
     * Each message is delivered to exactly one subscriber, picked round-robin.
     */
    LOAD_BALANCE,

    /**
     * Each message is delivered to every active subscriber.
     */
    BROADCAST,

    /**
     * Each message is delivered to the subscribers whose {@link CloudEventFilter} matches it.
     */
    MULTICAST,

    /**
     * Like {@link #LOAD_BALANCE}, but a subscriber is chosen by hashing a partition key so that
     * messages with the same key always go to the same subscriber (ordering). Reserved for
     * Phase 5.5 (§13.3.3); behaves like {@link #LOAD_BALANCE} until the partition-key wiring lands.
     */
    LOAD_BALANCE_STICKY
}
