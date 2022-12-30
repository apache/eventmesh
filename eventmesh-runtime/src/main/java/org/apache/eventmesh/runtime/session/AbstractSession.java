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
package org.apache.eventmesh.runtime.session;

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.runtime.core.protocol.api.Metadata;
import org.apache.eventmesh.runtime.session.SessionSerivce.SubscribeInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.csp.sentinel.slots.statistic.metric.ArrayMetric;

import io.cloudevents.CloudEvent;
import lombok.Data;
import lombok.Setter;

@Data
public abstract class AbstractSession<T> implements Session{

	protected T channel;
		
    private long createTime = System.currentTimeMillis();

    private long lastHeartbeatTime = System.currentTimeMillis();

    private long isolateTime = 0;
    
    private SubscribeRequestBody subscribeRequestBody;
    
    private AtomicBoolean isClose = new AtomicBoolean(false);
    
    private Map<String , SubscriptionItem> subscriptionItemMap;
    
    private Map<String, SubscribeInfo> sessionByTopic ;
    
    
    /**
     * service
     * consumer
     * topic
     * connect
     */
    private List<ArrayMetric> arrayMetricList;
    
    private Map<String,Object> header;
    
    @Setter
    private Metadata metadata;
    
    
    
    protected SubscriptionItem getSubscriptionItem(String topic) {
    	return subscriptionItemMap.get(topic);
    }
    
    public void heartbeat() {
    	lastHeartbeatTime = System.currentTimeMillis();
    }
    
    
    public void close() {
    	this.isClose.compareAndSet(false, true);
    	this.unSubscribe();
    	
    }
    
    public boolean isClose() {
    	return this.isClose.get();
    }
    
    protected void before(CloudEvent event) {
    	for(ArrayMetric arrayMetric : arrayMetricList) {
    		arrayMetric.addPass(1);
    	}
    	sessionByTopic.get(event.getSubject()).getArrayMetric().addPass(1);
    }
    
    protected void after(CloudEvent event) {
    	for(ArrayMetric arrayMetric : arrayMetricList) {
    		arrayMetric.addSuccess(1);
    	}
    	sessionByTopic.get(event.getSubject()).getArrayMetric().addSuccess(1);
    }
    
    protected void exception(Throwable e,CloudEvent event) {
    	for(ArrayMetric arrayMetric : arrayMetricList) {
    		arrayMetric.addException(1);
    	}
    	sessionByTopic.get(event.getSubject()).getArrayMetric().addException(1);
    }
    
    public Metadata getMetadata() {
    	return this.metadata;
    }
    
    public void unSubscribe() {
    	this.subscribeRequestBody = null;
    	this.subscriptionItemMap = null;
    	this.sessionByTopic = null;
    }
}
