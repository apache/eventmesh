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

package org.apache.eventmesh.runtime.boot;

import org.apache.eventmesh.runtime.common.ServiceState;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.configuration.EventMeshTCPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.store.h2.schema.H2SchemaAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public EventMeshHTTPServer eventMeshHTTPServer;

    private EventMeshTCPServer eventMeshTCPServer;
    
    private H2SchemaAdapter h2SchemaAdapter;

    private EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    private EventMeshTCPConfiguration eventMeshTCPConfiguration;

    private ServiceState serviceState;

    public EventMeshServer(EventMeshHTTPConfiguration eventMeshHttpConfiguration,
                           EventMeshTCPConfiguration eventMeshTCPConfiguration) {
        this.eventMeshHttpConfiguration = eventMeshHttpConfiguration;
        this.eventMeshTCPConfiguration = eventMeshTCPConfiguration;
    }

    public void init() throws Exception {
        eventMeshHTTPServer = new EventMeshHTTPServer(this, eventMeshHttpConfiguration);
        eventMeshHTTPServer.init();
        eventMeshTCPServer = new EventMeshTCPServer(this, eventMeshTCPConfiguration);
        if (eventMeshTCPConfiguration != null && eventMeshTCPConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.init();
        }
        
        h2SchemaAdapter = new H2SchemaAdapter();
        h2SchemaAdapter.init();
        String eventstore = System.getProperty(EventMeshConstants.EVENT_STORE_PROPERTIES, System.getenv(EventMeshConstants.EVENT_STORE_ENV));
        logger.info("eventstore : {}", eventstore);

        serviceState = ServiceState.INITED;
        logger.info("server state:{}", serviceState);
    }

    public void start() throws Exception {
        eventMeshHTTPServer.start();
        if (eventMeshTCPConfiguration != null && eventMeshTCPConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.start();
        }
        if (h2SchemaAdapter.isAdapterEnabled()) {
        	h2SchemaAdapter.start();            
        }
        serviceState = ServiceState.RUNNING;
        logger.info("server state:{}", serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        logger.info("server state:{}", serviceState);
        eventMeshHTTPServer.shutdown();
        if (eventMeshTCPConfiguration != null && eventMeshTCPConfiguration.eventMeshTcpServerEnabled) {
            eventMeshTCPServer.shutdown();
        }
        if (h2SchemaAdapter.isAdapterEnabled()) {
        	h2SchemaAdapter.shutdown();
        }
        serviceState = ServiceState.STOPED;
        logger.info("server state:{}", serviceState);
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public EventMeshTCPServer getEventMeshTCPServer() {
        return eventMeshTCPServer;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }
}
