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
package org.apache.eventmesh.runtime.core.protocol.api;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.tcp.Command;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import lombok.Getter;

public abstract class RpcContext {

	protected Metadata metadata;

	protected Object object;

	protected CloudEvent cloudEvent;

	protected String identifying;

	protected EventMeshRequest request = new EventMeshRequest();

	@Getter
	protected EventMeshResponse response = new EventMeshResponse();

	@Getter
	protected Command requestCommand;

	@Getter
	protected Command responseCommand;

	protected boolean isSuccess = true;

	protected boolean newProtocol = false;

	protected Throwable throwable;

	@Getter
	protected long requestTime = System.currentTimeMillis();

	@Getter
	protected Map<String, String> serviceContextData;

	protected void response(String message, int code) {

	}

	protected abstract void send();

	public abstract String sessionId();

	public abstract ProtocolType protocolType();

	public String identifying() {
		return identifying;
	}

	public EventMeshRequest request() {
		return request;
	}

	public void sendResponse() {
		this.send();
	}

	public void sendResponse(Map<String, Object> responseHeaderMap, Object responseBodyMap) {
		this.response.setHeaders(responseHeaderMap);
		this.response.setBodyObject(responseBodyMap);
	}

	public void sendErrorResponse() {
		this.sendErrorResponse(null);
	}

	public void sendErrorResponse(Throwable throwable) {
		this.throwable = throwable;
		this.isSuccess = false;
	}

	public void sendErrorResponse(EventMeshRetCode retCode, Map<String, Object> responseHeaderMap,
			Object responseBodyMap) {
		this.isSuccess = false;
		this.response.setHeaders(responseHeaderMap);
		this.response.setBodyObject(responseBodyMap);
	}

	public void sendErrorResponse(EventMeshRetCode retCode, String errorMessage) {
		this.sendErrorResponse(retCode, null, errorMessage);
	}

	public Map<String, Object> requestHeadler() {
		return this.request.getHeaders();
	}

	@SuppressWarnings("unchecked")
	public <T> T getObject() {
		return (T) this.object;
	}

	public Metadata getMetadata() {
		if (Objects.isNull(this.metadata)) {
			Metadata metadata = new Metadata();
			Map<String, Object> headers = this.request.getHeaders();
			metadata.setCode(MapUtils.getString(headers, ProtocolKey.REQUEST_CODE));
			metadata.setVersion(ProtocolVersion.get(MapUtils.getString(headers, ProtocolKey.VERSION)));
			String lan = StringUtils.isBlank(MapUtils.getString(headers, ProtocolKey.LANGUAGE))
					? Constants.LANGUAGE_JAVA
					: MapUtils.getString(headers, ProtocolKey.LANGUAGE);
			metadata.setLanguage(lan);
			metadata.setEnv(MapUtils.getString(headers, ProtocolKey.ClientInstanceKey.ENV));
			metadata.setIdc(MapUtils.getString(headers, ProtocolKey.ClientInstanceKey.IDC));
			metadata.setSys(MapUtils.getString(headers, ProtocolKey.ClientInstanceKey.SYS));
			metadata.setPid(MapUtils.getString(headers, ProtocolKey.ClientInstanceKey.PID));
			metadata.setIp(MapUtils.getString(headers, ProtocolKey.ClientInstanceKey.IP));
			metadata.setUsername(MapUtils.getString(headers, ProtocolKey.ClientInstanceKey.USERNAME));
			metadata.setPasswd(MapUtils.getString(headers, ProtocolKey.ClientInstanceKey.PASSWD));
			this.metadata = metadata;
		}
		return metadata;
	}

	protected CloudEvent getCloudEvent(Object object) {
		try {
			String protocolType = Objects.nonNull(this.request.getHeaders().get(ProtocolKey.PROTOCOL_TYPE))
					? this.request.getHeaders().get(ProtocolKey.PROTOCOL_TYPE).toString()
					: this.metadata.getProtocolType();
			ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory
					.getProtocolAdaptor(protocolType);
			return protocolAdaptor.toCloudEvent((ProtocolTransportObject) object);
		} catch (ProtocolHandleException e) {
			throw new RuntimeException(e);
		}
	}

	public abstract CloudEvent getCloudEvent();
}
