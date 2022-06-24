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

import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.apache.eventmesh.runtime.util.WebhookUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

import com.fasterxml.jackson.core.type.TypeReference;

public class LocalSubscribeEventProcessor extends AbstractEventProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public Logger aclLogger = LoggerFactory.getLogger("acl");


    public LocalSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
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
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster);
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP,
            IPUtils.getLocalAddress());
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv);
        responseHeaderMap.put(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
            eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);

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

        synchronized (eventMeshHTTPServer.localClientInfoMapping) {

            registerClient(requestWrapper, consumerGroup, subscriptionList, url);

            for (SubscriptionItem subTopic : subscriptionList) {
                List<Client> groupTopicClients = eventMeshHTTPServer.localClientInfoMapping
                    .get(consumerGroup + "@" + subTopic.getTopic());

                if (CollectionUtils.isEmpty(groupTopicClients)) {
                    httpLogger.error("group {} topic {} clients is empty", consumerGroup, subTopic);
                }

                Map<String, List<String>> idcUrls = new HashMap<>();
                for (Client client : groupTopicClients) {
                    if (idcUrls.containsKey(client.idc)) {
                        idcUrls.get(client.idc).add(StringUtils.deleteWhitespace(client.url));
                    } else {
                        List<String> urls = new ArrayList<>();
                        urls.add(client.url);
                        idcUrls.put(client.idc, urls);
                    }
                }
                ConsumerGroupConf consumerGroupConf =
                    eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup);
                if (consumerGroupConf == null) {
                    // new subscription
                    consumerGroupConf = new ConsumerGroupConf(consumerGroup);
                    ConsumerGroupTopicConf consumeTopicConfig = new ConsumerGroupTopicConf();
                    consumeTopicConfig.setConsumerGroup(consumerGroup);
                    consumeTopicConfig.setTopic(subTopic.getTopic());
                    consumeTopicConfig.setSubscriptionItem(subTopic);
                    consumeTopicConfig.setUrls(new HashSet<>(Arrays.asList(url)));

                    consumeTopicConfig.setIdcUrls(idcUrls);

                    Map<String, ConsumerGroupTopicConf> map = new HashMap<>();
                    map.put(subTopic.getTopic(), consumeTopicConfig);
                    consumerGroupConf.setConsumerGroupTopicConf(map);
                } else {
                    // already subscribed
                    Map<String, ConsumerGroupTopicConf> map =
                        consumerGroupConf.getConsumerGroupTopicConf();
                    if (!map.containsKey(subTopic.getTopic())) {
                        //If there are multiple topics, append it
                        ConsumerGroupTopicConf newTopicConf = new ConsumerGroupTopicConf();
                        newTopicConf.setConsumerGroup(consumerGroup);
                        newTopicConf.setTopic(subTopic.getTopic());
                        newTopicConf.setSubscriptionItem(subTopic);
                        newTopicConf.setUrls(new HashSet<>(Arrays.asList(url)));
                        newTopicConf.setIdcUrls(idcUrls);
                        map.put(subTopic.getTopic(), newTopicConf);
                    }
                    for (String key : map.keySet()) {
                        if (StringUtils.equals(subTopic.getTopic(), key)) {
                            ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                            latestTopicConf.setConsumerGroup(consumerGroup);
                            latestTopicConf.setTopic(subTopic.getTopic());
                            latestTopicConf.setSubscriptionItem(subTopic);
                            latestTopicConf.setUrls(new HashSet<>(Arrays.asList(url)));

                            ConsumerGroupTopicConf currentTopicConf = map.get(key);
                            latestTopicConf.getUrls().addAll(currentTopicConf.getUrls());
                            latestTopicConf.setIdcUrls(idcUrls);

                            map.put(key, latestTopicConf);
                        }
                    }
                }
                eventMeshHTTPServer.localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
            }

            long startTime = System.currentTimeMillis();
            try {
                // subscription relationship change notification
                eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                    eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup));

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

                responseWrapper = requestWrapper.createHttpResponse(EventMeshRetCode.SUCCESS);
                asyncContext.onComplete(responseWrapper, handler);
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

            // Update service metadata
            updateMetadata();
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private void registerClient(HttpEventWrapper requestWrapper, String consumerGroup,
                                List<SubscriptionItem> subscriptionItems, String url) {
        Map<String, Object> requestHeaderMap = requestWrapper.getSysHeaderMap();
        for (SubscriptionItem item : subscriptionItems) {
            Client client = new Client();
            client.env = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.ENV).toString();
            client.idc = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC).toString();
            client.sys = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString();
            client.ip = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IP).toString();
            client.pid = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString();
            client.consumerGroup = consumerGroup;
            client.topic = item.getTopic();
            client.url = url;
            client.lastUpTime = new Date();

            String groupTopicKey = client.consumerGroup + "@" + client.topic;

            if (eventMeshHTTPServer.localClientInfoMapping.containsKey(groupTopicKey)) {
                List<Client> localClients =
                    eventMeshHTTPServer.localClientInfoMapping.get(groupTopicKey);
                boolean isContains = false;
                for (Client localClient : localClients) {
                    if (StringUtils.equals(localClient.url, client.url)) {
                        isContains = true;
                        localClient.lastUpTime = client.lastUpTime;
                        break;
                    }
                }
                if (!isContains) {
                    localClients.add(client);
                }
            } else {
                List<Client> clients = new ArrayList<>();
                clients.add(client);
                eventMeshHTTPServer.localClientInfoMapping.put(groupTopicKey, clients);
            }
        }
    }

}
