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

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.common.SimpleMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer.EventMeshServerInterceptor;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.api.Metadata;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolType;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import lombok.Setter;

public class GrpcRpcContext extends Context {

	
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static GrpcRpcContext createGrpcRpcContext(String identifying,StreamObserver emitter,Object object,RequestHeader requestHeader) {
		GrpcRpcContext rpcContext = new GrpcRpcContext();
    	rpcContext.setIdentifying(identifying);
    	rpcContext.setEmitter(emitter);
    	rpcContext.setChannle(EventMeshServerInterceptor.THREAD_LOCAL.get().getChannel());
    	rpcContext.setObject(object);
    	Metadata metadata = new Metadata();
    	metadata.setEnv(requestHeader.getEnv());
    	metadata.setIdc(requestHeader.getIdc());
    	metadata.setIp(requestHeader.getIp());
    	metadata.setPid(requestHeader.getPid());
    	metadata.setSys(requestHeader.getSys());
    	metadata.setUsername(requestHeader.getUsername());
    	metadata.setPasswd(requestHeader.getPassword());
    	metadata.setLanguage(requestHeader.getLanguage());
    	metadata.setProtocolType(requestHeader.getProtocolType());
    	metadata.setProtocolDesc(requestHeader.getProtocolDesc());
    	metadata.setProtocolVersion(requestHeader.getProtocolVersion());
    	rpcContext.setMetadata(metadata);
    	return rpcContext;
	}
	
	@Getter
	@Setter
	private StreamObserver<Object> emitter;

	@Override
	public ProtocolType protocolType() {
		return ProtocolType.GRPC;
	}

	@Override
	protected void init() {

	}

	@Override
	protected void send() {
		try {

			if (this.response.getBodyObject() instanceof CloudEvent) {
				ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory
						.getProtocolAdaptor("");
				SimpleMessageWrapper wrapper = (SimpleMessageWrapper) grpcCommandProtocolAdaptor
						.fromCloudEvent((CloudEvent) this.response.getBodyObject());
				emitter.onNext(wrapper.getMessage());
			} else {
				StatusCode statusCode = this.isSuccess ? StatusCode.SUCCESS : StatusCode.OVERLOAD;
				String message;
				if (Objects.nonNull(this.throwable)) {
					message = statusCode.getErrMsg() + EventMeshConstants.BLANK_SPACE
							+ EventMeshUtil.stackTrace(this.throwable, 2);
				} else {
					message = statusCode.getErrMsg();
				}

				Response response = Response.newBuilder().setRespCode(statusCode.getRetCode()).setRespMsg(message)
						.setRespTime(String.valueOf(System.currentTimeMillis())).build();

				emitter.onNext(response);
			}
			this.asyncSendDridging.preHandler();
		} catch (Exception e) {
			this.asyncSendDridging.error();
		}
	}

	public void setIdentifying(String identifying) {
		this.identifying = identifying;
	}
	
	@Override
	public String sessionId() {
		return this.channle.remoteAddress().toString();
	}
	
	public void setObject(Object object) {
		this.object = object;
	}

	@Override
	public CloudEvent getCloudEvent() {
		return this.getCloudEvent(new SimpleMessageWrapper((SimpleMessage)this.object));
	}

}
