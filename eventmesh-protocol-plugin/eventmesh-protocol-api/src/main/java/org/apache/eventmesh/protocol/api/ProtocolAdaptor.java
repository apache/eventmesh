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

package org.apache.eventmesh.protocol.api;

import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.v1.CloudEventV1;

/**
 * Protocol transformer SPI interface, all protocol plugin should implementation.
 *
 * <p>All protocol stored in EventMesh is {@link CloudEvent}.
 *
 * @since 1.3.0
 */
@EventMeshSPI(isSingleton = true, eventMeshExtensionType = EventMeshExtensionType.PROTOCOL)
public interface ProtocolAdaptor {

    /**
     * transform protocol to {@link CloudEvent}.
     *
     * @param protocol input protocol
     * @return cloud event
     */
    CloudEventV1 toCloudEventV1(Package protocol) throws ProtocolHandleException;


    /**
     * Transform {@link CloudEvent} to target protocol.
     *
     * @param cloudEvent clout event
     * @return target protocol
     */
    Package fromCloudEventV1(CloudEventV1 cloudEvent) throws ProtocolHandleException;

    /**
     * Get protocol type.
     *
     * @return protocol type, protocol type should not be null
     */
    String getProtocolType();

}
