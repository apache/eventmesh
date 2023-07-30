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

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

@EventMeshTrace
public class RemoteUnSubscribeEventProcessor extends AbstractEventProcessor {

    private static final Logger httpLogger = LoggerFactory.getLogger(EventMeshConstants.PROTOCOL_HTTP);

    private static final Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);

    public RemoteUnSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void handler(HandlerService.HandlerSpecific handlerSpecific, HttpRequest httpRequest) throws Exception {

        AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();

        ChannelHandlerContext ctx = handlerSpecific.getCtx();

        HttpEventWrapper requestWrapper = asyncContext.getRequest();

        String localAddress = IPUtils.getLocalAddress();
        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        httpLogger.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP, remoteAddr, localAddress
        );

        // user request header
        Map<String, Object> userRequestHeaderMap = requestWrapper.getHeaderMap();
        userRequestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, remoteAddr);

        // build sys header
        requestWrapper.buildSysHeaderForClient();

        Map<String, Object> responseHeaderMap = builderResponseHeaderMap(requestWrapper);

        Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();

        Map<String, Object> responseBodyMap = new HashMap<>();

        //validate header
        if (validateSysHeader(sysHeaderMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        //validate body
        byte[] requestBody = requestWrapper.getBody();

        Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            new String(requestBody, Constants.DEFAULT_CHARSET),
            new TypeReference<HashMap<String, Object>>() {
            }
        )).orElseGet(Maps::newHashMap);

        if (validatedRequestBodyMap(requestBodyMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        String topic = JsonUtils.toJSONString(requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC).toString());

        long startTime = System.currentTimeMillis();
        try {
            // request to remote
            EventMeshHTTPConfiguration eventMeshHttpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
            String env = eventMeshHttpConfiguration.getEventMeshEnv();
            String idc = eventMeshHttpConfiguration.getEventMeshIDC();
            String cluster = eventMeshHttpConfiguration.getEventMeshCluster();
            String sysId = eventMeshHttpConfiguration.getSysID();
            String meshGroup = String.join("-", env, idc, cluster, sysId);

            // local unSubscription url
            String unsubscribeUrl = "http://" + localAddress + ":"
                + eventMeshHttpConfiguration.getEventMeshHttpServerPort()
                + RequestURI.PUBLISH_BRIDGE.getRequestURI();

            Map<String, Object> remoteBodyMap = new HashMap<>();
            remoteBodyMap.put(EventMeshConstants.URL, unsubscribeUrl);
            remoteBodyMap.put(EventMeshConstants.CONSUMER_GROUP, meshGroup);
            remoteBodyMap.put(EventMeshConstants.MANAGE_TOPIC, requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC));

            List<String> unSubTopicList = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
                JsonUtils.toJSONString(requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC)),
                new TypeReference<List<String>>() {
                }
            )).orElseGet(Collections::emptyList);

            String targetMesh = "";
            if (!Objects.isNull(requestBodyMap.get("remoteMesh"))) {
                targetMesh = requestBodyMap.get("remoteMesh").toString();
            }

            List<SubscriptionItem> subscriptionList = unSubTopicList.stream().map(s -> {
                SubscriptionItem subscriptionItem = new SubscriptionItem();
                subscriptionItem.setTopic(s);
                return subscriptionItem;
            }).collect(Collectors.toList());
            // Get mesh address from registry
            String meshAddress = getTargetMesh(meshGroup, subscriptionList);
            if (StringUtils.isNotBlank(meshAddress)) {
                targetMesh = meshAddress;
            }

            CloseableHttpClient closeableHttpClient = eventMeshHTTPServer.getHttpClientPool().getClient();

            String remoteResult = post(closeableHttpClient, targetMesh, builderRemoteHeaderMap(localAddress), remoteBodyMap,
                response -> EntityUtils.toString(response.getEntity(), Constants.DEFAULT_CHARSET));

            Map<String, String> remoteResultMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
                remoteResult,
                new TypeReference<Map<String, String>>() {
                }
            )).orElseGet(Maps::newHashMap);

            if (String.valueOf(EventMeshRetCode.SUCCESS.getRetCode()).equals(remoteResultMap.get(EventMeshConstants.RET_CODE))) {
                responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());
                handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
            } else {
                handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR, responseHeaderMap,
                    responseBodyMap, null);
            }
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            httpLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}", endTime - startTime, topic, e);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR, responseHeaderMap,
                responseBodyMap, null);
        }
    }

    @Override
    public String[] paths() {
        return new String[]{RequestURI.UNSUBSCRIBE_REMOTE.getRequestURI()};
    }


}
