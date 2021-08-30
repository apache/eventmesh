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
package org.apache.eventmesh.server.tcp.rebalance.recommend;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.server.tcp.EventMeshTCPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventMeshRecommendImpl implements EventMeshRecommendStrategy {

    protected final Logger logger = LoggerFactory.getLogger(EventMeshRecommendImpl.class);

    private EventMeshTCPServer eventMeshTCPServer;

    public EventMeshRecommendImpl(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public String calculateRecommendEventMesh(String group, String purpose) throws Exception {
        List<EventMeshDataInfo> eventMeshDataInfoList = null;
        if (StringUtils.isBlank(group) || StringUtils.isBlank(purpose)) {
            logger.warn("EventMeshRecommend failed,params illegal,group:{},purpose:{}", group, purpose);
            return null;
        }
        final String cluster = CommonConfiguration.eventMeshCluster;
        try {
            eventMeshDataInfoList = eventMeshTCPServer.getRegistry().findEventMeshInfoByCluster(cluster);
        } catch (Exception e) {
            logger.warn("EventMeshRecommend failed, findEventMeshInfoByCluster failed, cluster:{}, group:{}, purpose:{}, errMsg:{}", cluster, group, purpose, e);
            return null;
        }

        if (eventMeshDataInfoList == null || CollectionUtils.isEmpty(eventMeshDataInfoList)) {
            logger.warn("EventMeshRecommend failed,not find eventMesh instances from registry,cluster:{},group:{},purpose:{}", cluster, group, purpose);
            return null;
        }

        Map<String, String> localEventMeshMap = new HashMap<>();
        Map<String, String> remoteEventMeshMap = new HashMap<>();
        String localIdc = CommonConfiguration.eventMeshIDC;
        for (EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfoList) {
            String idc = eventMeshDataInfo.getEventMeshName().split("-")[0];
            if (StringUtils.isNotBlank(idc)) {
                if (StringUtils.equals(idc, localIdc)) {
                    localEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
                } else {
                    remoteEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
                }
            } else {
                logger.error("EventMeshName may be illegal,idc is null,eventMeshName:{}", eventMeshDataInfo.getEventMeshName());
            }
        }

        if (localEventMeshMap.size() == 0 && remoteEventMeshMap.size() == 0) {
            logger.warn("EventMeshRecommend failed,find no legal eventMesh instances from registry,localIDC:{}", localIdc);
            return null;
        }
        if (localEventMeshMap.size() > 0) {
            //recommend eventmesh of local idc
            return recommendProxyByDistributeData(cluster, group, purpose, localEventMeshMap, true);
        } else if (remoteEventMeshMap.size() > 0) {
            //recommend eventmesh of other idc
            return recommendProxyByDistributeData(cluster, group, purpose, remoteEventMeshMap, false);
        } else {
            logger.error("localEventMeshMap or remoteEventMeshMap size error");
            return null;
        }
    }

    @Override
    public List<String> calculateRedirectRecommendEventMesh(Map<String, String> eventMeshMap, Map<String, Integer> clientDistributeMap, String group, int recommendProxyNum) throws Exception {
        logger.info("eventMeshMap:{},clientDistributionMap:{},group:{},recommendNum:{}", eventMeshMap, clientDistributeMap, group, recommendProxyNum);
        List<String> recommendProxyList = null;

        //find eventmesh with least client
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : clientDistributeMap.entrySet()) {
            list.add(entry);
        }
        Collections.sort(list, Comparator.comparingInt(Map.Entry::getValue));
        logger.info("clientDistributionMap after sort:{}", list);

        recommendProxyList = new ArrayList<>(recommendProxyNum);
        while (recommendProxyList.size() < recommendProxyNum) {
            Map.Entry<String, Integer> minProxyItem = list.get(0);
            int currProxyNum = clientDistributeMap.get(CommonConfiguration.eventMeshName);
            recommendProxyList.add(eventMeshMap.get(minProxyItem.getKey()));
            clientDistributeMap.put(minProxyItem.getKey(), minProxyItem.getValue() + 1);
            clientDistributeMap.put(CommonConfiguration.eventMeshName, currProxyNum - 1);
            Collections.sort(list, Comparator.comparingInt(Map.Entry::getValue));
            logger.info("clientDistributionMap after sort:{}", list);
        }
        logger.info("choose proxys with min instance num, group:{}, recommendProxyNum:{}, recommendProxyList:{}", group, recommendProxyNum, recommendProxyList);
        return recommendProxyList;
    }

    private String recommendProxyByDistributeData(String cluster, String group, String purpose, Map<String, String> eventMeshMap, boolean caculateLocal) {
        logger.info("eventMeshMap:{},cluster:{},group:{},purpose:{},caculateLocal:{}", eventMeshMap, cluster, group, purpose, caculateLocal);

        String recommendProxyAddr = null;
        List<String> tmpProxyAddrList = null;
        Map<String, Map<String, Integer>> eventMeshClientDistributionDataMap = null;
        try {
            eventMeshClientDistributionDataMap = eventMeshTCPServer.getRegistry().findEventMeshClientDistributionData(cluster, group, purpose);
        } catch (Exception e) {
            logger.warn("EventMeshRecommend failed,findEventMeshClientDistributionData failed,cluster:{},group:{},purpose:{}, errMsg:{}", cluster, group, purpose, e);
        }

        if (eventMeshClientDistributionDataMap == null || MapUtils.isEmpty(eventMeshClientDistributionDataMap)) {
            tmpProxyAddrList = new ArrayList<>(eventMeshMap.values());
            Collections.shuffle(tmpProxyAddrList);
            recommendProxyAddr = tmpProxyAddrList.get(0);
            logger.info("No distribute data in registry,cluster:{}, group:{},purpose:{}, recommendProxyAddr:{}", cluster, group, purpose, recommendProxyAddr);
            return recommendProxyAddr;
        }

        Map<String, Integer> localClientDistributionMap = new HashMap<>();
        Map<String, Integer> remoteClientDistributionMap = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : eventMeshClientDistributionDataMap.entrySet()) {
            String idc = entry.getKey().split("-")[0];
            if (StringUtils.isNotBlank(idc)) {
                if (StringUtils.equals(idc, CommonConfiguration.eventMeshIDC)) {
                    localClientDistributionMap.put(entry.getKey(), entry.getValue().get(purpose));
                } else {
                    remoteClientDistributionMap.put(entry.getKey(), entry.getValue().get(purpose));
                }
            } else {
                logger.error("eventMeshName may be illegal,idc is null,eventMeshName:{}", entry.getKey());
            }
        }
        recommendProxyAddr = recommendProxy(eventMeshMap, (caculateLocal == true) ? localClientDistributionMap : remoteClientDistributionMap, group);
        logger.info("eventMeshMap:{},group:{},purpose:{},caculateLocal:{},recommendProxyAddr:{}", eventMeshMap, group, purpose, caculateLocal, recommendProxyAddr);
        return recommendProxyAddr;
    }

    private String recommendProxy(Map<String, String> eventMeshMap, Map<String, Integer> clientDistributionMap, String group) {
        logger.info("eventMeshMap:{},clientDistributionMap:{},group:{}", eventMeshMap, clientDistributionMap, group);
        String recommendProxy = null;

        for (String proxyName : clientDistributionMap.keySet()) {
            if (!eventMeshMap.keySet().contains(proxyName)) {
                logger.warn("exist proxy not register but exist in distributionMap,proxy:{}", proxyName);
                return null;
            }
        }
        for (String proxy : eventMeshMap.keySet()) {
            if (!clientDistributionMap.keySet().contains(proxy)) {
                clientDistributionMap.put(proxy, 0);
            }
        }

        //select the eventmesh with least instances
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : clientDistributionMap.entrySet()) {
            list.add(entry);
        }
        if (list.size() == 0) {
            logger.error("no legal distribute data,check eventMeshMap and distributeData, group:{}", group);
            return null;
        } else {
            Collections.sort(list, Comparator.comparingInt(Map.Entry::getValue));
            logger.info("clientDistributionMap after sort:{}", list);
            recommendProxy = eventMeshMap.get(list.get(0).getKey());
            return recommendProxy;
        }
    }
}
