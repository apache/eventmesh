/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmeth.server.http;

import com.google.common.eventbus.EventBus;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.server.api.exception.EventMeshServerException;
import org.apache.eventmesh.server.api.model.ServiceState;
import org.apache.eventmeth.server.http.acl.Acl;
import org.apache.eventmeth.server.http.config.EventMeshHTTPConfiguration;
import org.apache.eventmeth.server.http.consumer.ConsumerGroupConf;
import org.apache.eventmeth.server.http.consumer.ConsumerManager;
import org.apache.eventmeth.server.http.model.AbstractHTTPPushRequest;
import org.apache.eventmeth.server.http.model.Client;
import org.apache.eventmeth.server.http.producer.ProducerManager;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class EventMeshHTTPServer extends AbstractEventMeshHTTPServer {

    public ServiceState serviceState;

    private EventBus eventBus = new EventBus();

    private final ConcurrentHashMap<String /**group*/, ConsumerGroupConf> localConsumerGroupMapping = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String /**group@topic*/, List<Client>> localClientInfoMapping = new ConcurrentHashMap<>();

    private ConsumerManager consumerManager;

    private ProducerManager producerManager;

    private Acl acl;

    public EventMeshHTTPServer() {
        super(EventMeshHTTPConfiguration.httpServerPort, EventMeshHTTPConfiguration.eventMeshServerUseTls);
    }

    public void init() throws EventMeshServerException {
        logger.info("==================EventMeshHTTPServer Initialing==================");
        super.init();
        try {
            if (CommonConfiguration.eventMeshServerSecurityEnable) {
                acl = new Acl();
                acl.init(CommonConfiguration.eventMeshSecurityPluginType);
            }

            consumerManager = new ConsumerManager(this);
            consumerManager.init();

            producerManager = new ProducerManager(this);
            producerManager.init();

            logger.info("--------------------------EventMeshHTTPServer inited");
        } catch (Exception ex) {
            throw new EventMeshServerException(ex);
        }

    }

    @Override
    public void start() throws EventMeshServerException {
        super.start();
        try {
            if (CommonConfiguration.eventMeshServerSecurityEnable) {
                acl.start();
            }
            consumerManager.start();
            producerManager.start();
            logger.info("--------------------------EventMeshHTTPServer started");
        } catch (Exception ex) {
            throw new EventMeshServerException(ex);
        }
    }

    @Override
    public void shutdown() throws EventMeshServerException {
        super.shutdown();
        try {
            if (CommonConfiguration.eventMeshServerSecurityEnable) {
                acl.shutdown();
            }
            producerManager.shutdown();

            consumerManager.shutdown();

            AbstractHTTPPushRequest.httpClientPool.shutdown();

            logger.info("--------------------------EventMeshHTTPServer shutdown");
        } catch (Exception ex) {
            throw new EventMeshServerException(ex);
        }
    }

    public ConsumerManager getConsumerManager() {
        return consumerManager;
    }

    public ProducerManager getProducerManager() {
        return producerManager;
    }

    public ServiceState getServiceState() {
        return serviceState;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public ConcurrentHashMap<String, ConsumerGroupConf> getLocalConsumerGroupMapping() {
        return localConsumerGroupMapping;
    }

    public ConcurrentHashMap<String, List<Client>> getLocalClientInfoMapping() {
        return localClientInfoMapping;
    }

}
