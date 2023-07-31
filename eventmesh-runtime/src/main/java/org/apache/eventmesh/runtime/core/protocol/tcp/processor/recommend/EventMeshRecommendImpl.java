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

package org.apache.eventmesh.runtime.core.protocol.tcp.processor.recommend;

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
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshRecommendImpl implements EventMeshRecommendStrategy {

    private static final int DEFAULT_PROXY_NUM = 1;

    private final transient EventMeshTCPServer eventMeshTCPServer;

    public EventMeshRecommendImpl(final EventMeshTCPServer eventMeshTCPServer) {
        this.eventMeshTCPServer = eventMeshTCPServer;
    }

    @Override
    public String calculateRecommendEventMesh(final String group, final String purpose) {
        List<EventMeshDataInfo> eventMeshDataInfoList;

        if (StringUtils.isAnyBlank(group, purpose)) {
            if (log.isWarnEnabled()) {
                log.warn("EventMeshRecommend failed,params illegal,group:{},purpose:{}", group, purpose);
            }
            return null;
        }

        final String cluster = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshCluster();
        try {
            eventMeshDataInfoList = eventMeshTCPServer.getRegistry().findEventMeshInfoByCluster(cluster);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("EventMeshRecommend failed, findEventMeshInfoByCluster failed, cluster:{}, group:{}, purpose:{}, errMsg:{}",
                    cluster, group, purpose, e);
            }
            return null;
        }

        if (CollectionUtils.isEmpty(eventMeshDataInfoList)) {
            if (log.isWarnEnabled()) {
                log.warn("EventMeshRecommend failed,not find eventMesh instances from registry,cluster:{},group:{},purpose:{}",
                    cluster, group, purpose);
            }
            return null;
        }

        final Map<String, String> localEventMeshMap = new HashMap<>();
        final Map<String, String> remoteEventMeshMap = new HashMap<>();
        final String localIdc = eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC();
        for (final EventMeshDataInfo eventMeshDataInfo : eventMeshDataInfoList) {
            String idc = eventMeshDataInfo.getEventMeshName().split("-")[0];
            if (StringUtils.isNotBlank(idc)) {
                final String dummy = StringUtils.equals(idc, localIdc)
                    ? localEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint())
                    : remoteEventMeshMap.put(eventMeshDataInfo.getEventMeshName(), eventMeshDataInfo.getEndpoint());
            } else {
                if (log.isErrorEnabled()) {
                    log.error("EventMeshName may be illegal,idc is null,eventMeshName:{}", eventMeshDataInfo.getEventMeshName());
                }
            }
        }

        if (MapUtils.isNotEmpty(localEventMeshMap)) {
            //recommend eventmesh of local idc
            return recommendProxyByDistributeData(cluster, group, purpose, localEventMeshMap, true);
        } else if (MapUtils.isNotEmpty(remoteEventMeshMap)) {
            //recommend eventmesh of other idc
            return recommendProxyByDistributeData(cluster, group, purpose, remoteEventMeshMap, false);
        } else {
            log.error("localEventMeshMap or remoteEventMeshMap size error");
            return null;
        }
    }

    @Override
    public List<String> calculateRedirectRecommendEventMesh(final Map<String, String> eventMeshMap,
        final Map<String, Integer> clientDistributedMap,
        final String group,
        final int recommendProxyNum,
        final String eventMeshName) throws Exception {
        Objects.requireNonNull(eventMeshMap, "eventMeshMap can not be null");
        Objects.requireNonNull(clientDistributedMap, "clientDistributedMap can not be null");

        if (recommendProxyNum < DEFAULT_PROXY_NUM || MapUtils.isEmpty(clientDistributedMap)) {
            return new ArrayList<String>();
        }

        if (log.isInfoEnabled()) {
            log.info("eventMeshMap:{},clientDistributionMap:{},group:{},recommendNum:{},currEventMeshName:{}",
                eventMeshMap, clientDistributedMap, group, recommendProxyNum, eventMeshName);
        }

        //find eventmesh with least client
        final List<Map.Entry<String, Integer>> clientDistributedList = new ArrayList<>();
        final ValueComparator vc = new ValueComparator();
        clientDistributedMap.entrySet().forEach(clientDistributedList::add);
        Collections.sort(clientDistributedList, vc);

        if (log.isInfoEnabled()) {
            log.info("clientDistributedLists after sort:{}", clientDistributedList);
        }

        final List<String> recommendProxyList = new ArrayList<>(recommendProxyNum);
        while (recommendProxyList.size() < recommendProxyNum) {
            final Map.Entry<String, Integer> minProxyItem = clientDistributedList.get(0);
            final int currProxyNum = clientDistributedMap.get(eventMeshName);
            recommendProxyList.add(eventMeshMap.get(minProxyItem.getKey()));
            clientDistributedMap.put(minProxyItem.getKey(), minProxyItem.getValue() + 1);
            clientDistributedMap.put(eventMeshName, currProxyNum - 1);
            Collections.sort(clientDistributedList, vc);
            if (log.isInfoEnabled()) {
                log.info("clientDistributedList after sort:{}", clientDistributedList);
            }
        }

        if (log.isInfoEnabled()) {
            log.info("choose proxys with min instance num, group:{}, recommendProxyNum:{}, recommendProxyList:{}",
                group, recommendProxyNum, recommendProxyList);
        }
        return recommendProxyList;
    }

    private String recommendProxyByDistributeData(final String cluster, final String group, final String purpose,
        final Map<String, String> eventMeshMap, final boolean caculateLocal) {
        Objects.requireNonNull(eventMeshMap, "eventMeshMap can not be null");

        if (log.isInfoEnabled()) {
            log.info("eventMeshMap:{},cluster:{},group:{},purpose:{},caculateLocal:{}", eventMeshMap, cluster,
                group, purpose, caculateLocal);
        }

        Map<String, Map<String, Integer>> eventMeshClientDistributionDataMap = null;
        try {
            eventMeshClientDistributionDataMap = eventMeshTCPServer.getRegistry().findEventMeshClientDistributionData(
                cluster, group, purpose);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("EventMeshRecommend failed,findEventMeshClientDistributionData failed,"
                    + "cluster:{},group:{},purpose:{}, errMsg:{}", cluster, group, purpose, e);
            }
        }

        String recommendProxyAddr;
        if (MapUtils.isEmpty(eventMeshClientDistributionDataMap)) {
            final List<String> tmpProxyAddrList = new ArrayList<>(eventMeshMap.values());
            if (CollectionUtils.isEmpty(tmpProxyAddrList)) {
                return null;
            }

            Collections.shuffle(tmpProxyAddrList);
            recommendProxyAddr = tmpProxyAddrList.get(0);
            if (log.isInfoEnabled()) {
                log.info("No distribute data in registry,cluster:{}, group:{},purpose:{}, recommendProxyAddr:{}",
                    cluster, group, purpose, recommendProxyAddr);
            }
            return recommendProxyAddr;
        }

        final Map<String, Integer> localClientDistributionMap = new HashMap<>();
        final Map<String, Integer> remoteClientDistributionMap = new HashMap<>();

        eventMeshClientDistributionDataMap.entrySet().forEach(entry -> {
            final String idc = entry.getKey().split("-")[0];
            if (StringUtils.isNotBlank(idc)) {
                if (StringUtils.equals(idc, eventMeshTCPServer.getEventMeshTCPConfiguration().getEventMeshIDC())) {
                    localClientDistributionMap.put(entry.getKey(), entry.getValue().get(purpose));
                } else {
                    remoteClientDistributionMap.put(entry.getKey(), entry.getValue().get(purpose));
                }
            } else {
                if (log.isErrorEnabled()) {
                    log.error("eventMeshName may be illegal,idc is null,eventMeshName:{}", entry.getKey());
                }
            }
        });

        recommendProxyAddr = recommendProxy(eventMeshMap, (caculateLocal == true) ? localClientDistributionMap
            : remoteClientDistributionMap, group);

        if (log.isInfoEnabled()) {
            log.info("eventMeshMap:{},group:{},purpose:{},caculateLocal:{},recommendProxyAddr:{}", eventMeshMap,
                group, purpose, caculateLocal, recommendProxyAddr);
        }

        return recommendProxyAddr;
    }

    private String recommendProxy(final Map<String, String> eventMeshMap,
        final Map<String, Integer> clientDistributionMap,
        final String group) {
        Objects.requireNonNull(eventMeshMap, "eventMeshMap can not be null");
        Objects.requireNonNull(clientDistributionMap, "clientDistributionMap can not be null");

        if (log.isInfoEnabled()) {
            log.info("eventMeshMap:{},clientDistributionMap:{},group:{}", eventMeshMap, clientDistributionMap, group);
        }

        if (!eventMeshMap.keySet().containsAll(clientDistributionMap.keySet())) {
            if (log.isWarnEnabled()) {
                log.warn("exist proxy not register but exist in distributionMap");
            }
            return null;
        }

        eventMeshMap.keySet().forEach(proxy -> clientDistributionMap.putIfAbsent(proxy, 0));

        //select the eventmesh with least instances
        if (MapUtils.isEmpty(clientDistributionMap)) {
            if (log.isErrorEnabled()) {
                log.error("no legal distribute data,check eventMeshMap and distributeData, group:{}", group);
            }
            return null;
        } else {
            final List<Map.Entry<String, Integer>> list = new ArrayList<>();
            clientDistributionMap.entrySet().forEach(list::add);
            Collections.sort(list, new ValueComparator());
            if (log.isInfoEnabled()) {
                log.info("clientDistributionMap after sort:{}", list);
            }
            return eventMeshMap.get(list.get(0).getKey());
        }
    }

}
