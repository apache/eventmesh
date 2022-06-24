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
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;

import com.fasterxml.jackson.core.type.TypeReference;

public class LocalUnSubscribeEventProcessor extends AbstractEventProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    public LocalUnSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpEventWrapper> asyncContext)
        throws Exception {

        HttpEventWrapper requestWrapper = asyncContext.getRequest();

        HttpEventWrapper responseWrapper;

        httpLogger.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
            EventMeshConstants.PROTOCOL_HTTP,
            RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress());

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

        String unSubscribeUrl = requestBodyMap.get("url").toString();
        String consumerGroup = requestBodyMap.get("consumerGroup").toString();
        String topic = JsonUtils.serialize(requestBodyMap.get("topic"));

        // unSubscriptionItem
        List<String> unSubTopicList = JsonUtils.deserialize(topic, new TypeReference<List<String>>() {
        });

        String env = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.ENV).toString();
        String idc = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC).toString();
        String sys = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString();
        String ip = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.IP).toString();
        String pid = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString();

        final CompleteHandler<HttpEventWrapper> handler = httpEventWrapper -> {
            try {
                if (httpLogger.isDebugEnabled()) {
                    httpLogger.debug("{}", httpEventWrapper);
                }
                eventMeshHTTPServer.sendResponse(ctx, httpEventWrapper.httpResponse());
                eventMeshHTTPServer.metrics.getSummaryMetrics().recordHTTPReqResTimeCost(
                    System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
            } catch (Exception ex) {
                // ignore
            }
        };

        synchronized (eventMeshHTTPServer.localClientInfoMapping) {
            boolean isChange = true;

            registerClient(requestWrapper, consumerGroup, unSubTopicList, unSubscribeUrl);

            for (String unSubTopic : unSubTopicList) {
                List<Client> groupTopicClients = eventMeshHTTPServer.localClientInfoMapping
                    .get(consumerGroup + "@" + unSubTopic);
                Iterator<Client> clientIterator = groupTopicClients.iterator();
                while (clientIterator.hasNext()) {
                    Client client = clientIterator.next();
                    if (StringUtils.equals(client.pid, pid)
                        && StringUtils.equals(client.url, unSubscribeUrl)) {
                        httpLogger.warn("client {} start unsubscribe", JsonUtils.serialize(client));
                        clientIterator.remove();
                    }
                }
                if (groupTopicClients.size() > 0) {
                    //change url
                    Map<String, List<String>> idcUrls = new HashMap<>();
                    Set<String> clientUrls = new HashSet<>();
                    for (Client client : groupTopicClients) {
                        // remove subscribed url
                        if (!StringUtils.equals(unSubscribeUrl, client.url)) {
                            clientUrls.add(client.url);
                            if (idcUrls.containsKey(client.idc)) {
                                idcUrls.get(client.idc)
                                    .add(StringUtils.deleteWhitespace(client.url));
                            } else {
                                List<String> urls = new ArrayList<>();
                                urls.add(client.url);
                                idcUrls.put(client.idc, urls);
                            }
                        }

                    }
                    synchronized (eventMeshHTTPServer.localConsumerGroupMapping) {
                        ConsumerGroupConf consumerGroupConf =
                            eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup);
                        Map<String, ConsumerGroupTopicConf> map =
                            consumerGroupConf.getConsumerGroupTopicConf();
                        for (String topicKey : map.keySet()) {
                            // only modify the topic to subscribe
                            if (StringUtils.equals(unSubTopic, topicKey)) {
                                ConsumerGroupTopicConf latestTopicConf =
                                    new ConsumerGroupTopicConf();
                                latestTopicConf.setConsumerGroup(consumerGroup);
                                latestTopicConf.setTopic(unSubTopic);
                                latestTopicConf
                                    .setSubscriptionItem(map.get(topicKey).getSubscriptionItem());
                                latestTopicConf.setUrls(clientUrls);

                                latestTopicConf.setIdcUrls(idcUrls);

                                map.put(unSubTopic, latestTopicConf);
                            }
                        }
                        eventMeshHTTPServer.localConsumerGroupMapping
                            .put(consumerGroup, consumerGroupConf);
                    }
                } else {
                    isChange = false;
                    break;
                }
            }
            long startTime = System.currentTimeMillis();
            if (isChange) {
                try {
                    eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                        eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup));

                    responseWrapper =
                        asyncContext.getRequest().createHttpResponse(EventMeshRetCode.SUCCESS);
                    asyncContext.onComplete(responseWrapper, handler);

                } catch (Exception e) {
                    Map<String, Object> responseBodyMap = new HashMap<>();
                    responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode());
                    responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2));
                    HttpEventWrapper err = asyncContext.getRequest().createHttpResponse(responseHeaderMap, responseBodyMap);
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error(
                        "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms"
                            + "|topic={}|bizSeqNo={}|uniqueId={}", endTime - startTime,
                        JsonUtils.serialize(unSubTopicList), unSubscribeUrl, e);
                    eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsgFailed();
                    eventMeshHTTPServer.metrics.getSummaryMetrics()
                        .recordSendMsgCost(endTime - startTime);
                }
            } else {
                //remove
                try {
                    eventMeshHTTPServer.getConsumerManager()
                        .notifyConsumerManager(consumerGroup, null);
                    responseWrapper =
                        asyncContext.getRequest().createHttpResponse(EventMeshRetCode.SUCCESS);
                    asyncContext.onComplete(responseWrapper, handler);
                    // clean ClientInfo
                    eventMeshHTTPServer.localClientInfoMapping.keySet()
                        .removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // clean ConsumerGroupInfo
                    eventMeshHTTPServer.localConsumerGroupMapping.keySet()
                        .removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    Map<String, Object> responseBodyMap = new HashMap<>();
                    responseBodyMap.put("retCode", EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode());
                    responseBodyMap.put("retMsg", EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2));
                    HttpEventWrapper err = asyncContext.getRequest().createHttpResponse(
                        responseHeaderMap, responseBodyMap);
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error(
                        "message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms"
                            + "|topic={}|bizSeqNo={}|uniqueId={}", endTime - startTime,
                        JsonUtils.serialize(unSubTopicList), unSubscribeUrl, e);
                    eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsgFailed();
                    eventMeshHTTPServer.metrics.getSummaryMetrics().recordSendMsgCost(endTime - startTime);
                }
            }

            // Update service metadata
            updateMetadata();
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private void registerClient(HttpEventWrapper requestWrapper,
                                String consumerGroup,
                                List<String> topicList, String url) {
        Map<String, Object> requestHeaderMap = requestWrapper.getSysHeaderMap();
        for (String topic : topicList) {
            Client client = new Client();
            client.env = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.ENV).toString();
            client.idc = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC).toString();
            client.sys = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS).toString();
            client.ip = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IP).toString();
            client.pid = requestHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString();
            client.consumerGroup = consumerGroup;
            client.topic = topic;
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
