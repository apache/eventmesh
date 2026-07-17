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
 * Local (same-instance) delivery sink used by {@link ClusterCoordinator} when a target subscriber
 * lives on this instance. In production this hands the event to the local push buffer / reliability
 * layer; tests record deliveries to assert cross-instance routing.
 */
@FunctionalInterface
public interface LocalDeliverer {

    /**
     * @param topic the EventMesh topic the event was published to
     * @return true if the local delivery was accepted (buffered / handed to a subscriber)
     */
    boolean deliver(String topic, String clientId, CloudEvent event);
}
