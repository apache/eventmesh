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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.eventmesh.api.AsyncConsumeContext;
import org.apache.eventmesh.api.EventListener;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshResponse;
import org.apache.eventmesh.runtime.session.DownstreamHandler;
import org.apache.eventmesh.runtime.session.Session;
import org.apache.eventmesh.runtime.session.SessionSerivce;

import java.util.List;
import java.util.Map;

import io.cloudevents.CloudEvent;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Slf4j(topic = "message")
public class EventMeshPushListener implements EventListener {

	private EventMeshEventCloudService eventMeshEventCloudService;

	private SessionSerivce sessionSerivce;

	private Map<String, Object> header;

	private String consumerGroup;

	private SubscriptionMode subscriptionMode;

	@Override
	public void consume(CloudEvent cloudEvent, AsyncConsumeContext context) {

		List<Session> sessionList = sessionSerivce.getSession(consumerGroup, cloudEvent.getSubject(), subscriptionMode);

		if (CollectionUtils.isEmpty(sessionList)) {
			log.warn("no session, topic is {} ");
			return;
		}
		for (Session session : sessionList) {
			CloudEvent newCloudEvent = eventMeshEventCloudService.record(cloudEvent, context);
			try {
				
				// sync  async
				session.downstreamMessage(this.header,newCloudEvent, new DownstreamHandler() {

					@Override
					public void success(EventMeshResponse eventMeshResponse) {
						eventMeshEventCloudService.consumeAck(newCloudEvent);
					}

					@Override
					public void fail(EventMeshResponse eventMeshResponse) {
						if (subscriptionMode == SubscriptionMode.CLUSTERING) {
							eventMeshEventCloudService.retry(newCloudEvent, context, EventMeshPushListener.this);
						}

					}

					@Override
					public void exception(Throwable e) {
						if (subscriptionMode == SubscriptionMode.CLUSTERING) {
							eventMeshEventCloudService.retry(newCloudEvent, context, EventMeshPushListener.this);
						}

					}

					@Override
					public void downstreamSuccess() {

					}
				});
			} catch (Exception e) {
				log.error(e.getMessage(), e);
				if (subscriptionMode == SubscriptionMode.CLUSTERING) {
					eventMeshEventCloudService.retry(newCloudEvent, context, this);
				}
			}
		}

	}

}
