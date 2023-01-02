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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshRecommendImpl implements EventMeshRecommendStrategy {
    
    private final EventMeshTCPServer eventMeshTCPServer;

    public EventMeshRecommendImpl(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public String calculateRecommendEventMesh(String group, String purpose) {
        List<EventMeshDataInfo> eventMeshDataInfoList;
        if (StringUtils.isAnyBlank(group, purpose)) {
            log.warn("EventMeshRecommend failed,params illegal,group:{},purpose:{}", group, purpose);
            return null;
        }
        final String cluster = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshCluster();
        try {
            eventMeshDataInfoList = eventMeshTCPServer.getRegistry().findEventMeshInfoByCluster(cluster);
        } catch (Exception e) {
            log.warn("EventMeshRecommend failed, findEventMeshInfoByCluster failed, cluster:{}, group:{}, purpose:{}, errMsg:{}",
                    cluster, group, purpose, e);
            return null;
        }

        if (CollectionUtils.isEmpty(eventMeshDataInfoList)) {
            log.warn("EventMeshRecommend failed,not find eventMesh instances from registry,cluster:{},group:{},purpose:{}",
                    cluster, group, purpose);
            return null;
        }

        Map<String, String> localEventMeshMap = new HashMap<>();
        Map<String, String> remoteEventMeshMap = new HashMap<>();
        String localIdc = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC();
        for (EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfoList) {
            String idc = eventMeshDataInfo.getEventMeshName().split("-")[0];
            if (!StringUtils.isNotBlank(idc)) {
                log.error("EventMeshName may be illegal,idc is null,eventMeshName:{}", eventMeshDataInfo.getEventMeshName());
                continue;
            }
            if (StringUtils.equals(idc, localIdc)) {
                localEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
            } else {
                remoteEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
            }
        }

        if (localEventMeshMap.isEmpty() && remoteEventMeshMap.isEmpty()) {
            log.warn("EventMeshRecommend failed,find no legal eventMesh instances from registry,localIDC:{}", localIdc);
            return null;
        }
        if (localEventMeshMap.size() > 0) {
            //recommend eventmesh of local idc
            return recommendProxyByDistributeData(cluster, group, purpose, localEventMeshMap, true);
        } else if (remoteEventMeshMap.size() > 0) {
            //recommend eventmesh of other idc
            return recommendProxyByDistributeData(cluster, group, purpose, remoteEventMeshMap, false);
        } else {
            log.error("localEventMeshMap or remoteEventMeshMap size error");
            return null;
        }
    }

    @Override
    public List<String> calculateRedirectRecommendEventMesh(Map<String, String> eventMeshMap,
                                                            Map<String, Integer> clientDistributeMap, String group,
                                                            int recommendProxyNum, String eventMeshName) throws Exception {
        if (recommendProxyNum < 1) {
            return null;
        }
        log.info("eventMeshMap:{},clientDistributionMap:{},group:{},recommendNum:{},currEventMeshName:{}",
                eventMeshMap, clientDistributeMap, group, recommendProxyNum, eventMeshName);
        //find eventmesh with least client
        ValueComparator vc = new ValueComparator();
        List<Map.Entry<String, Integer>> list = new ArrayList<>(clientDistributeMap.entrySet());
        list.sort(vc);
        log.info("clientDistributionMap after sort:{}", list);

        List<String> recommendProxyList = new ArrayList<>(recommendProxyNum);
        while (recommendProxyList.size() < recommendProxyNum) {
            Map.Entry<String, Integer> minProxyItem = list.get(0);
            int currProxyNum = clientDistributeMap.get(eventMeshName);
            recommendProxyList.add(eventMeshMap.get(minProxyItem.getKey()));
            clientDistributeMap.put(minProxyItem.getKey(), minProxyItem.getValue() + 1);
            clientDistributeMap.put(eventMeshName, currProxyNum - 1);
            list.sort(vc);
            log.info("clientDistributionMap after sort:{}", list);
        }
        log.info("choose proxys with min instance num, group:{}, recommendProxyNum:{}, recommendProxyList:{}",
                group, recommendProxyNum, recommendProxyList);
        return recommendProxyList;
    }

    private String recommendProxyByDistributeData(String cluster, String group, String purpose,
                                                  Map<String, String> eventMeshMap, boolean caculateLocal) {
        log.info("eventMeshMap:{},cluster:{},group:{},purpose:{},caculateLocal:{}", eventMeshMap, cluster,
                group, purpose, caculateLocal);

        String recommendProxyAddr;
        List<String> tmpProxyAddrList;
        Map<String, Map<String, Integer>> eventMeshClientDistributionDataMap = null;
        try {
            eventMeshClientDistributionDataMap = eventMeshTCPServer.getRegistry().findEventMeshClientDistributionData(
                    cluster, group, purpose);
        } catch (Exception e) {
            log.warn("EventMeshRecommend failed,findEventMeshClientDistributionData failed,"
                    + "cluster:{},group:{},purpose:{}, errMsg:{}", cluster, group, purpose, e);
        }

        if (eventMeshClientDistributionDataMap == null || MapUtils.isEmpty(eventMeshClientDistributionDataMap)) {
            tmpProxyAddrList = new ArrayList<>(eventMeshMap.values());
            Collections.shuffle(tmpProxyAddrList);
            recommendProxyAddr = tmpProxyAddrList.get(0);
            log.info("No distribute data in registry,cluster:{}, group:{},purpose:{}, recommendProxyAddr:{}",
                    cluster, group, purpose, recommendProxyAddr);
            return recommendProxyAddr;
        }

        Map<String, Integer> localClientDistributionMap = new HashMap<>();
        Map<String, Integer> remoteClientDistributionMap = new HashMap<>();
        eventMeshClientDistributionDataMap.forEach((k, v) -> {
            String idc = k.split("-")[0];
            if (StringUtils.isNotBlank(idc)) {
                if (StringUtils.equals(idc, eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC())) {
                    localClientDistributionMap.put(k, v.get(purpose));
                } else {
                    remoteClientDistributionMap.put(k, v.get(purpose));
                }
            } else {
                log.error("eventMeshName may be illegal,idc is null,eventMeshName:{}", k);
            }
        });
        recommendProxyAddr = recommendProxy(eventMeshMap, caculateLocal ? localClientDistributionMap
                : remoteClientDistributionMap, group);

        log.info("eventMeshMap:{},group:{},purpose:{},caculateLocal:{},recommendProxyAddr:{}", eventMeshMap,
                group, purpose, caculateLocal, recommendProxyAddr);
        return recommendProxyAddr;
    }

    private String recommendProxy(Map<String, String> eventMeshMap, Map<String, Integer> clientDistributionMap, String group) {
        log.info("eventMeshMap:{},clientDistributionMap:{},group:{}", eventMeshMap, clientDistributionMap, group);

        for (String proxyName : clientDistributionMap.keySet()) {
            if (!eventMeshMap.containsKey(proxyName)) {
                log.warn("exist proxy not register but exist in distributionMap,proxy:{}", proxyName);
                return null;
            }
        }
        for (String proxy : eventMeshMap.keySet()) {
            clientDistributionMap.putIfAbsent(proxy,0);
        }

        //select the eventmesh with least instances
        ValueComparator vc = new ValueComparator();
        List<Map.Entry<String, Integer>> list = new ArrayList<>(clientDistributionMap.entrySet());
        if (list.isEmpty()) {
            log.error("no legal distribute data,check eventMeshMap and distributeData, group:{}", group);
            return null;
        } else {
            list.sort(vc);
            log.info("clientDistributionMap after sort:{}", list);
            return eventMeshMap.get(list.get(0).getKey());
        }
    }

    private List<String> calculate(Map<String, String> proxyMap, Map<String, Integer> clientDistributionMap,
                                   String group, int recommendProxyNum) {
        return null;
    }
}
