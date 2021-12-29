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

package org.apache.eventmesh.runtime.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.eventmesh.api.connector.ConnectorResourceService;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

public class ConnectorResource {

    private static final Logger logger = LoggerFactory.getLogger(ConnectorResource.class);
    private static ConnectorResourceService connectorResourceService;

    public void init(String connectorResourcePluginType) throws Exception {
        connectorResourceService = EventMeshExtensionFactory.getExtension(ConnectorResourceService.class, connectorResourcePluginType);
        if (connectorResourceService == null) {
            logger.error("can't load the connectorResourceService plugin, please check.");
            throw new RuntimeException("doesn't load the connectorResourceService plugin, please check.");
        }
        connectorResourceService.init();
    }

    public void release() throws Exception {
        connectorResourceService.release();
    }
}
