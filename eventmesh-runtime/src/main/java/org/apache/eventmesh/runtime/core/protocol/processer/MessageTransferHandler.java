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

package org.apache.eventmesh.runtime.core.protocol.processer;

import org.apache.eventmesh.api.RequestReplyCallback;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.EventMeshEventCloudConfig;
import org.apache.eventmesh.runtime.core.EventMeshEventCloudService;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshProtocolHandler;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshRequest;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolIdentifying;
import org.apache.eventmesh.runtime.core.protocol.api.RpcContext;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import lombok.Setter;

@ProtocolIdentifying(tcp = { "REQUEST_TO_SERVER", "RESPONSE_TO_SERVER", "ASYNC_MESSAGE_TO_SERVER",
		"BROADCAST_MESSAGE_TO_SERVER" },http= {"101","104","105","106"})
public class MessageTransferHandler implements EventMeshProtocolHandler {

	@Setter
	private EventMeshEventCloudService eventMeshEventCloudService;

	@Setter
	private EventMeshEventCloudConfig eventMeshEventCloudConfig;

	@Override
	public void handler(EventMeshRequest eventMeshRequest, RpcContext rpcContext) throws Exception {
		CloudEvent event = rpcContext.getCloudEvent();
		String content = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
		if (content.length() > eventMeshEventCloudConfig.getEventMeshEventSize()) {
			throw new Exception("event size exceeds the limit: " + eventMeshEventCloudConfig.getEventMeshEventSize());
		}

		event = addTimestamp(rpcContext,event, rpcContext.identifying(), rpcContext.getRequestTime());

		if (rpcContext.identifying().equals("REQUEST_TO_SERVER") || rpcContext.identifying().equals("101")  ) {
			long timeout = EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
			if (event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL) != null) {
				timeout = Long.parseLong(
						(String) Objects.requireNonNull(event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL)));
			}
			MessageRequestReplyCallback messageRequestReplyCallback = new MessageRequestReplyCallback();
			messageRequestReplyCallback.event = event;
			messageRequestReplyCallback.rpcContext = rpcContext;
			eventMeshEventCloudService.request(event, rpcContext, messageRequestReplyCallback, timeout);
		} else if (rpcContext.identifying().equals("RESPONSE_TO_SERVER") ||rpcContext.identifying().equals("104") ||rpcContext.identifying().equals("ASYNC_MESSAGE_TO_SERVER")) {
			MessageSendCallback messageSendCallback = new MessageSendCallback();
			messageSendCallback.event = event;
			messageSendCallback.rpcContext = rpcContext;
			eventMeshEventCloudService.send(event, messageSendCallback);
		}
	}

	private CloudEvent addTimestamp(RpcContext rpcContext,CloudEvent event, String cmd, long sendTime) {
		if (cmd.equals("RESPONSE_TO_SERVER")) {
			event = CloudEventBuilder.from(event)
					.withExtension(EventMeshConstants.RSP_C2EVENTMESH_TIMESTAMP, String.valueOf(rpcContext.getRequestTime()))
					.withExtension(EventMeshConstants.RSP_EVENTMESH2MQ_TIMESTAMP, String.valueOf(sendTime))
					.withExtension(EventMeshConstants.RSP_SEND_EVENTMESH_IP,
							rpcContext.getServiceContextData().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP))
					.build();
		} else {
			event = CloudEventBuilder.from(event)
					.withExtension(EventMeshConstants.REQ_C2EVENTMESH_TIMESTAMP, String.valueOf(rpcContext.getRequestTime()))
					.withExtension(EventMeshConstants.REQ_EVENTMESH2MQ_TIMESTAMP, String.valueOf(sendTime))
					.withExtension(EventMeshConstants.REQ_SEND_EVENTMESH_IP,
							rpcContext.getServiceContextData().get(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP))
					.build();
		}
		return event;
	}

	private class MessageRequestReplyCallback implements RequestReplyCallback {

		private RpcContext rpcContext;

		private CloudEvent event;

		@Override
		public void onSuccess(CloudEvent event) {
			 this.event = CloudEventBuilder.from(event)
                     .withExtension(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP,
                             String.valueOf(System.currentTimeMillis()))
                     .withExtension(EventMeshConstants.RSP_MQ2EVENTMESH_TIMESTAMP,
                             String.valueOf(System.currentTimeMillis()))
                     .build();
			 rpcContext.sendResponse(null , this.event);
		}

		@Override
		public void onException(Throwable e) {
			rpcContext.sendErrorResponse(e);
		}

	}

	@Setter
	private class MessageSendCallback implements SendCallback {

		private RpcContext rpcContext;

		private CloudEvent event;

		@Override
		public void onSuccess(SendResult sendResult) {
			 event = CloudEventBuilder.from(event)
                     .withExtension(EventMeshConstants.RSP_EVENTMESH2C_TIMESTAMP,
                             String.valueOf(System.currentTimeMillis()))
                     .withExtension(EventMeshConstants.RSP_MQ2EVENTMESH_TIMESTAMP,
                             String.valueOf(System.currentTimeMillis()))
                     .build();
			
			 rpcContext.sendResponse(null , event);
		}

		@Override
		public void onException(OnExceptionContext context) {
			rpcContext.sendErrorResponse(context.getException());
		}
	}
}
