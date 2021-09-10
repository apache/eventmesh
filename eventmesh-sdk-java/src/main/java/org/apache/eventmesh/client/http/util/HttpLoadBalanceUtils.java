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

package org.apache.eventmesh.client.http.util;

import com.google.common.base.Splitter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.loadbalance.LoadBalanceSelector;
import org.apache.eventmesh.common.loadbalance.RandomLoadBalanceSelector;
import org.apache.eventmesh.common.loadbalance.Weight;
import org.apache.eventmesh.common.loadbalance.WeightRoundRobinLoadBalanceSelector;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class HttpLoadBalanceUtils {

    private static final Pattern IP_PORT_PATTERN = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5}");
    private static final Pattern IP_PORT_WEIGHT_PATTERN = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{4,5}:\\d{1,6}");

    public static LoadBalanceSelector<String> createEventMeshServerLoadBalanceSelector(LiteClientConfig liteClientConfig)
            throws EventMeshException {
        List<String> eventMeshAddrs = Splitter.on(";").splitToList(liteClientConfig.getLiteEventMeshAddr());
        if (CollectionUtils.isEmpty(eventMeshAddrs)) {
            throw new EventMeshException("liteEventMeshAddr can not be empty");
        }

        LoadBalanceSelector<String> eventMeshServerSelector = null;
        switch (liteClientConfig.getLoadBalanceType()) {
            case RANDOM:
                List<String> eventMeshAddrList = new LinkedList<>();
                for (String eventMeshAddr : eventMeshAddrs) {
                    if (!IP_PORT_PATTERN.matcher(eventMeshAddr).matches()) {
                        throw new EventMeshException(
                                String.format("liteEventMeshAddr:%s is not illegal", liteClientConfig.getLiteEventMeshAddr()));
                    }
                    eventMeshAddrList.add(eventMeshAddr);
                }
                eventMeshServerSelector = new RandomLoadBalanceSelector<>(eventMeshAddrList);
                break;
            case WEIGHT_ROUND_ROBIN:
                List<Weight<String>> eventMeshAddrWeightList = new LinkedList<>();
                for (String eventMeshAddrWight : eventMeshAddrs) {
                    if (!IP_PORT_WEIGHT_PATTERN.matcher(eventMeshAddrWight).matches()) {
                        throw new EventMeshException(
                                String.format("liteEventMeshAddr:%s is not illegal", liteClientConfig.getLiteEventMeshAddr()));
                    }
                    int splitIndex = eventMeshAddrWight.lastIndexOf(":");
                    Weight<String> weight = new Weight<>(
                            eventMeshAddrWight.substring(0, splitIndex),
                            Integer.parseInt(eventMeshAddrWight.substring(splitIndex + 1))
                    );
                    eventMeshAddrWeightList.add(weight);
                }
                eventMeshServerSelector = new WeightRoundRobinLoadBalanceSelector<>(eventMeshAddrWeightList);
                break;
            default:
                // ignore
        }
        if (eventMeshServerSelector == null) {
            throw new EventMeshException("liteEventMeshAddr param illegal,please check");
        }
        return eventMeshServerSelector;
    }

}
