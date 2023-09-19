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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.rebalance;

import org.apache.eventmesh.api.meta.dto.EventMeshDataInfo;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.boot.EventMeshTCPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.EventMeshTcp2Client;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendImpl;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.recommend.EventMeshRecommendStrategy;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshRebalanceImpl implements EventMeshRebalanceStrategy {

    private final EventMeshTCPServer eventMeshTCPServer;

    public EventMeshRebalanceImpl(EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public void doRebalance() throws Exception {
        long startTime = System.currentTimeMillis();
        log.info("doRebalance start===========startTime:{}", startTime);

        Set<String> groupSet = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupMap().keySet();
        if (CollectionUtils.isEmpty(groupSet)) {
            log.warn("doRebalance failed,eventmesh has no group, please check eventmeshData");
            return;
        }

        final String cluster = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshCluster();
        //get eventmesh of local idc
        Map<String, String> localEventMeshMap = queryLocalEventMeshMap(cluster);
        if (MapUtils.isEmpty(localEventMeshMap)) {
            return;
        }

        for (String group : groupSet) {
            doRebalanceByGroup(cluster, group, EventMeshConstants.PURPOSE_SUB, localEventMeshMap);
            doRebalanceByGroup(cluster, group, EventMeshConstants.PURPOSE_PUB, localEventMeshMap);
        }
        log.info("doRebalance end===========startTime:{}, cost:{}", startTime, System.currentTimeMillis() - startTime);
    }

    private Map<String, String> queryLocalEventMeshMap(String cluster) {
        Map<String, String> localEventMeshMap = null;
        List<EventMeshDataInfo> eventMeshDataInfoList = null;
        try {
            eventMeshDataInfoList = eventMeshTCPServer.getMetaStorage().findEventMeshInfoByCluster(cluster);

            if (eventMeshDataInfoList == null || CollectionUtils.isEmpty(eventMeshDataInfoList)) {
                log.warn("doRebalance failed,query eventmesh instances is null from registry,cluster:{}", cluster);
                return Collections.emptyMap();
            }
            localEventMeshMap = new HashMap<>();
            String localIdc = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC();
            for (EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfoList) {
                String idc = eventMeshDataInfo.getEventMeshName().split("-")[0];
                if (StringUtils.isNotBlank(idc) && StringUtils.equals(idc, localIdc)) {
                    localEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
                }
            }

            if (0 == localEventMeshMap.size()) {
                log.warn("doRebalance failed,query eventmesh instances of localIDC is null from registry,localIDC:{},cluster:{}",
                    localIdc, cluster);
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.warn("doRebalance failed,findEventMeshInfoByCluster failed,cluster:{},errMsg:{}", cluster, e);
            return Collections.emptyMap();
        }

        return localEventMeshMap;
    }

    private void doRebalanceByGroup(String cluster, String group, String purpose, Map<String, String> eventMeshMap) throws Exception {
        log.info("doRebalanceByGroup start, cluster:{}, group:{}, purpose:{}", cluster, group, purpose);

        //query distribute data of local idc
        Map<String, Integer> clientDistributionMap = queryLocalEventMeshDistributeData(cluster, group, purpose,
            eventMeshMap);
        if (MapUtils.isEmpty(clientDistributionMap)) {
            return;
        }

        doRebalanceRedirect(eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshName(), group, purpose,
            eventMeshMap, clientDistributionMap);
        log.info("doRebalanceByGroup end, cluster:{}, group:{}, purpose:{}", cluster, group, purpose);

    }

    private void doRebalanceRedirect(String currEventMeshName, String group, String purpose, Map<String, String> eventMeshMap,
        Map<String, Integer> clientDistributionMap) throws Exception {
        if (MapUtils.isEmpty(clientDistributionMap)) {
            return;
        }

        //calculate client num need to redirect in currEventMesh
        int judge = calculateRedirectNum(currEventMeshName, group, purpose, clientDistributionMap);

        if (judge > 0) {

            //select redirect target eventmesh list
            List<String> eventMeshRecommendResult = selectRedirectEventMesh(group, eventMeshMap, clientDistributionMap,
                judge, currEventMeshName);
            if (eventMeshRecommendResult == null || eventMeshRecommendResult.size() != judge) {
                log.warn("doRebalance failed,recommendEventMeshNum is not consistent,recommendResult:{},judge:{}",
                    eventMeshRecommendResult, judge);
                return;
            }

            //do redirect
            doRedirect(group, purpose, judge, eventMeshRecommendResult);
        } else {
            log.info("rebalance condition not satisfy,group:{}, purpose:{},judge:{}", group, purpose, judge);
        }
    }

    private void doRedirect(String group, String purpose, int judge, List<String> eventMeshRecommendResult) throws Exception {
        log.info("doRebalance redirect start---------------------group:{},judge:{}", group, judge);
        Set<Session> sessionSet = null;
        if (EventMeshConstants.PURPOSE_SUB.equals(purpose)) {
            sessionSet = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupMap().get(group).getGroupConsumerSessions();
        } else if (EventMeshConstants.PURPOSE_PUB.equals(purpose)) {
            sessionSet = eventMeshTCPServer.getClientSessionGroupMapping().getClientGroupMap().get(group).getGroupProducerSessions();
        } else {
            log.warn("doRebalance failed,param is illegal, group:{}, purpose:{}", group, purpose);
            return;
        }
        List<Session> sessionList = new ArrayList<>(sessionSet);
        Collections.shuffle(new ArrayList<>(sessionList));

        for (int i = 0; i < judge; i++) {
            String newProxyIp = eventMeshRecommendResult.get(i).split(":")[0];
            String newProxyPort = eventMeshRecommendResult.get(i).split(":")[1];
            String redirectSessionAddr = EventMeshTcp2Client.redirectClient2NewEventMesh(eventMeshTCPServer.getTcpThreadPoolGroup(), newProxyIp,
                Integer.parseInt(newProxyPort), sessionList.get(i), eventMeshTCPServer.getClientSessionGroupMapping());
            log.info("doRebalance,redirect sessionAddr:{}", redirectSessionAddr);
            ThreadUtils.sleep(eventMeshTCPServer.getEventMeshTCPConfiguration().getSleepIntervalInRebalanceRedirectMills(), TimeUnit.MILLISECONDS);
        }
        log.info("doRebalance redirect end---------------------group:{}", group);
    }

    private List<String> selectRedirectEventMesh(String group, Map<String, String> eventMeshMap,
        Map<String, Integer> clientDistributionMap, int judge,
        String eventMeshName) throws Exception {
        EventMeshRecommendStrategy eventMeshRecommendStrategy = new EventMeshRecommendImpl(eventMeshTCPServer);
        return eventMeshRecommendStrategy.calculateRedirectRecommendEventMesh(eventMeshMap, clientDistributionMap,
            group, judge, eventMeshName);
    }

    public int calculateRedirectNum(String eventMeshName, String group, String purpose, Map<String, Integer> clientDistributionMap) throws Exception {
        int sum = 0;
        for (Integer item : clientDistributionMap.values()) {
            sum += item;
        }
        int currentNum = 0;
        if (clientDistributionMap.get(eventMeshName) != null) {
            currentNum = clientDistributionMap.get(eventMeshName);
        }
        int avgNum = sum / clientDistributionMap.size();
        int modNum = sum % clientDistributionMap.size();

        List<String> eventMeshList = new ArrayList<>(clientDistributionMap.keySet());
        Collections.sort(eventMeshList);
        int index = -1;
        for (int i = 0; i < Math.min(modNum, eventMeshList.size()); i++) {
            if (StringUtils.equals(eventMeshName, eventMeshList.get(i))) {
                index = i;
                break;
            }
        }
        int rebalanceResult = 0;
        if (avgNum == 0) {
            rebalanceResult = 1;
        } else {
            rebalanceResult = (modNum != 0 && index < modNum && index >= 0) ? avgNum + 1 : avgNum;
        }
        log.info("rebalance caculateRedirectNum,group:{}, purpose:{},sum:{},avgNum:{},"
                +
                "modNum:{}, index:{}, currentNum:{}, rebalanceResult:{}", group, purpose, sum,
            avgNum, modNum, index, currentNum, rebalanceResult);
        return currentNum - rebalanceResult;
    }

    private Map<String, Integer> queryLocalEventMeshDistributeData(String cluster, String group, String purpose,
        Map<String, String> eventMeshMap) {
        Map<String, Integer> localEventMeshDistributeData = null;
        Map<String, Map<String, Integer>> eventMeshClientDistributionDataMap = null;
        try {
            eventMeshClientDistributionDataMap = eventMeshTCPServer.getMetaStorage().findEventMeshClientDistributionData(
                cluster, group, purpose);

            if (MapUtils.isEmpty(eventMeshClientDistributionDataMap)) {
                log.warn("doRebalance failed,found no distribute data in regitry, cluster:{}, group:{}, purpose:{}",
                    cluster, group, purpose);
                return Collections.emptyMap();
            }

            localEventMeshDistributeData = new HashMap<>();
            String localIdc = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC();
            for (Map.Entry<String, Map<String, Integer>> entry : eventMeshClientDistributionDataMap.entrySet()) {
                String idc = entry.getKey().split("-")[0];
                if (StringUtils.isNotBlank(idc) && StringUtils.equals(idc, localIdc)) {
                    localEventMeshDistributeData.put(entry.getKey(), entry.getValue().get(purpose));
                }
            }

            if (0 == localEventMeshDistributeData.size()) {
                log.warn("doRebalance failed,found no distribute data of localIDC in regitry,cluster:{},group:{}, purpose:{},localIDC:{}",
                    cluster, group, purpose, localIdc);
                return Collections.emptyMap();
            }

            log.info("before revert clientDistributionMap:{}, group:{}, purpose:{}", localEventMeshDistributeData,
                group, purpose);
            for (String eventMeshName : localEventMeshDistributeData.keySet()) {
                if (!eventMeshMap.containsKey(eventMeshName)) {
                    log.warn(
                        "doRebalance failed,exist eventMesh not register but exist in "
                            + "distributionMap,cluster:{},grpup:{},purpose:{},eventMeshName:{}",
                        cluster, group, purpose, eventMeshName);
                    return Collections.emptyMap();
                }
            }
            for (String eventMesh : eventMeshMap.keySet()) {
                if (!localEventMeshDistributeData.containsKey(eventMesh)) {
                    localEventMeshDistributeData.put(eventMesh, 0);
                }
            }
            log.info("after revert clientDistributionMap:{}, group:{}, purpose:{}", localEventMeshDistributeData,
                group, purpose);
        } catch (Exception e) {
            log.warn("doRebalance failed,cluster:{},group:{},purpose:{},findProxyClientDistributionData failed, errMsg:{}",
                cluster, group, purpose, e);
            return Collections.emptyMap();
        }

        return localEventMeshDistributeData;
    }
}
