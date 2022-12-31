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

import org.apache.eventmesh.runtime.core.protocol.api.EventMeshProtocolHandler;

import java.util.concurrent.ThreadPoolExecutor;

import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;

import lombok.Data;


@Data
public class EventMeshHandlerInfo {

	private boolean traceEnabled;
	
	private EventMeshProtocolHandler EventMeshProtocolHandler;
	
	private Class<?> clazz;
	
	private boolean isAsync;
	
	private ThreadPoolExecutor threadPoolExecutor;
	
	private ArrayMetric arrayMetric;
	
	private RateLimiterWrapper rateLimiterWrapper;
}
