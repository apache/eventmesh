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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend;

import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.util.ValueComparator;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventMeshRecommendImpl implements EventMeshRecommendStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventMeshRecommendImpl.class);

    private static final int DEFAULT_PROXY_NUM = 1;

    private final transient EventMeshTCPServer eventMeshTCPServer;

    public EventMeshRecommendImpl(final EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public String calculateRecommendEventMesh(final String group, final String purpose) throws Exception {
        List<EventMeshDataInfo> eventMeshDataInfoList;

        if (StringUtils.isBlank(group) || StringUtils.isBlank(purpose)) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("EventMeshRecommend failed,params illegal,group:{},purpose:{}", group, purpose);
            }
            return null;
        }

        final String cluster = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshCluster();
        try {
            eventMeshDataInfoList = eventMeshTCPServer.getRegistry().findEventMeshInfoByCluster(cluster);
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("EventMeshRecommend failed, findEventMeshInfoByCluster failed, cluster:{}, group:{}, purpose:{}, errMsg:{}",
                        cluster, group, purpose, e);
            }
            return null;
        }

        if (eventMeshDataInfoList == null || CollectionUtils.isEmpty(eventMeshDataInfoList)) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("EventMeshRecommend failed,not find eventMesh instances from registry,cluster:{},group:{},purpose:{}",
                        cluster, group, purpose);
            }
            return null;
        }

        final Map<String, String> localEventMeshMap = new HashMap<>();
        final Map<String, String> remoteEventMeshMap = new HashMap<>();
        final String localIdc = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC();
        for (final EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfoList) {
            final String idc = eventMeshDataInfo.getEventMeshName().split("-")[0];
            if (StringUtils.isNotBlank(idc)) {
                if (StringUtils.equals(idc, localIdc)) {
                    localEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
                } else {
                    remoteEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
                }
            } else {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("EventMeshName may be illegal,idc is null,eventMeshName:{}", eventMeshDataInfo.getEventMeshName());
                }
            }
        }

        if (MapUtils.isEmpty(localEventMeshMap)) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("EventMeshRecommend failed,find no legal eventMesh instances from registry,localIDC:{}", localIdc);
            }
            return null;
        }

        if (MapUtils.isNotEmpty(localEventMeshMap)) {
            //recommend eventmesh of local idc
            return recommendProxyByDistributeData(cluster, group, purpose, localEventMeshMap, true);
        } else if (MapUtils.isNotEmpty(remoteEventMeshMap)) {
            //recommend eventmesh of other idc
            return recommendProxyByDistributeData(cluster, group, purpose, remoteEventMeshMap, false);
        } else {
            LOGGER.error("localEventMeshMap or remoteEventMeshMap size error");
            return null;
        }
    }

    @Override
    public List<String> calculateRedirectRecommendEventMesh(final Map<String, String> eventMeshMap,
                                                            final Map<String, Integer> clientDistributeMap,
                                                            final String group,
                                                            final int recommendProxyNum,
                                                            final String eventMeshName) throws Exception {
        if (recommendProxyNum < DEFAULT_PROXY_NUM) {
            return new ArrayList<String>();
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("eventMeshMap:{},clientDistributionMap:{},group:{},recommendNum:{},currEventMeshName:{}",
                    eventMeshMap, clientDistributeMap, group, recommendProxyNum, eventMeshName);
        }
        //find eventmesh with least client
        final List<Map.Entry<String, Integer>> list = new ArrayList<>();
        final ValueComparator vc = new ValueComparator();
        for (final Map.Entry<String, Integer> entry : clientDistributeMap.entrySet()) {
            list.add(entry);
        }
        Collections.sort(list, vc);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("clientDistributionMap after sort:{}", list);
        }

        final List<String> recommendProxyList = new ArrayList<>(recommendProxyNum);
        while (recommendProxyList.size() < recommendProxyNum) {
            final Map.Entry<String, Integer> minProxyItem = list.get(0);
            final int currProxyNum = clientDistributeMap.get(eventMeshName);
            recommendProxyList.add(eventMeshMap.get(minProxyItem.getKey()));
            clientDistributeMap.put(minProxyItem.getKey(), minProxyItem.getValue() + 1);
            clientDistributeMap.put(eventMeshName, currProxyNum - 1);
            Collections.sort(list, vc);

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("clientDistributionMap after sort:{}", list);
            }
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("choose proxys with min instance num, group:{}, recommendProxyNum:{}, recommendProxyList:{}",
                    group, recommendProxyNum, recommendProxyList);
        }
        return recommendProxyList;
    }

    private String recommendProxyByDistributeData(final String cluster, final String group, final String purpose,
                                                  final Map<String, String> eventMeshMap, final boolean caculateLocal) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("eventMeshMap:{},cluster:{},group:{},purpose:{},caculateLocal:{}", eventMeshMap, cluster,
                    group, purpose, caculateLocal);
        }


        Map<String, Map<String, Integer>> eventMeshClientDistributionDataMap = null;
        try {
            eventMeshClientDistributionDataMap = eventMeshTCPServer.getRegistry().findEventMeshClientDistributionData(
                    cluster, group, purpose);
        } catch (Exception e) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("EventMeshRecommend failed,findEventMeshClientDistributionData failed,"
                        + "cluster:{},group:{},purpose:{}, errMsg:{}", cluster, group, purpose, e);
            }
        }

        String recommendProxyAddr;
        if (MapUtils.isEmpty(eventMeshClientDistributionDataMap)) {
            final List<String> tmpProxyAddrList = new ArrayList<>(eventMeshMap.values());
            Collections.shuffle(tmpProxyAddrList);
            recommendProxyAddr = tmpProxyAddrList.get(0);
            if (LOGGER.isWarnEnabled()) {
                LOGGER.info("No distribute data in registry,cluster:{}, group:{},purpose:{}, recommendProxyAddr:{}",
                        cluster, group, purpose, recommendProxyAddr);
            }
            return recommendProxyAddr;
        }

        final Map<String, Integer> localClientDistributionMap = new HashMap<>();
        final Map<String, Integer> remoteClientDistributionMap = new HashMap<>();
        for (final Map.Entry<String, Map<String, Integer>> entry : eventMeshClientDistributionDataMap.entrySet()) {
            final String idc = entry.getKey().split("-")[0];
            if (StringUtils.isNotBlank(idc)) {
                if (StringUtils.equals(idc, eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC())) {
                    localClientDistributionMap.put(entry.getKey(), entry.getValue().get(purpose));
                } else {
                    remoteClientDistributionMap.put(entry.getKey(), entry.getValue().get(purpose));
                }
            } else {
                if (LOGGER.isErrorEnabled()) {
                    LOGGER.error("eventMeshName may be illegal,idc is null,eventMeshName:{}", entry.getKey());
                }
            }
        }

        recommendProxyAddr = recommendProxy(eventMeshMap, (caculateLocal == true) ? localClientDistributionMap
                : remoteClientDistributionMap, group);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("eventMeshMap:{},group:{},purpose:{},caculateLocal:{},recommendProxyAddr:{}", eventMeshMap,
                    group, purpose, caculateLocal, recommendProxyAddr);
        }
        return recommendProxyAddr;
    }

    private String recommendProxy(final Map<String, String> eventMeshMap,
                                  final Map<String, Integer> clientDistributionMap, final String group) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("eventMeshMap:{},clientDistributionMap:{},group:{}", eventMeshMap, clientDistributionMap, group);
        }

        for (final String proxyName : clientDistributionMap.keySet()) {
            if (!eventMeshMap.containsKey(proxyName)) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("exist proxy not register but exist in distributionMap,proxy:{}", proxyName);
                }
                return null;
            }
        }

        for (final String proxy : eventMeshMap.keySet()) {
            if (!clientDistributionMap.containsKey(proxy)) {
                clientDistributionMap.put(proxy, 0);
            }
        }

        //select the eventmesh with least instances
        final List<Map.Entry<String, Integer>> list = new ArrayList<>();
        final ValueComparator vc = new ValueComparator();
        for (final Map.Entry<String, Integer> entry : clientDistributionMap.entrySet()) {
            list.add(entry);
        }

        if (CollectionUtils.isEmpty(list)0) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("no legal distribute data,check eventMeshMap and distributeData, group:{}", group);
            }
            return null;
        } else {
            Collections.sort(list, vc);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("clientDistributionMap after sort:{}", list);
            }
            return eventMeshMap.get(list.get(0).getKey());
        }
    }

}
