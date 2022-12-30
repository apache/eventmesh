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
package org.apache.eventmesh.runtime.core.protocol.context;

import org.apache.eventmesh.runtime.core.protocol.AsyncSendDridging;
import org.apache.eventmesh.runtime.core.protocol.EventMeshTraceService.TraceOperation;
import org.apache.eventmesh.runtime.core.protocol.api.Metadata;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;

import java.util.Map;

import org.slf4j.Logger;

import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;

import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Setter;

public abstract class Context extends RpcContext {
	
	
	
	@Setter
	private Logger logger;
	
	@Setter
	private ArrayMetric arrayMetric;


	
	protected Map<String, Object> header;
	
	@Getter
	@Setter
	protected Channel channle;
	
	@Setter
	protected AsyncSendDridging asyncSendDridging;
	
	@Setter
	private TraceOperation traceOperation;
	
	
	@Setter
	protected Class<?> clazz;

	protected abstract void init();
	
	public void doInit() {
		this.init();
	}
	
	public void setServiceContextData(Map<String,String> serviceContextData) {
		this.serviceContextData = serviceContextData;
	}
	
	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}
	
}
