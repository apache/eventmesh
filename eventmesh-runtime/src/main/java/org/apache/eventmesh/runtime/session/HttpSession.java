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

import org.apache.eventmesh.runtime.core.protocol.api.EventMeshRequest;
import org.apache.eventmesh.runtime.core.protocol.api.EventMeshResponse;

import java.net.InetSocketAddress;
import java.util.Map;

import io.cloudevents.CloudEvent;
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
					HttpSession.this.after(event);
				}
				
				@Override
				public void fail(EventMeshResponse eventMeshResponse) {
					downstreamHandler.fail(eventMeshResponse);
					HttpSession.this.after(event);
				}
				
				@Override
				public void exception(Throwable e) {
					try {
						downstreamHandler.exception(e);
						HttpSession.this.exception(e,event);
					}catch(Exception e1) {
						HttpSession.this.exception(e1,event);
					}
				}
				
				@Override
				public void downstreamSuccess() {
					
				}
			});
			
			DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.POST, "");
			ChannelFuture channelFuture = this.channel.writeAndFlush(request);
			this.before(event);
			channelFuture.addListener(new ChannelFutureListener() {
	
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if(future.isSuccess()) {
						try {
							downstreamHandler.downstreamSuccess();
							HttpSession.this.after(event);
						}catch(Exception e) {
							HttpSession.this.exception(e,event);
						}
					}else {
						try {
							downstreamHandler.exception(future.cause());
							HttpSession.this.exception(future.cause(),event);
						}catch(Exception e) {
							HttpSession.this.exception(e,event);
						}
					}
				}
			});
		}catch(Exception e) {
			this.exception(e,event);
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
