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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.eventmesh.common.utils.InnerCollectionUtils;
import org.apache.eventmesh.protocol.api.exception.EventMeshProtocolException;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

import java.util.List;

public enum EventMeshProtocolPluginFactory {
    ;

    public static List<EventMeshProtocolServer> getEventMeshProtocolServers(List<String> protocolPluginNames) {
        if (CollectionUtils.isEmpty(protocolPluginNames)) {
            throw new EventMeshProtocolException(String.format("protocolPluginNames: %s can not be empty", protocolPluginNames));
        }
        return InnerCollectionUtils.collectionToArrayList(protocolPluginNames, EventMeshProtocolPluginFactory::getEventMeshProtocolServer);
    }

    public static EventMeshProtocolServer getEventMeshProtocolServer(String protocolPluginName) {
        EventMeshProtocolServer eventMeshProtocolServer = EventMeshExtensionFactory.getExtension(EventMeshProtocolServer.class, protocolPluginName);
        if (eventMeshProtocolServer == null) {
            throw new EventMeshProtocolException(String.format("cannot find the protocol plugin: %s", protocolPluginName));
        }
        return eventMeshProtocolServer;
    }
}
