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
package org.apache.eventmesh.runtime.core.protocol;

import org.apache.eventmesh.runtime.core.protocol.api.ProtocolType;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.data.MetricBucket;
import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;
import com.alibaba.csp.sentinel.slots.statistic.metric.BucketLeapArray;

import lombok.Getter;

/**
 * service 
 * protocol
 * Interface
 * @author hahaha
 *
 */
public class EventMeshMetricsService {

	@Getter
	private ArrayMetric serviceArrayMetric = createArrayMetric();
	
	private Map<ProtocolType , ArrayMetric> protocolArrayMetric = new HashMap<>();
	
	private Map<String, ArrayMetric> sessionArrayMetric = new ConcurrentHashMap<>();
	
	private Map<String, ArrayMetric>  interfacesArrayMetric = new HashMap<>(); 
	
	private Map<String,ArrayMetric> consumerGroupArrayMetric = new ConcurrentHashMap<>(); 
	
	{
		for(ProtocolType type : ProtocolType.values()) {
			protocolArrayMetric.put(type, createArrayMetric());
		}
	}
	
	public ArrayMetric getArrayMetricByInterfaces(String interfaces) {
		ArrayMetric arrayMetric = interfacesArrayMetric.computeIfAbsent(interfaces, key -> createArrayMetric());
		return arrayMetric;
		
	}
	
	public ArrayMetric getArrayMetricBySession(RpcContext context) {
		ArrayMetric arrayMetric = sessionArrayMetric.get(context.sessionId());
		if(Objects.isNull(arrayMetric)) {
			arrayMetric = sessionArrayMetric.computeIfAbsent(context.sessionId(), key -> createArrayMetric());
		}
		return arrayMetric;
	}
	
	public ArrayMetric getArrayMetricByConsumerGroup(String consumerGroup) {
		ArrayMetric arrayMetric = consumerGroupArrayMetric.get(consumerGroup);
		if(Objects.isNull(arrayMetric)) {
			arrayMetric = consumerGroupArrayMetric.computeIfAbsent(consumerGroup, key -> createArrayMetric());
		}
		return arrayMetric;
	}
	
	public ArrayMetric getArrayMetricByProtocol(ProtocolType protocolType) {
		return protocolArrayMetric.get(protocolType);
		
	}
	
	
	private ArrayMetric createArrayMetric() {
		LeapArray<MetricBucket> leapArray = new BucketLeapArray(60, 60 * 1000);
		return new ArrayMetric(leapArray);
	}
}
