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

package com.webank.eventmesh.runtime.boot;

import com.webank.eventmesh.runtime.common.ServiceState;
import com.webank.eventmesh.runtime.configuration.EventMeshConfiguration;
import com.webank.eventmesh.runtime.configuration.ProxyConfiguration;
import com.webank.eventmesh.runtime.constants.ProxyConstants;
//import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public ProxyHTTPServer proxyHTTPServer;

    private ProxyTCPServer proxyTCPServer;

    private ProxyConfiguration proxyConfiguration;

    private EventMeshConfiguration eventMeshConfiguration;

    private ServiceState serviceState;

    public ProxyServer(ProxyConfiguration proxyConfiguration,
                       EventMeshConfiguration eventMeshConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
        this.eventMeshConfiguration = eventMeshConfiguration;
    }

    public void init() throws Exception {
        proxyHTTPServer = new ProxyHTTPServer(this, proxyConfiguration);
        proxyHTTPServer.init();
        proxyTCPServer = new ProxyTCPServer(this, eventMeshConfiguration);
        if (eventMeshConfiguration.proxyTcpServerEnabled) {
            proxyTCPServer.init();
        }

        String eventstore = System.getProperty(ProxyConstants.EVENT_STORE_PROPERTIES, System.getenv(ProxyConstants.EVENT_STORE_ENV));
        logger.info("eventstore : {}", eventstore);
//        logger.info("load custom {} class for proxy", ConsumeMessageConcurrentlyService.class.getCanonicalName());

        serviceState = ServiceState.INITED;
        logger.info("server state:{}",serviceState);
    }

    public void start() throws Exception {
        proxyHTTPServer.start();
        if (eventMeshConfiguration.proxyTcpServerEnabled) {
            proxyTCPServer.start();
        }
        serviceState = ServiceState.RUNNING;
        logger.info("server state:{}",serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        logger.info("server state:{}",serviceState);
        proxyHTTPServer.shutdown();
        if (eventMeshConfiguration.proxyTcpServerEnabled) {
            proxyTCPServer.shutdown();
        }
        serviceState = ServiceState.STOPED;
        logger.info("server state:{}",serviceState);
    }

    public ProxyHTTPServer getProxyHTTPServer() {
        return proxyHTTPServer;
    }

    public ProxyTCPServer getProxyTCPServer() {
        return proxyTCPServer;
    }

    public ServiceState getServiceState() { return serviceState; }
}
