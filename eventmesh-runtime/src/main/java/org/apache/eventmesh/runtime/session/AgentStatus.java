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

package org.apache.eventmesh.runtime.session;

/**
 * Lifecycle status of a registered agent (stored in {@link AgentRecord}). The matchmaker only routes
 * to {@link #READY} agents whose heartbeat is fresh and capacity not exceeded.
 *
 * @see SessionRegistry#readyAgents()
 */
public enum AgentStatus {
    /** Registered but not yet subscribed to its channel (pre ready-before-route, §5.2). */
    PENDING,
    /** Subscribed and routable. */
    READY
}
