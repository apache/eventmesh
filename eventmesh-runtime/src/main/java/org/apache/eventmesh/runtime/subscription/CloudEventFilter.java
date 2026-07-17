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

import java.util.Objects;

import io.cloudevents.CloudEvent;

/**
 * Predicate a subscriber registers to decide, in {@link DistributionMode#MULTICAST}, whether a
 * given CloudEvent should be delivered to it. MQ tag filtering is never used (§3.2); EventMesh
 * filters on CloudEvents attributes itself.
 */
@FunctionalInterface
public interface CloudEventFilter {

    boolean match(CloudEvent event);

    /**
     * A filter that accepts every event.
     */
    CloudEventFilter ACCEPT_ALL = event -> true;

    /**
     * Matches on the CloudEvents standard {@code type} attribute.
     */
    static CloudEventFilter byType(String type) {
        Objects.requireNonNull(type, "type");
        return event -> type.equals(event.getType());
    }

    /**
     * Matches on the CloudEvents standard {@code subject} attribute.
     */
    static CloudEventFilter bySubject(String subject) {
        Objects.requireNonNull(subject, "subject");
        return event -> subject.equals(event.getSubject());
    }
}
