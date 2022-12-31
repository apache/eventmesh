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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshRequest;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshResponse;

import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

public class TcpSession extends AbstractSession<Channel> {

	@Override
	public void downstreamMessage(Map<String, Object> header, CloudEvent event, DownstreamHandler downstreamHandler) {
		try {
			Package package1 = this.createPackage(event);
			ChannelFuture channelFuture = this.channel.writeAndFlush(package1);
			this.before(event);
			channelFuture.addListener(new ChannelFutureListener() {

				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						try {
							downstreamHandler.downstreamSuccess();
							TcpSession.this.after(event);
						} catch (Exception e) {
							TcpSession.this.exception(e,event);
						}
					} else {
						try {
							downstreamHandler.exception(future.cause());
							TcpSession.this.exception(future.cause(),event);
						} catch (Exception e) {
							TcpSession.this.exception(e,event);
						}
					}
				}
			});
		} catch (Exception e) {
			this.exception(e,event);
		}

	}

	private Package createPackage(CloudEvent event) throws ProtocolHandleException {
		SubscriptionItem subscriptionItem = this.getSubscriptionItem(event.getSubject());
		Command cmd;
		if (SubscriptionMode.BROADCASTING.equals(subscriptionItem.getMode())) {
			cmd = Command.BROADCAST_MESSAGE_TO_CLIENT;
		} else if (SubscriptionType.SYNC.equals(subscriptionItem.getType())) {
			cmd = Command.REQUEST_TO_CLIENT;
		} else {
			cmd = Command.ASYNC_MESSAGE_TO_CLIENT;
		}

		String protocolType = Objects.requireNonNull(event.getExtension(Constants.PROTOCOL_TYPE)).toString();
		ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
		event = CloudEventBuilder.from(event)
				.withExtension(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()))
				.build();
		String seq = event.getExtension("cloudeventid").toString();
		Package	pkg = (Package) protocolAdaptor.fromCloudEvent(event);
			pkg.setHeader(new Header(cmd, OPStatus.SUCCESS.getCode(), null, seq));
			pkg.getHeader().putProperty(Constants.PROTOCOL_TYPE, protocolType);
		return pkg;
	}

	@Override
	public void downstreamMessage(EventMeshRequest request, DownstreamHandler downstreamHandler) {
		// TODO Auto-generated method stub

	}

	@Override
	public void downstream(EventMeshResponse response, DownstreamHandler downstreamHandler) {
		// TODO Auto-generated method stub

	}

}
