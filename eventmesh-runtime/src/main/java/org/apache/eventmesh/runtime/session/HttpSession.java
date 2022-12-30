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

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshRequest;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshResponse;
import org.apache.eventmesh.runtime.core.protocol.http.push.AsyncHTTPPushRequest;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.WebhookUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;

public class HttpSession extends AbstractSession<Channel>{

	private HttpClient httpClient;
	
	private InetSocketAddress inetSocketAddress;
	
	@Override
	public void downstreamMessage(Map<String, Object> header, CloudEvent event, DownstreamHandler downstreamHandler) {
		try {
			
			httpClient.connect(inetSocketAddress, new DownstreamHandler() {
				
				@Override
				public void success(EventMeshResponse eventMeshResponse) {
					downstreamHandler.success(eventMeshResponse);
					HttpSession.this.after();
				}
				
				@Override
				public void fail(EventMeshResponse eventMeshResponse) {
					downstreamHandler.fail(eventMeshResponse);
					HttpSession.this.after();
				}
				
				@Override
				public void exception(Throwable e) {
					try {
						downstreamHandler.exception(e);
						HttpSession.this.exception(e);
					}catch(Exception e1) {
						HttpSession.this.exception(e1);
					}
				}
				
				@Override
				public void downstreamSuccess() {
					
				}
			});
			
			DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST, "");
			ChannelFuture channelFuture = this.channel.writeAndFlush(request);
			this.before();
			channelFuture.addListener(new ChannelFutureListener() {
	
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if(future.isSuccess()) {
						try {
							downstreamHandler.downstreamSuccess();
							HttpSession.this.after();
						}catch(Exception e) {
							HttpSession.this.exception(e);
						}
					}else {
						try {
							downstreamHandler.exception(future.cause());
							HttpSession.this.exception(future.cause());
						}catch(Exception e) {
							HttpSession.this.exception(e);
						}
					}
				}
			});
		}catch(Exception e) {
			this.exception(e);
		}
		
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
