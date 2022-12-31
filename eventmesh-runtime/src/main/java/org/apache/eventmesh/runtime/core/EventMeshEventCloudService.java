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

import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.api.EventMeshAsyncConsumeContext;
import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.core.EventMeshEventCloudConfig.RemoveAfter;
import org.apache.eventmesh.runtime.core.plugin.MQProducerWrapper;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "message")
public class EventMeshEventCloudService {

	private static final String CLOUD_EVENT_ID_KEY = "cloudeventid";

	private EventMeshEventCloudConfig eventMeshEventCloudConfig;

	@Setter
	private EventMeshConsumerManager eventMeshConsumerManager;

	private Map<Long, CloudEventInfo> cloudEventInfoMap = new ConcurrentHashMap<>();

	private AtomicLong cloudEventInfoID = new AtomicLong();

	private MQProducerWrapper mqProducerWrapper;

	public EventMeshEventCloudService(EventMeshEventCloudConfig eventMeshEventCloudConfig) throws Exception {
		this.eventMeshEventCloudConfig = eventMeshEventCloudConfig;
		mqProducerWrapper = new MQProducerWrapper(eventMeshEventCloudConfig.getConnectorPluginType());
		mqProducerWrapper.init(null);
		mqProducerWrapper.start();
	}

	public void send(CloudEvent cloudEvent, SendCallback sendCallback) throws Exception {
		mqProducerWrapper.send(cloudEvent, sendCallback);
	}

	public boolean reply(final CloudEvent cloudEvent, final SendCallback sendCallback) throws Exception {
		return mqProducerWrapper.reply(cloudEvent, sendCallback);
	}

	public void request(CloudEvent cloudEvent, RpcContext rpcContext, RequestReplyCallback rrCallback, long timeout)
			throws Exception {
		mqProducerWrapper.request(cloudEvent, rrCallback, timeout);
	}

	public CloudEvent record(CloudEvent cloudEvent, AsyncConsumeContext context) {
		Long cloudEventKey = cloudEventInfoID.incrementAndGet();
		CloudEventInfo cloudEventInfo = new CloudEventInfo();
		cloudEvent = CloudEventBuilder.from(cloudEvent).withExtension(CLOUD_EVENT_ID_KEY, cloudEventKey.toString())
				.build();
		cloudEventInfo.cloudEvent = cloudEvent;
		cloudEventInfo.context = context;
		cloudEventInfoMap.put(cloudEventKey, cloudEventInfo);
		return cloudEvent;
	}

	public boolean consumeAck(String key) {
		CloudEventInfo cloudEventInfo = cloudEventInfoMap.remove(Long.valueOf(key));
		if (Objects.isNull(cloudEventInfo)) {
			log.warn("key {} is not find CloudEventInfo", key);
			return false;
		}
		List<CloudEvent> cloudEventList = new ArrayList<>();
		cloudEventList.add(cloudEventInfo.cloudEvent);
		EventMeshAsyncConsumeContext eventMeshConsumeConcurrentlyContext = (EventMeshAsyncConsumeContext) cloudEventInfo.context;
		return eventMeshConsumerManager.updateOffset(cloudEventInfo.groupName, cloudEventInfo.subscriptionMode,
				cloudEventList, eventMeshConsumeConcurrentlyContext.getAbstractContext());
	}

	public boolean consumeAck(CloudEvent cloudEvent) {
		return this.consumeAck(cloudEvent.getExtension(CLOUD_EVENT_ID_KEY).toString());
	}

	public void removeCloudInfo(CloudEvent cloudEvent) {
		CloudEventInfo cloudEventInfo = cloudEventInfoMap
				.remove(Long.valueOf(cloudEvent.getExtension(CLOUD_EVENT_ID_KEY).toString()));
		if(Objects.isNull(cloudEventInfo)) {
			log.warn("warn cloudEvent not existence ， CLOUD_EVENT_ID_KEY is {}",cloudEvent.getExtension(CLOUD_EVENT_ID_KEY).toString());
			return;
		}
		
		if (RemoveAfter.REDO == this.eventMeshEventCloudConfig.getRemoveAfter()) {
			try {
				mqProducerWrapper.send(cloudEventInfo.cloudEvent, new SendCallback() {

					@Override
					public void onSuccess(SendResult sendResult) {
						log.warn("message redo succes");
					}

					@Override
					public void onException(OnExceptionContext context) {
						log.warn("message redo Exception");
					}
				});
			} catch (Exception e) {
				log.error(e.getMessage(), e);
			}
		}
	}

	public void retry(CloudEvent cloudEvent, AsyncConsumeContext context, EventListener eventListener) {

		if (!this.eventMeshEventCloudConfig.isRetry()) {
			this.removeCloudInfo(cloudEvent);
			return;
		}

		Long cloudEventKey = (Long) Long.valueOf(cloudEvent.getExtension(CLOUD_EVENT_ID_KEY).toString());
		CloudEventInfo cloudEventInfo = cloudEventInfoMap.get(cloudEventKey);
		cloudEventInfo.context = context;
		cloudEventInfo.cloudEvent = cloudEvent;
		cloudEventInfo.eventListener = eventListener;
		cloudEventInfo.retry = eventMeshEventCloudConfig.getRetryNum();
		cloudEventInfo.retryTotalTime = System.currentTimeMillis() + eventMeshEventCloudConfig.getRetryTotalTime();
		cloudEventInfo.intervalTime = eventMeshEventCloudConfig.getIntervalTime();
		cloudEventInfo.isRetry = true;
		cloudEventInfo.retry = cloudEventInfo.retry - 1;
		if (cloudEventInfo.retry <= 0 || cloudEventInfo.retryTotalTime > System.currentTimeMillis()) {
			this.removeCloudInfo(cloudEvent);
			return;
		}
	
	}

	private static class RetryRunnable implements Runnable {

		private CloudEventInfo retryInfo;

		@Override
		public void run() {
			retryInfo.eventListener.consume(retryInfo.cloudEvent, retryInfo.context);
		}

	}

	@Getter
	@Setter(value = AccessLevel.PROTECTED)
	protected static class CloudEventInfo {

		private volatile boolean isRetry = false;

		private String groupName;

		private SubscriptionMode subscriptionMode;

		private int retry;

		private long retryTotalTime = 2000;

		private long intervalTime = 500;

		private CloudEvent cloudEvent;

		private AsyncConsumeContext context;

		private EventListener eventListener;
	}
}
