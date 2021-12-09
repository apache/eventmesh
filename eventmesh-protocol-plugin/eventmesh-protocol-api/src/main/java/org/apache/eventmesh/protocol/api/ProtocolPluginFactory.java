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

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.experimental.UtilityClass;

/**
 * A factory to get Protocol plugin instance.
 *
 * @since 1.3.0
 */
@UtilityClass
public class ProtocolPluginFactory {

    private static final Map<String, ProtocolAdaptor<ProtocolTransportObject>> PROTOCOL_ADAPTOR_MAP =
        new ConcurrentHashMap<>(16);

    /**
     * Get protocol adaptor by name.
     *
     * @param protocolType protocol type
     * @return protocol adaptor
     * @throws IllegalArgumentException if protocol not found
     */
    @SuppressWarnings("unchecked")
    public static ProtocolAdaptor<ProtocolTransportObject> getProtocolAdaptor(String protocolType) {
        ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = PROTOCOL_ADAPTOR_MAP.computeIfAbsent(
            protocolType,
            (type) -> EventMeshExtensionFactory.getExtension(ProtocolAdaptor.class, type)
        );
        if (protocolAdaptor == null) {
            throw new IllegalArgumentException(
                String.format("Cannot find the Protocol adaptor: %s", protocolType)
            );
        }
        return protocolAdaptor;
    }
}
