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

import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.OPStatus;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;

import io.cloudevents.CloudEvent;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.Setter;

public class TcpRpcContext extends Context {	
	
	private static final Map<Command,Command>  COMMAND_MAP = new HashMap<>();
	
	static {
		COMMAND_MAP.put(Command.HEARTBEAT_REQUEST, Command.HEARTBEAT_RESPONSE);
		COMMAND_MAP.put(Command.HELLO_REQUEST, Command.HELLO_RESPONSE);
	}
	
	@Setter
	private Package pkg;
	

	@Override
	public ProtocolType protocolType() {
		return ProtocolType.TCP;
	}

	@Override
	protected void init() {
		this.requestCommand = pkg.getHeader().getCmd();
		this.responseCommand = COMMAND_MAP.get(this.requestCommand);
		this.identifying = pkg.getHeader().getCmd().name();
		this.header = pkg.getHeader().getProperties();		
		
		request.setHeaders(pkg.getHeader().getProperties());
		request.setSeq(pkg.getHeader().getSeq());
		Object body = pkg.getBody();
		if(body instanceof String) {
			request.setBodyString((String)body);
		}else if(body instanceof Object) {
			this.object = body;
		}else {
			request.setBody((byte[])body);
		}
		
	}

	@Override
	protected void send() {
		Header header = new Header();
		
		header.setSeq(this.request.getSeq());
		header.setCmd(this.requestCommand);
		OPStatus status = this.isSuccess ? OPStatus.SUCCESS : OPStatus.FAIL;
		header.setDesc(status.getDesc());
		header.setCode(status.getCode());
		if(Objects.nonNull(this.response.getHeaders())) {
			for( Entry<String, Object> e : this.response.getHeaders().entrySet()) {
				header.putProperty(e.getKey(), e.getValue());
			}
		}
		
		for(Entry<String,String> e : this.serviceContextData.entrySet()) {
			header.putProperty(e.getKey(), e.getValue());
		}
		
		Package packages = new Package();
		packages.setHeader(header);
		if(Objects.nonNull(response.getBody())) {
			packages.setBody(this.response.getBody());
		}
		
		ChannelFuture channelFuture = channle.writeAndFlush(packages);
		
		channelFuture.addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (future.isSuccess()) {
					asyncSendDridging.preHandler();
				} else {
					asyncSendDridging.error();
				}
			}
		});
		
	}

	@Override
	public String sessionId() {
		return this.channle.remoteAddress().toString();
	}

	@Override
	public CloudEvent getCloudEvent() {
		return this.getCloudEvent(pkg);
	}

}
