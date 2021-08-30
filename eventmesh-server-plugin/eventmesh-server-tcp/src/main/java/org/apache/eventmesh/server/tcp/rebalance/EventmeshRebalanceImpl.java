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
package org.apache.eventmesh.server.tcp.rebalance;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.api.registry.dto.EventMeshDataInfo;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.server.tcp.EventMeshTCPServer;
import org.apache.eventmesh.server.tcp.client.session.Session;
import org.apache.eventmesh.server.tcp.config.EventMeshTCPConfiguration;
import org.apache.eventmesh.server.tcp.config.TcpProtocolConstants;
import org.apache.eventmesh.server.tcp.rebalance.recommend.EventMeshRecommendImpl;
import org.apache.eventmesh.server.tcp.rebalance.recommend.EventMeshRecommendStrategy;
import org.apache.eventmesh.server.tcp.utils.EventMeshTcp2Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventmeshRebalanceImpl implements EventMeshRebalanceStrategy {

    protected final Logger logger = LoggerFactory.getLogger(EventmeshRebalanceImpl.class);

    private EventMeshTCPServer eventMeshTCPServer;

    public EventmeshRebalanceImpl(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void doRebalance() throws Exception {
        long startTime = System.currentTimeMillis();
        logger.info("doRebalance start===========startTime:{}", startTime);

        Set<String> groupSet = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupMap().keySet();
        if (CollectionUtils.isEmpty(groupSet)) {
            logger.warn("doRebalance failed,eventmesh has no group, please check eventmeshData");
            return;
        }

        final String cluster = CommonConfiguration.eventMeshCluster;
        //get eventmesh of local idc
        Map<String, String> localEventMeshMap = queryLocalEventMeshMap(cluster);
        if (localEventMeshMap == null || localEventMeshMap.size() == 0) {
            return;
        }

        for (String group : groupSet) {
            doRebalanceByGroup(cluster, group, TcpProtocolConstants.PURPOSE_SUB, localEventMeshMap);
            doRebalanceByGroup(cluster, group, TcpProtocolConstants.PURPOSE_PUB, localEventMeshMap);
        }
        logger.info("doRebalance end===========startTime:{}, cost:{}", startTime, System.currentTimeMillis() - startTime);
    }

    private Map<String, String> queryLocalEventMeshMap(String cluster) {
        Map<String, String> localEventMeshMap = null;
        List<EventMeshDataInfo> eventMeshDataInfoList = null;
        try {
            eventMeshDataInfoList = eventMeshTCPServer.getRegistry().findEventMeshInfoByCluster(cluster);

            if (eventMeshDataInfoList == null || CollectionUtils.isEmpty(eventMeshDataInfoList)) {
                logger.warn("doRebalance failed,query eventmesh instances is null from registry,cluster:{}", cluster);
                return null;
            }
            localEventMeshMap = new HashMap<>();
            String localIdc = CommonConfiguration.eventMeshIDC;
            for (EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfoList) {
                String idc = eventMeshDataInfo.getEventMeshName().split("-")[0];
                if (StringUtils.isNotBlank(idc) && StringUtils.equals(idc, localIdc)) {
                    localEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
                }
            }

            if (0 == localEventMeshMap.size()) {
                logger.warn("doRebalance failed,query eventmesh instances of localIDC is null from registry,localIDC:{},cluster:{}", localIdc, cluster);
                return null;
            }
        } catch (Exception e) {
            logger.warn("doRebalance failed,findEventMeshInfoByCluster failed,cluster:{},errMsg:{}", cluster, e);
            return null;
        }

        return localEventMeshMap;
    }

    private void doRebalanceByGroup(String cluster, String group, String purpose, Map<String, String> eventMeshMap) throws Exception {
        //query distribute data of loacl idc
        Map<String, Integer> clientDistributionMap = queryLocalEventMeshDistributeData(cluster, group, purpose, eventMeshMap);
        if (clientDistributionMap == null || clientDistributionMap.size() == 0) {
            return;
        }

        int sum = 0;
        for (Integer item : clientDistributionMap.values()) {
            sum += item.intValue();
        }
        int currentNum = 0;
        if (clientDistributionMap.get(CommonConfiguration.eventMeshName) != null) {
            currentNum = clientDistributionMap.get(CommonConfiguration.eventMeshName);
        }
        int avgNum = sum / clientDistributionMap.size();
        int judge = avgNum >= 2 ? avgNum / 2 : 1;

        if (currentNum - avgNum > judge) {
            Set<Session> sessionSet = null;
            if (TcpProtocolConstants.PURPOSE_PUB.equals(purpose)) {
                sessionSet = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupMap().get(group).getGroupProducerSessions();
            } else if (TcpProtocolConstants.PURPOSE_SUB.equals(purpose)) {
                sessionSet = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupMap().get(group).getGroupConsumerSessions();
            } else {
                logger.warn("doRebalance failed,purpose is not support,purpose:{}", purpose);
                return;
            }

            List<Session> sessionList = new ArrayList<>(sessionSet);
            Collections.shuffle(new ArrayList<>(sessionList));
            EventMeshRecommendStrategy eventMeshRecommendStrategy = new EventMeshRecommendImpl(eventMeshTCPServer);
            List<String> eventMeshRecommendResult = eventMeshRecommendStrategy.calculateRedirectRecommendEventMesh(eventMeshMap, clientDistributionMap, group, judge);
            if (eventMeshRecommendResult == null || eventMeshRecommendResult.size() != judge) {
                logger.warn("doRebalance failed,recommendProxyNum is not consistent,recommendResult:{},judge:{}", eventMeshRecommendResult, judge);
                return;
            }
            logger.info("doRebalance redirect start---------------------group:{},purpose:{},judge:{}", group, purpose, judge);
            for (int i = 0; i < judge; i++) {
                //String redirectSessionAddr = ProxyTcp2Client.redirectClientForRebalance(sessionList.get(i), eventMeshTCPServer.getClientSessionGroupMapping());
                String newProxyIp = eventMeshRecommendResult.get(i).split(":")[0];
                String newProxyPort = eventMeshRecommendResult.get(i).split(":")[1];
                String redirectSessionAddr = EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer, newProxyIp, Integer.valueOf(newProxyPort), sessionList.get(i), eventMeshTCPServer.getClientSessionGroupMapping());
                logger.info("doRebalance,redirect sessionAddr:{}", redirectSessionAddr);
                try {
                    Thread.sleep(EventMeshTCPConfiguration.sleepIntervalInRebalanceRedirectMills);
                } catch (InterruptedException e) {
                    logger.warn("Thread.sleep occur InterruptedException", e);
                }
            }
            logger.info("doRebalance redirect end---------------------group:{}, purpose:{}", group, purpose);
        } else {
            logger.info("rebalance condition not satisfy,group:{},sum:{},currentNum:{},avgNum:{},judge:{}", group, sum, currentNum, avgNum, judge);
        }
    }

    private Map<String, Integer> queryLocalEventMeshDistributeData(String cluster, String group, String purpose, Map<String, String> eventMeshMap) {
        Map<String, Integer> localEventMeshDistributeData = null;
        Map<String, Map<String, Integer>> eventMeshClientDistributionDataMap = null;
        try {
            eventMeshClientDistributionDataMap = eventMeshTCPServer.getRegistry().findEventMeshClientDistributionData(cluster, group, purpose);

            if (eventMeshClientDistributionDataMap == null || eventMeshClientDistributionDataMap.size() == 0) {
                logger.warn("doRebalance failed,found no distribute data in regitry, cluster:{}, group:{}, purpose:{}", cluster, group, purpose);
                return null;
            }

            localEventMeshDistributeData = new HashMap<>();
            String localIdc = CommonConfiguration.eventMeshIDC;
            for (Map.Entry<String, Map<String, Integer>> entry : eventMeshClientDistributionDataMap.entrySet()) {
                String idc = entry.getKey().split("-")[0];
                if (StringUtils.isNotBlank(idc) && StringUtils.equals(idc, localIdc)) {
                    localEventMeshDistributeData.put(entry.getKey(), entry.getValue().get(purpose));
                }
            }

            if (0 == localEventMeshDistributeData.size()) {
                logger.warn("doRebalance failed,found no distribute data of localIDC in regitry,cluster:{},group:{}, purpose:{},localIDC:{}", cluster, group, purpose, localIdc);
                return null;
            }

            logger.info("before revert clientDistributionMap:{}, group:{}, purpose:{}", localEventMeshDistributeData, group, purpose);
            for (String eventMeshName : localEventMeshDistributeData.keySet()) {
                if (!eventMeshMap.keySet().contains(eventMeshName)) {
                    logger.warn("doRebalance failed,exist eventMesh not register but exist in distributionMap,cluster:{},grpup:{},purpose:{},eventMeshName:{}", cluster, group, purpose, eventMeshName);
                    return null;
                }
            }
            for (String eventMesh : eventMeshMap.keySet()) {
                if (!localEventMeshDistributeData.keySet().contains(eventMesh)) {
                    localEventMeshDistributeData.put(eventMesh, 0);
                }
            }
            logger.info("after revert clientDistributionMap:{}, group:{}, purpose:{}", localEventMeshDistributeData, group, purpose);
        } catch (Exception e) {
            logger.warn("doRebalance failed,cluster:{},group:{},purpose:{},findProxyClientDistributionData failed, errMsg:{}", cluster, group, purpose, e);
            return null;
        }

        return localEventMeshDistributeData;
    }

}
