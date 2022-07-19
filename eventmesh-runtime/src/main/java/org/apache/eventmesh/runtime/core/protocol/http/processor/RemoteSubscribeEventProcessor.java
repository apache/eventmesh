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
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.WebhookUtil;

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
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;

public class RemoteSubscribeEventProcessor extends AbstractEventProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger aclLogger = LoggerFactory.getLogger("acl");


    public RemoteSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpEventWrapper> asyncContext)
        throws Exception {

        HttpEventWrapper requestWrapper = asyncContext.getRequest();

        HttpEventWrapper responseWrapper;

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

        //validate header
        if (StringUtils.isBlank(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC).toString())
            || StringUtils.isBlank(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString())
            || !StringUtils.isNumeric(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString())
            || StringUtils.isBlank(sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString())) {

            Map<String, Object> responseBodyMap = new HashMap<>();
            responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode());
            responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg());
            responseWrapper = requestWrapper.createHttpResponse(responseHeaderMap, responseBodyMap);
            asyncContext.onComplete(responseWrapper);
            return;
        }


        //validate body
        byte[] requestBody = requestWrapper.getBody();

        Map<String, Object> requestBodyMap = JsonUtils.deserialize(new String(requestBody), new TypeReference<HashMap<String, Object>>() {
        });


        if (requestBodyMap.get("url") == null || requestBodyMap.get("topic") == null || requestBodyMap.get("consumerGroup") == null) {
            Map<String, Object> responseBodyMap = new HashMap<>();
            responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode());
            responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg());
            responseWrapper = requestWrapper.createHttpResponse(responseHeaderMap, responseBodyMap);
            asyncContext.onComplete(responseWrapper);
            return;
        }

        String url = requestBodyMap.get("url").toString();
        String consumerGroup = requestBodyMap.get("consumerGroup").toString();
        String topic = JsonUtils.serialize(requestBodyMap.get("topic"));


        // SubscriptionItem
        List<SubscriptionItem> subscriptionList = JsonUtils.deserialize(topic, new TypeReference<List<SubscriptionItem>>() {
        });

        //do acl check
        if (eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshServerSecurityEnable) {
            String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
            String user = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.USERNAME).toString();
            String pass = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PASSWD).toString();
            String subsystem = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString();
            for (SubscriptionItem item : subscriptionList) {
                try {
                    Acl.doAclCheckInHttpReceive(remoteAddr, user, pass, subsystem, item.getTopic(),
                        requestWrapper.getRequestURI());
                } catch (Exception e) {
                    Map<String, Object> responseBodyMap = new HashMap<>();
                    responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_ACL_ERR.getRetCode());
                    responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_ACL_ERR.getErrMsg());

                    responseWrapper = asyncContext.getRequest().createHttpResponse(responseHeaderMap, responseBodyMap);
                    asyncContext.onComplete(responseWrapper);
                    aclLogger.warn("CLIENT HAS NO PERMISSION,SubscribeProcessor subscribe failed", e);
                    return;
                }
            }
        }

        // validate URL
        try {
            if (!IPUtils.isValidDomainOrIp(url, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv4BlackList,
                eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIpv6BlackList)) {
                httpLogger.error("subscriber url {} is not valid", url);
                Map<String, Object> responseBodyMap = new HashMap<>();
                responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode());
                responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " invalid URL: " + url);
                responseWrapper = requestWrapper.createHttpResponse(responseHeaderMap, responseBodyMap);
                asyncContext.onComplete(responseWrapper);
                return;
            }
        } catch (Exception e) {
            httpLogger.error("subscriber url {} is not valid, error {}", url, e.getMessage());
            Map<String, Object> responseBodyMap = new HashMap<>();
            responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode());
            responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + " invalid URL: " + url);
            responseWrapper = requestWrapper.createHttpResponse(responseHeaderMap, responseBodyMap);
            asyncContext.onComplete(responseWrapper);
            return;
        }

        // obtain webhook delivery agreement for Abuse Protection
        boolean isWebhookAllowed = WebhookUtil.obtainDeliveryAgreement(eventMeshHTTPServer.httpClientPool.getClient(),
            url, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshWebhookOrigin);

        if (!isWebhookAllowed) {
            httpLogger.error("subscriber url {} is not allowed by the target system", url);
            Map<String, Object> responseBodyMap = new HashMap<>();
            responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode());
            responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg() + "unauthorized webhook URL: " + url);
            responseWrapper = requestWrapper.createHttpResponse(responseHeaderMap, responseBodyMap);
            asyncContext.onComplete(responseWrapper);
            return;
        }

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
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.USERNAME, "eventmesh");
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PASSWD, "pass");
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.PRODUCERGROUP, meshGroup);
            remoteHeaderMap.put(ProtocolKey.ClientInstanceKey.CONSUMERGROUP, meshGroup);

            // local subscription url
            String localUrl = "http://" + IPUtils.getLocalAddress() + ":"
                + eventMeshHTTPServer.getEventMeshHttpConfiguration().httpServerPort
                + RequestURI.PUBLISH_BRIDGE.getRequestURI();

            Map<String, Object> remoteBodyMap = new HashMap<>();
            remoteBodyMap.put("url", localUrl);
            remoteBodyMap.put("consumerGroup", meshGroup);
            remoteBodyMap.put("topic", requestBodyMap.get("topic"));

            String targetMesh = requestBodyMap.get("remoteMesh") == null ? "" : requestBodyMap.get("remoteMesh").toString();

            // Get mesh address from registry
            String meshAddress = getTargetMesh(consumerGroup, subscriptionList);
            if (StringUtils.isNotBlank(meshAddress)) {
                targetMesh = meshAddress;
            }


            CloseableHttpClient closeableHttpClient = eventMeshHTTPServer.httpClientPool.getClient();

            String remoteResult = post(closeableHttpClient, targetMesh, remoteHeaderMap, remoteBodyMap,
                response -> EntityUtils.toString(response.getEntity(), Charset.forName(Constants.DEFAULT_CHARSET)));

            Map<String, String> remoteResultMap = JsonUtils.deserialize(remoteResult, new TypeReference<Map<String, String>>() {
            });

            final CompleteHandler<HttpEventWrapper> handler = httpEventWrapper -> {
                try {
                    if (httpLogger.isDebugEnabled()) {
                        httpLogger.debug("{}", httpEventWrapper);
                    }
                    eventMeshHTTPServer.sendResponse(ctx, httpEventWrapper.httpResponse());
                    eventMeshHTTPServer.metrics.getSummaryMetrics().recordHTTPReqResTimeCost(
                        System.currentTimeMillis() - requestWrapper.getReqTime());
                } catch (Exception ex) {
                    // ignore
                }
            };

            if (String.valueOf(EventMeshRetCode.SUCCESS.getRetCode()).equals(remoteResultMap.get("retCode"))) {
                responseWrapper = requestWrapper.createHttpResponse(EventMeshRetCode.SUCCESS);
                asyncContext.onComplete(responseWrapper, handler);
            } else {
                Map<String, Object> responseBodyMap = new HashMap<>();
                responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getRetCode());
                responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg());
                HttpEventWrapper err = asyncContext.getRequest().createHttpResponse(
                    responseHeaderMap, responseBodyMap);
                asyncContext.onComplete(err);
            }

        } catch (Exception e) {
            Map<String, Object> responseBodyMap = new HashMap<>();
            responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getRetCode());
            responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_SUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2));
            HttpEventWrapper err = asyncContext.getRequest().createHttpResponse(
                responseHeaderMap, responseBodyMap);
            asyncContext.onComplete(err);
            long endTime = System.currentTimeMillis();
            httpLogger.error(
                "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}"
                    + "|bizSeqNo={}|uniqueId={}", endTime - startTime,
                JsonUtils.serialize(subscriptionList), url, e);
            eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsgFailed();
            eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsgCost(endTime - startTime);
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
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
