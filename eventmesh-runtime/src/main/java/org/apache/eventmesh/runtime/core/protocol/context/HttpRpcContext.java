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

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.Header;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.core.protocol.api.ProtocolType;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;

public class HttpRpcContext extends Context {

	private static final Map<String, RequestCode> CODE_REQUEST = new HashMap<>();

	private static final Map<RequestCode, EventMeshRetCode> REQUEST_RET_CODE = new HashMap<>();

	static {

		for (RequestCode code : RequestCode.values()) {
			CODE_REQUEST.put(code.getRequestCode().toString(), code);
		}
		// TODO
		REQUEST_RET_CODE.put(RequestCode.HEARTBEAT, EventMeshRetCode.EVENTMESH_HEARTBEAT_ERR);
	}

	private HttpRequest httpRequest;
	
	private HttpCommand httpCommand = new HttpCommand();

	public DefaultHttpDataFactory defaultHttpDataFactory = new DefaultHttpDataFactory(false);

	@Override
	public ProtocolType protocolType() {
		return ProtocolType.HTTP;
	}

	public void setChannle(Channel channle) {
		this.channle = channle;
	}

	public void setHttpRequest(HttpRequest httpRequest) {
		this.httpRequest = httpRequest;
	}

	@Override
	protected void init() {
		this.httpRequest.headers();
		this.httpRequest.method();
		this.httpRequest.protocolVersion();
		this.identifying = this.httpRequest.uri();
		try {
			if (Objects.equals(this.httpRequest.headers().get(ProtocolKey.PROTOCOL_NEW_VERSION), "true")) {
				this.newProtocol = true;
			} else {
				Map<String, Object> bodyMap = parseHttpRequestBody(httpRequest);
				this.identifying = (httpRequest.method() == HttpMethod.POST)
						? httpRequest.headers().get(ProtocolKey.REQUEST_CODE)
						: MapUtils.getString(bodyMap, StringUtils.lowerCase(ProtocolKey.REQUEST_CODE), "");
				// TODO
				this.request.setHeaders(parseHttpHeader(httpRequest));
				this.object = Body.buildBody(identifying, bodyMap);
				this.httpCommand.setBody((Body)this.object );
				this.httpCommand.setHeader(Header.buildHeader(identifying, this.request.getHeaders()));
				this.httpCommand.setRequestCode(this.identifying);
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {

		}
	}

	/**
	 * TODO
	 * 
	 * @param httpRequest
	 * @return
	 * @throws IOException
	 */
	private Map<String, Object> parseHttpRequestBody(HttpRequest httpRequest) throws IOException {
		Map<String, Object> httpRequestBody = new HashMap<>();

		if (HttpMethod.GET.equals(httpRequest.method())) {
			QueryStringDecoder getDecoder = new QueryStringDecoder(httpRequest.uri());
			getDecoder.parameters().forEach((key, value) -> httpRequestBody.put(key, value.get(0)));
		} else if (HttpMethod.POST.equals(httpRequest.method())) {
			HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(defaultHttpDataFactory, httpRequest);
			for (InterfaceHttpData parm : decoder.getBodyHttpDatas()) {
				if (parm.getHttpDataType() == InterfaceHttpData.HttpDataType.Attribute) {
					Attribute data = (Attribute) parm;
					httpRequestBody.put(data.getName(), data.getValue());
				}
			}
			decoder.destroy();
		}
		return httpRequestBody;
	}

	private Map<String, Object> parseHttpHeader(HttpRequest fullReq) {
		Map<String, Object> headerParam = new HashMap<>();
		for (String key : fullReq.headers().names()) {
			if (StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_TYPE.toString(), key)
					|| StringUtils.equalsIgnoreCase(HttpHeaderNames.ACCEPT_ENCODING.toString(), key)
					|| StringUtils.equalsIgnoreCase(HttpHeaderNames.CONTENT_LENGTH.toString(), key)) {
				continue;
			}
			headerParam.put(key, fullReq.headers().get(key));
		}
		return headerParam;
	}

	@Override
	protected void send() {

		HttpResponse defaultFullHttpResponse = this.newProtocol ? this.newProtocol() : this.oldProtocol();
		ChannelFuture channelFuture = channle.writeAndFlush(defaultFullHttpResponse);
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

	private HttpResponse newProtocol() {

		return null;
	}

	private HttpResponse oldProtocol() {
		ByteBuf content = null;
		RequestCode requestCode = CODE_REQUEST.get(this.identifying);
		EventMeshRetCode retCode = this.isSuccess ? EventMeshRetCode.SUCCESS : REQUEST_RET_CODE.get(requestCode);
		HttpHeaders headers = new DefaultHttpHeaders();
		headers.add(ProtocolKey.REQUEST_CODE, retCode.getRetCode());

		for (Entry<String, String> entry : this.serviceContextData.entrySet()) {
			headers.add(entry.getKey(), entry.getValue());
		}

		Map<String, Object> body = new HashMap<>();
		body.put("retCode", retCode.getRetCode());
		body.put("resTime", System.currentTimeMillis());
		if (this.isSuccess) {
			if (Objects.nonNull(this.response.getBodyObject())) {

			} else {
				body.put("retMsg", retCode.getErrMsg());
			}
		} else {
			body.put("retMsg", retCode.getErrMsg()
					+ (Objects.isNull(this.throwable) ? "" : EventMeshUtil.stackTrace(this.throwable, 2)));
		}
		content = Unpooled
				.wrappedBuffer(Objects.requireNonNull(JsonUtils.serialize(body)).getBytes(Constants.DEFAULT_CHARSET));
		headers.add(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=" + Constants.DEFAULT_CHARSET);
		headers.add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
		headers.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

		if (Objects.nonNull(this.response.getHeaders())) {
			for (Entry<String, Object> entry : response.getHeaders().entrySet()) {
				headers.add(entry.getKey(), entry.getValue());
			}
		}
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK,
				content, headers, headers);
		return response;
	}

	@Override
	public String sessionId() {
		InetSocketAddress inetSocketAddress = (InetSocketAddress) channle.remoteAddress();
		return inetSocketAddress.getHostString();
	}

	@Override
	public CloudEvent getCloudEvent() {
	
		return this.getCloudEvent(this.httpCommand);
	}

}
