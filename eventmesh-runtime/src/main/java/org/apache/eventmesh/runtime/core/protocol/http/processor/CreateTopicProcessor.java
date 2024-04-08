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

package org.apache.eventmesh.runtime.core.protocol.http.processor;

import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HttpClientGroupMapping;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;

import com.fasterxml.jackson.core.type.TypeReference;

@EventMeshTrace
public class CreateTopicProcessor implements AsyncHttpProcessor {

    private static final Logger HTTP_LOGGER = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    public CreateTopicProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void handler(HandlerService.HandlerSpecific handlerSpecific, HttpRequest httpRequest)
        throws Exception {
        final AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();
        final ChannelHandlerContext ctx = handlerSpecific.getCtx();
        final HttpEventWrapper requestWrapper = asyncContext.getRequest();

        HTTP_LOGGER.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress());

        // user request header
        Map<String, Object> userRequestHeaderMap = requestWrapper.getHeaderMap();
        String requestIp = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        userRequestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP.getKey(), requestIp);

        Map<String, Object> responseHeaderMap = new HashMap<>();
        responseHeaderMap.put(ProtocolKey.REQUEST_URI, requestWrapper.getRequestURI());
        responseHeaderMap
            .put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshCluster());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, IPUtils.getLocalAddress());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshEnv());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, eventMeshHTTPServer.getEventMeshHttpConfiguration().getEventMeshIDC());

        // validate body
        byte[] requestBody = requestWrapper.getBody();

        Map<String, Object> requestBodyMap = JsonUtils.parseTypeReferenceObject(new String(requestBody),
            new TypeReference<HashMap<String, Object>>() {
            });

        HttpEventWrapper responseWrapper;

        if (requestBodyMap.get("topic") == null || StringUtils.isBlank(requestBodyMap.get("topic").toString())) {
            Map<String, Object> responseBodyMap = new HashMap<>();
            responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode());
            responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + "topic is null");
            HTTP_LOGGER.warn("create topic fail, topic is null");
            responseWrapper = requestWrapper.createHttpResponse(responseHeaderMap, responseBodyMap);
            responseWrapper.setHttpResponseStatus(HttpResponseStatus.BAD_REQUEST);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        String topic = requestBodyMap.get("topic").toString();

        long startTime = System.currentTimeMillis();
        try {
            // pub topic in local cache
            String[] topicArr = topic.split(";");
            for (String item : topicArr) {
                item = StringUtils.deleteWhitespace(item);
                if (!HttpClientGroupMapping.getInstance().getLocalTopicSet().contains(item)) {
                    HttpClientGroupMapping.getInstance().getLocalTopicSet().add(item);
                    HTTP_LOGGER.info("create topic success, topic:{}", item);
                }
            }

            final CompleteHandler<HttpEventWrapper> handler = httpEventWrapper -> {
                try {
                    HTTP_LOGGER.debug("{}", httpEventWrapper);
                    eventMeshHTTPServer.sendResponse(ctx, httpEventWrapper.httpResponse());
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPReqResTimeCost(
                        System.currentTimeMillis() - requestWrapper.getReqTime());
                } catch (Exception ex) {
                    // ignore
                    HTTP_LOGGER.warn("create topic, sendResponse fail,", ex);
                }
            };

            responseWrapper = requestWrapper.createHttpResponse(EventMeshRetCode.SUCCESS);
            asyncContext.onComplete(responseWrapper, handler);

        } catch (Exception e) {
            Map<String, Object> responseBodyMap = new HashMap<>();
            responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getRetCode());
            responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_RUNTIME_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2));
            responseWrapper = asyncContext.getRequest().createHttpResponse(
                responseHeaderMap, responseBodyMap);
            responseWrapper.setHttpResponseStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_RUNTIME_ERR, responseHeaderMap,
                responseBodyMap, null);
            long endTime = System.currentTimeMillis();
            HTTP_LOGGER.warn(
                "create topic fail, eventMesh2client|cost={}ms|topic={}", endTime - startTime, topic, e);
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgFailed();
            eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendMsgCost(endTime - startTime);
        }
    }

    @Override
    public String[] paths() {
        return new String[] {RequestURI.CREATE_TOPIC.getRequestURI()};
    }

    @Override
    public Executor executor() {
        return eventMeshHTTPServer.getHttpThreadPoolGroup().getClientManageExecutor();
    }
}
