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

package com.webank.emesher.boot;

import com.webank.emesher.common.ServiceState;
import com.webank.emesher.configuration.AccessConfiguration;
import com.webank.emesher.configuration.ProxyConfiguration;
import com.webank.emesher.constants.ProxyConstants;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyServer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());
    public ProxyHTTPServer proxyHTTPServer;

    private ProxyTCPServer proxyTCPServer;

    private ProxyConfiguration proxyConfiguration;

    private AccessConfiguration accessConfiguration;

    private ServiceState serviceState;

    public ProxyServer(ProxyConfiguration proxyConfiguration,
                       AccessConfiguration accessConfiguration) {
        this.proxyConfiguration = proxyConfiguration;
        this.accessConfiguration = accessConfiguration;
    }

    public void init() throws Exception {
        proxyHTTPServer = new ProxyHTTPServer(this, proxyConfiguration);
        proxyHTTPServer.init();
        proxyTCPServer = new ProxyTCPServer(this, accessConfiguration);
        if (accessConfiguration.proxyTcpServerEnabled) {
            proxyTCPServer.init();
        }

        String useRocket = System.getProperty(ProxyConstants.USE_ROCKET_PROPERTIES, System.getenv(ProxyConstants.USE_ROCKET_ENV));
        logger.info("useRocket : {}", useRocket);
        logger.info("load custom {} class for proxy", ConsumeMessageConcurrentlyService.class.getCanonicalName());

        serviceState = ServiceState.INITED;
        logger.info("server state:{}",serviceState);
    }

    public void start() throws Exception {
        proxyHTTPServer.start();
        if (accessConfiguration.proxyTcpServerEnabled) {
            proxyTCPServer.start();
        }
        serviceState = ServiceState.RUNNING;
        logger.info("server state:{}",serviceState);
    }

    public void shutdown() throws Exception {
        serviceState = ServiceState.STOPING;
        logger.info("server state:{}",serviceState);
        proxyHTTPServer.shutdown();
        if (accessConfiguration.proxyTcpServerEnabled) {
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
