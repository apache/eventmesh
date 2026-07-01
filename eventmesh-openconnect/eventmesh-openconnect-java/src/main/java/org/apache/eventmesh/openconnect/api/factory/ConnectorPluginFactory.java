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

package org.apache.eventmesh.openconnect.api.factory;

import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.SinkConnector;
import org.apache.eventmesh.openconnect.api.connector.SourceConnector;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;


/**
 * The factory to get connector {@link SourceConnector} and {@link SinkConnector}
 */
public class ConnectorPluginFactory {

    /**
     * Get ConnectorCreateService instance by plugin name
     *
     * @param connectorPluginName plugin name
     * @return ConnectorCreateService instance
     */
    public static ConnectorCreateService<?> createConnector(String connectorPluginName) {
        return EventMeshExtensionFactory.getExtension(ConnectorCreateService.class, connectorPluginName);
    }

}
