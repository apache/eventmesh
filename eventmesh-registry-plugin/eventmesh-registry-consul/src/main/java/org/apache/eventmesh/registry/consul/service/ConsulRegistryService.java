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

package org.apache.eventmesh.registry.consul.service;

import static org.apache.eventmesh.registry.consul.constant.ConsulConstant.DEFAULT_CONNECTION_TIMEOUT;
import static org.apache.eventmesh.registry.consul.constant.ConsulConstant.DEFAULT_MAX_CONNECTIONS;
import static org.apache.eventmesh.registry.consul.constant.ConsulConstant.DEFAULT_MAX_PER_ROUTE_CONNECTIONS;
import static org.apache.eventmesh.registry.consul.constant.ConsulConstant.DEFAULT_READ_TIMEOUT;
import static org.apache.eventmesh.registry.consul.constant.ConsulConstant.IP_PORT_SEPARATOR;

import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.ConsulRawClient;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.agent.model.Service;
import com.ecwid.consul.v1.health.HealthServicesRequest;

public class ConsulRegistryService implements RegistryService {

    private static final Logger logger = LoggerFactory.getLogger(ConsulRegistryService.class);

    private static final AtomicBoolean INIT_STATUS = new AtomicBoolean(false);

    private static final AtomicBoolean START_STATUS = new AtomicBoolean(false);

    private String consulHost;

    private String consulPort;

    private ConsulClient consulClient;

    @Override
    public void init() throws RegistryException {
        if (INIT_STATUS.compareAndSet(false, true)) {
            for (String key : ConfigurationContextUtil.KEYS) {
                CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
                if (null != commonConfiguration) {
                    String namesrvAddr = commonConfiguration.namesrvAddr;
                    if (StringUtils.isBlank(namesrvAddr)) {
                        throw new RegistryException("namesrvAddr cannot be null");
                    }
                    String[] addr = namesrvAddr.split(":");
                    if (addr.length != 2) {
                        throw new RegistryException("Illegal namesrvAddr");
                    }
                    this.consulHost = addr[0];
                    this.consulPort = addr[1];
                    break;
                }
            }
        }
    }

    @Override
    public void start() throws RegistryException {
        consulClient = new ConsulClient(new ConsulRawClient(consulHost, Integer.parseInt(consulPort), getHttpClient()));
    }

    @Override
    public void shutdown() throws RegistryException {
        INIT_STATUS.compareAndSet(true, false);
        START_STATUS.compareAndSet(true, false);
        consulClient = null;
    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws RegistryException {
        try {
            String[] ipPort = eventMeshRegisterInfo.getEndPoint().split(IP_PORT_SEPARATOR);
            NewService service = new NewService();
            service.setPort(Integer.parseInt(ipPort[1]));
            service.setAddress(ipPort[0]);
            service.setName(eventMeshRegisterInfo.getEventMeshClusterName());
            service.setId(eventMeshRegisterInfo.getEventMeshClusterName() + "-" + eventMeshRegisterInfo.getEventMeshName());
            consulClient.agentServiceRegister(service);
        } catch (Exception e) {
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully registered to consul");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws RegistryException {
        try {
            consulClient.agentServiceDeregister(eventMeshUnRegisterInfo.getEventMeshClusterName() + "-" + eventMeshUnRegisterInfo.getEventMeshName());
        } catch (Exception e) {
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully unregistered to consul");
        return true;
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws RegistryException {
        Map<String, Service> agentServices = consulClient.getAgentServices().getValue();
        HealthServicesRequest request = HealthServicesRequest.newBuilder().setPassing(true).build();
        consulClient.getHealthServices(clusterName, request);
        List<EventMeshDataInfo> eventMeshDataInfos = new ArrayList<>();
        agentServices.forEach((k, v) -> {
            String[] split = v.getId().split("-");
            eventMeshDataInfos.add(new EventMeshDataInfo(split[0], split[1], v.getAddress() + ":" + v.getPort(), 0));
        });
        return eventMeshDataInfos;
    }

    @Override
    public Map<String, Map<String, Integer>> findEventMeshClientDistributionData(String clusterName, String group, String purpose)
        throws RegistryException {
        return Collections.emptyMap();
    }


    private HttpClient getHttpClient() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(DEFAULT_MAX_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE_CONNECTIONS);

        RequestConfig requestConfig = RequestConfig.custom().
            setConnectTimeout(DEFAULT_CONNECTION_TIMEOUT).
            setConnectionRequestTimeout(DEFAULT_CONNECTION_TIMEOUT).
            setSocketTimeout(DEFAULT_READ_TIMEOUT).
            build();

        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create().
            setConnectionManager(connectionManager).
            setDefaultRequestConfig(requestConfig).
            useSystemProperties();

        return httpClientBuilder.build();
    }

    public ConsulClient getConsulClient() {
        return consulClient;
    }
}
