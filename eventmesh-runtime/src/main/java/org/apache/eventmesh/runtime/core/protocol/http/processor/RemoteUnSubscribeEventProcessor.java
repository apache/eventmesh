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
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;

@EventMeshTrace(isEnable = false)
public class RemoteUnSubscribeEventProcessor extends AbstractEventProcessor implements AsyncHttpProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);

    public RemoteUnSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void handler(HandlerService.HandlerSpecific handlerSpecific, HttpRequest httpRequest) throws Exception {

        AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();

        ChannelHandlerContext ctx = handlerSpecific.getCtx();

        HttpEventWrapper requestWrapper = asyncContext.getRequest();

        httpLogger.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP, RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress()
        );

        // user request header
        Map<String, Object> userRequestHeaderMap = requestWrapper.getHeaderMap();
        String requestIp = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        userRequestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, requestIp);

        // build sys header
        requestWrapper.buildSysHeaderForClient();

        Map<String, Object> responseHeaderMap = new HashMap<>();
        responseHeaderMap.put(ProtocolKey.REQUEST_URI, requestWrapper.getRequestURI());
        responseHeaderMap
            .put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster);
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, IPUtils.getLocalAddress());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv);
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

        Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();

        Map<String, Object> responseBodyMap = new HashMap<>();

        //validate header
        if (StringUtils.isBlank(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC).toString())
            || StringUtils.isBlank(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString())
            || !StringUtils.isNumeric(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString())
            || StringUtils.isBlank(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString())) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        //validate body
        byte[] requestBody = requestWrapper.getBody();

        Map<String, Object> requestBodyMap = JsonUtils.deserialize(new String(requestBody, Constants.DEFAULT_CHARSET),
            new TypeReference<HashMap<String, Object>>() {});

        if (requestBodyMap.get(EventMeshConstants.URL) == null || requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC) == null || requestBodyMap.get(EventMeshConstants.CONSUMER_GROUP) == null) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        String unSubscribeUrl = requestBodyMap.get(EventMeshConstants.URL).toString();
        String consumerGroup = requestBodyMap.get(EventMeshConstants.CONSUMER_GROUP).toString();
        String topic = requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC).toString();

        long startTime = System.currentTimeMillis();
        try {
            // request to remote
            String env = eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv;
            String idc = eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC;
            String cluster = eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster;
            String sysId = eventMeshHTTPServer.getEventMeshHttpConfiguration().sysID;
            String meshGroup = env + "-" + idc + "-" + cluster + "-" + sysId;

            Map<String, String> remoteHeaderMap = new HashMap<>();
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.ENV, env);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.IDC, idc);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, IPUtils.getLocalAddress());
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PID, String.valueOf(ThreadUtils.getPID()));
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.SYS, sysId);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.USERNAME, EventMeshConstants.USER_NAME);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PASSWD, EventMeshConstants.PASSWD);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PRODUCERGROUP, meshGroup);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.CONSUMERGROUP, meshGroup);

            // local unSubscription url
            String unsubscribeUrl = "http://" + IPUtils.getLocalAddress() + ":"
                + eventMeshHTTPServer.getEventMeshHttpConfiguration().httpServerPort
                + RequestURI.PUBLISH_BRIDGE.getRequestURI();

            Map<String, Object> remoteBodyMap = new HashMap<>();
            remoteBodyMap.put(EventMeshConstants.URL, unsubscribeUrl);
            remoteBodyMap.put(EventMeshConstants.CONSUMER_GROUP, meshGroup);
            remoteBodyMap.put(EventMeshConstants.MANAGE_TOPIC, requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC));

            List<String> unSubTopicList = JsonUtils.deserialize(JsonUtils.serialize(requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC)), new TypeReference<List<String>>() {
            });

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
            String meshAddress = getTargetMesh(consumerGroup, subscriptionList);
            if (StringUtils.isNotBlank(meshAddress)) {
                targetMesh = meshAddress;
            }

            CloseableHttpClient closeableHttpClient = eventMeshHTTPServer.httpClientPool.getClient();

            String remoteResult = post(closeableHttpClient, targetMesh, remoteHeaderMap, remoteBodyMap,
                response -> EntityUtils.toString(response.getEntity(), Constants.DEFAULT_CHARSET));

            Map<String, String> remoteResultMap = JsonUtils.deserialize(remoteResult, new TypeReference<Map<String, String>>() {
            });

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
            httpLogger.error(
                "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}"
                    + "|bizSeqNo={}|uniqueId={}", endTime - startTime,
                topic, unSubscribeUrl, e);
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR, responseHeaderMap,
                responseBodyMap, null);
        }
    }

    @Override
    public String[] paths() {
        return new String[] {RequestURI.UNSUBSCRIBE_REMOTE.getRequestURI()};
    }

    public static String post(CloseableHttpClient client, String uri,
                              Map<String, String> requestHeader, Map<String, Object> requestBody,
                              ResponseHandler<String> responseHandler) throws IOException {
        Preconditions.checkState(client != null, "client can't be null");
        Preconditions.checkState(StringUtils.isNotBlank(uri), "uri can't be null");
        Preconditions.checkState(requestHeader != null, "requestParam can't be null");
        Preconditions.checkState(responseHandler != null, "responseHandler can't be null");

        HttpPost httpPost = new HttpPost(uri);

        httpPost.addHeader("Content-Type", ContentType.APPLICATION_JSON.getMimeType());

        //header
        if (MapUtils.isNotEmpty(requestHeader)) {
            for (Map.Entry<String, String> entry : requestHeader.entrySet()) {
                httpPost.addHeader(entry.getKey(), entry.getValue());
            }
        }

        //body
        if (MapUtils.isNotEmpty(requestBody)) {
            String jsonStr = JsonUtils.serialize(requestBody);
            httpPost.setEntity(new StringEntity(jsonStr, ContentType.APPLICATION_JSON));
        }

        //ttl
        RequestConfig.Builder configBuilder = RequestConfig.custom();
        configBuilder.setSocketTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)))
            .setConnectTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)))
            .setConnectionRequestTimeout(Integer.parseInt(String.valueOf(Constants.DEFAULT_HTTP_TIME_OUT)));

        httpPost.setConfig(configBuilder.build());

        return client.execute(httpPost, responseHandler);
    }

}
