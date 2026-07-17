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

package org.apache.eventmesh.runtime.cluster;

import io.cloudevents.CloudEvent;

/**
 * Delivers a CloudEvent to a subscriber that lives on a <em>different</em> instance (§13.2.5).
 *
 * <p>The production implementation does {@code HTTP POST /internal/forward} to the target instance's
 * address (looked up via Meta). Tests substitute an in-process router that hands the event to the
 * target instance's local delivery path.</p>
 */
@FunctionalInterface
public interface Forwarder {

    /**
     * Forward {@code event} to {@code clientId} on {@code targetInstance}.
     *
     * @param topic the event's topic (needed so the target knows which subscription to deliver to)
     * @return true if the target accepted the delivery (used for retry decisions)
     */
    boolean forward(String targetInstance, String clientId, String topic, CloudEvent event);
}
