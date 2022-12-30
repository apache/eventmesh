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
package org.apache.eventmesh.runtime.core;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.http.body.client.SubscribeRequestBody;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;
import org.apache.eventmesh.runtime.session.Session;
import org.apache.eventmesh.runtime.session.SessionSerivce;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import io.cloudevents.CloudEvent;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "message")
public class EventMeshConsumerManager {

	@Setter
	private EventMeshEventCloudService eventMeshEventCloudService;
	
	@Setter
	private EventMeshEventCloudConfig eventMeshEventCloudConfig;

	@Setter
	private SessionSerivce sessionSerivce;

	private Map<String, Object> header;

	private Map<String/* consumer group */ , ConsumerManagerWrapper> consumerMap = new ConcurrentHashMap<>();

	public boolean updateOffset(String consumerGroup, SubscriptionMode subscriptionMode, List<CloudEvent> events,
			AbstractContext abstractContext) {
		ConsumerManagerWrapper consumerManagerWrapper = consumerMap.get(consumerGroup);
		if (Objects.isNull(consumerManagerWrapper)) {
			return false;
		}
		MQConsumerWrapper consumerWrapper = this.getConsumer(subscriptionMode, consumerManagerWrapper);
		if(Objects.isNull(consumerWrapper)) {
			return false;
		}
		consumerWrapper.updateOffset(events, abstractContext);
		return true;
	}
	
	private MQConsumerWrapper getConsumer(SubscriptionMode subscriptionMode , ConsumerManagerWrapper consumerManagerWrapper) {
		
		return Objects.equals(subscriptionMode, SubscriptionMode.CLUSTERING)
				? consumerManagerWrapper.persistentMqConsumer
				: consumerManagerWrapper.broadcastMqConsumer;
	}

	public void subscribe(RpcContext context, SubscribeRequestBody subscribeRequestBody) throws Exception {
		
		if(Objects.isNull(subscribeRequestBody.getConsumerGroup())) {
			Session session = sessionSerivce.getSession(context);
			subscribeRequestBody.setConsumerGroup(session.getMetadata().getConsumerGroup());
		}
		
		ConsumerManagerWrapper consumerManagerWrapper = consumerMap
				.computeIfAbsent(subscribeRequestBody.getConsumerGroup(), key -> new ConsumerManagerWrapper());
		Map<String,MQConsumerWrapper> topicAndConsumerMap = new HashMap<>();
		try {
			for (SubscriptionItem subscriptionItem : subscribeRequestBody.getTopics()) {
				MQConsumerWrapper consumerWrapper = this.getConsumer(subscriptionItem.getMode(), consumerManagerWrapper);
				if (Objects.isNull(consumerWrapper)) {
					synchronized (consumerManagerWrapper) {
						consumerWrapper = this.getConsumer(subscriptionItem.getMode(), consumerManagerWrapper);
						if (Objects.nonNull(consumerWrapper)) {
							continue;
						}
						consumerWrapper = createMQConsumerWrapper(subscribeRequestBody, subscriptionItem);
						if (Objects.equals(subscriptionItem.getMode(), SubscriptionMode.CLUSTERING)) {
							consumerManagerWrapper.persistentMqConsumer = consumerWrapper;
						} else {
							consumerManagerWrapper.broadcastMqConsumer = consumerWrapper;
						}
					}
				}
				consumerWrapper.subscribe(subscriptionItem.getTopic());
				topicAndConsumerMap.put(subscriptionItem.getTopic(),consumerWrapper);
			}
			sessionSerivce.subscribe(context, subscribeRequestBody);
		}catch(Exception e) {
			log.error(e.getMessage(),e);
			for(Entry<String,MQConsumerWrapper> ens :topicAndConsumerMap.entrySet()) {
				try {
					ens.getValue().unsubscribe(ens.getKey());
				}catch(Exception e1) {
					log.error(e.getMessage(),e);
				}
			}
			
		}
	}

	public void unSubscribe(RpcContext context) {

	}

	private MQConsumerWrapper createMQConsumerWrapper(SubscribeRequestBody subscribeRequestBody,
			SubscriptionItem subscriptionItem) throws Exception {
		MQConsumerWrapper consumerWrapper = new MQConsumerWrapper(eventMeshEventCloudConfig.getConnectorPluginType());
		Properties keyValue = new Properties();
		keyValue.put("consumerGroup", subscribeRequestBody.getConsumerGroup());
		keyValue.put("instanceName", subscribeRequestBody.getConsumerGroup());
		keyValue.put("isBroadcast", Objects.equals(subscriptionItem.getMode(), SubscriptionMode.BROADCASTING)?"true":"false");
		consumerWrapper.init(keyValue);
		
		EventMeshPushListener eventMeshPushListener = new EventMeshPushListener();
		eventMeshPushListener.setHeader(header);
		eventMeshPushListener.setEventMeshEventCloudService(eventMeshEventCloudService);
		eventMeshPushListener.setSessionSerivce(sessionSerivce);
		eventMeshPushListener.setSubscriptionMode(subscriptionItem.getMode());
		eventMeshPushListener.setConsumerGroup(subscribeRequestBody.getConsumerGroup());
		consumerWrapper.registerEventListener(eventMeshPushListener);
		consumerWrapper.start();
		return consumerWrapper;
	}

	@Setter(value = AccessLevel.PROTECTED)
	protected static class ConsumerManagerWrapper {

		private String consumerGroup;

		private MQConsumerWrapper persistentMqConsumer;

		private MQConsumerWrapper broadcastMqConsumer;

	}

}
