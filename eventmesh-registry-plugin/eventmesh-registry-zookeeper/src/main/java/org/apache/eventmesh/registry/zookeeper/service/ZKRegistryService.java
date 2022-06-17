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

package org.apache.eventmesh.registry.zookeeper.service;


import com.google.common.collect.Maps;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.eventmesh.api.exception.RegistryException;
import org.apache.eventmesh.api.registry.RegistryService;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshRegisterInfo;
import org.apache.eventmesh.api.registry.dto.EventMeshUnRegisterInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.utils.ConfigurationContextUtil;
import org.apache.eventmesh.registry.zookeeper.constant.ZKConstant;
import org.apache.eventmesh.registry.zookeeper.util.JsonUtils;
import org.apache.zookeeper.CreateMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class ZKRegistryService implements RegistryService {

    private static final Logger logger = LoggerFactory.getLogger(ZKRegistryService.class);

    private static final AtomicBoolean INIT_STATUS = new AtomicBoolean(false);

    private static final AtomicBoolean START_STATUS = new AtomicBoolean(false);

    private String serverAddr;

    public CuratorFramework zkClient = null;


    @Override
    public void init() throws RegistryException {
        boolean update = INIT_STATUS.compareAndSet(false, true);
        if (!update) {
            return;
        }

        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration commonConfiguration = ConfigurationContextUtil.get(key);
            if (null == commonConfiguration) {
                continue;
            }
            if (StringUtils.isBlank(commonConfiguration.namesrvAddr)) {
                throw new RegistryException("namesrvAddr cannot be null");
            }
            this.serverAddr = commonConfiguration.namesrvAddr;
            break;
        }
    }

    @Override
    public void start() throws RegistryException {
        boolean update = START_STATUS.compareAndSet(false, true);
        if (!update) {
            return;
        }
        try {
            RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 5);

            zkClient = CuratorFrameworkFactory.builder()
                    .connectString(serverAddr)
                    .sessionTimeoutMs(ZKConstant.SESSION_TIME_OUT)
                    .retryPolicy(retryPolicy)
                    .namespace(ZKConstant.NAMESPACE)
                    .build();
            zkClient.start();

        } catch (Exception e) {
            logger.error("[ZKRegistryService][start] error", e);
            throw new RegistryException(e.getMessage());
        }
    }

    @Override
    public void shutdown() throws RegistryException {
        INIT_STATUS.compareAndSet(true, false);
        START_STATUS.compareAndSet(true, false);
        try {
            zkClient.close();
        } catch (Exception e) {
            logger.error("[ZKRegistryService][shutdown] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("ZKRegistryService close");
    }

    @Override
    public List<EventMeshDataInfo> findEventMeshInfoByCluster(String clusterName) throws RegistryException {
        List<EventMeshDataInfo> eventMeshDataInfoList = new ArrayList<>();
        for (String key : ConfigurationContextUtil.KEYS) {
            CommonConfiguration configuration = ConfigurationContextUtil.get(key);
            if (Objects.isNull(configuration)) {
                continue;
            }
            String eventMeshName = configuration.eventMeshName;
            try {

                String serviceName = eventMeshName.concat("-").concat(key);
                String clusterPath = formatServicePath(clusterName,serviceName);

                List<String> instances = zkClient.getChildren().forPath(clusterPath);

                if (CollectionUtils.isEmpty(instances)) {
                    continue;
                }

                eventMeshDataInfoList = instances.stream()
                        .map(p -> new EventMeshDataInfo(clusterName, serviceName, p, 0L))
                        .collect(Collectors.toList());

            } catch (Exception e) {
                logger.error("[ZKRegistryService][findEventMeshInfoByCluster] error", e);
                throw new RegistryException(e.getMessage());
            }

        }
        return eventMeshDataInfoList;
    }

    @Override
    public Map<String, Map<String, Integer>> findEventMeshClientDistributionData(String clusterName, String group, String purpose)
            throws RegistryException {
        // todo find metadata
        return null;
    }

    @Override
    public boolean register(EventMeshRegisterInfo eventMeshRegisterInfo) throws RegistryException {
        try {

            String[] ipPort = eventMeshRegisterInfo.getEndPoint().split(ZKConstant.IP_PORT_SEPARATOR);
            String ip = ipPort[0];
            Integer port = Integer.valueOf(ipPort[1]);
            String eventMeshName = eventMeshRegisterInfo.getEventMeshName();
            String eventMeshClusterName = eventMeshRegisterInfo.getEventMeshClusterName();
            Map<String, Map<String, Integer>> instanceNumMap = eventMeshRegisterInfo.getEventMeshInstanceNumMap();

            // clusterName/eventMeshName/ip:port
            String path = formatInstancePath(eventMeshClusterName,eventMeshName,eventMeshRegisterInfo.getEndPoint());

            HashMap<String, Object> dataMap = Maps.newHashMap();
            dataMap.put("ip", ip);
            dataMap.put("port", port);
            dataMap.put("weight", 1.0);
            dataMap.put("instanceNumMap", instanceNumMap);

            zkClient.create()
                    .creatingParentsIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(path, JsonUtils.toJSON(dataMap).getBytes(Charset.forName("utf-8")));

        } catch (Exception e) {
            logger.error("[ZKRegistryService][register] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully registered to zookeeper");
        return true;
    }

    @Override
    public boolean unRegister(EventMeshUnRegisterInfo eventMeshUnRegisterInfo) throws RegistryException {
        try {
            String eventMeshName = eventMeshUnRegisterInfo.getEventMeshName();
            String eventMeshClusterName = eventMeshUnRegisterInfo.getEventMeshClusterName();

            String path = formatInstancePath(eventMeshClusterName,eventMeshName,eventMeshUnRegisterInfo.getEndPoint());

            zkClient.delete().forPath(path);
        } catch (Exception e) {
            logger.error("[ZKRegistryService][unRegister] error", e);
            throw new RegistryException(e.getMessage());
        }
        logger.info("EventMesh successfully logout to zookeeper");
        return true;
    }

    private String formatInstancePath(String clusterName, String serviceName, String endPoint){
        return ZKConstant.PATH_SEPARATOR.concat(clusterName)
                .concat(ZKConstant.PATH_SEPARATOR).concat(serviceName)
                .concat(ZKConstant.PATH_SEPARATOR).concat(endPoint);
    }

    private String formatServicePath(String clusterName,String serviceName){
        return ZKConstant.PATH_SEPARATOR.concat(clusterName)
                .concat(ZKConstant.PATH_SEPARATOR).concat(serviceName);
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public CuratorFramework getZkClient() {
        return zkClient;
    }
}
