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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.RequestURI;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.common.EventMeshTrace;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.AbstractEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

@EventMeshTrace(isEnable = false)
public class LocalUnSubscribeEventProcessor extends AbstractEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalUnSubscribeEventProcessor.class);

    public LocalUnSubscribeEventProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void handler(final HandlerService.HandlerSpecific handlerSpecific, final HttpRequest httpRequest) throws Exception {

        AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();
        ChannelHandlerContext ctx = handlerSpecific.getCtx();
        HttpEventWrapper requestWrapper = asyncContext.getRequest();

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
                    EventMeshConstants.PROTOCOL_HTTP,
                    RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtils.getLocalAddress());
        }

        // user request header
        Map<String, Object> userRequestHeaderMap = requestWrapper.getHeaderMap();
        String requestIp = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        userRequestHeaderMap.put(ProtocolKey.ClientInstanceKey.IP, requestIp);

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

        final Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.deserialize(
                new String(requestBody, Constants.DEFAULT_CHARSET),
                new TypeReference<HashMap<String, Object>>() {
                }
        )).orElse(Maps.newHashMap());

        if (requestBodyMap.get(EventMeshConstants.URL) == null
                || requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC) == null
                || requestBodyMap.get(EventMeshConstants.CONSUMER_GROUP) == null) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                    responseBodyMap, null);
            return;
        }

        String unSubscribeUrl = requestBodyMap.get(EventMeshConstants.URL).toString();
        String consumerGroup = requestBodyMap.get(EventMeshConstants.CONSUMER_GROUP).toString();
        String topic = JsonUtils.serialize(requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC));

        // unSubscriptionItem
        List<String> unSubTopicList = Optional.ofNullable(JsonUtils.deserialize(
                topic,
                new TypeReference<List<String>>() {
                }
        )).orElse(Collections.emptyList());

        String pid = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID).toString();

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
                        LOGGER.warn("client {} start unsubscribe", JsonUtils.serialize(client));
                        clientIterator.remove();
                    }
                }

                if (CollectionUtils.isNotEmpty(groupTopicClients)) {
                    //change url
                    Map<String, List<String>> idcUrls = new HashMap<>();
                    Set<String> clientUrls = new HashSet<>();
                    for (Client client : groupTopicClients) {
                        // remove subscribed url
                        if (!StringUtils.equals(unSubscribeUrl, client.url)) {
                            clientUrls.add(client.url);

                            List<String> urls = idcUrls.get(client.idc);
                            if (urls == null) {
                                urls = new ArrayList<>();
                                idcUrls.put(client.idc, urls);
                            }
                            urls.add(StringUtils.deleteWhitespace(client.url));

                        }

                    }

                    synchronized (eventMeshHTTPServer.localConsumerGroupMapping) {
                        ConsumerGroupConf consumerGroupConf =
                                eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup);
                        Map<String, ConsumerGroupTopicConf> map =
                                consumerGroupConf.getConsumerGroupTopicConf();
                        Set<Map.Entry<String, ConsumerGroupTopicConf>> entryMap = map.entrySet();
                        for (Map.Entry<String, ConsumerGroupTopicConf> entry : entryMap) {
                            // only modify the topic to subscribe
                            if (StringUtils.equals(unSubTopic, entry.getKey())) {
                                ConsumerGroupTopicConf latestTopicConf =
                                        new ConsumerGroupTopicConf();
                                latestTopicConf.setConsumerGroup(consumerGroup);
                                latestTopicConf.setTopic(unSubTopic);
                                latestTopicConf
                                        .setSubscriptionItem(entry.getValue().getSubscriptionItem());
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

                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());

                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);

                } catch (Exception e) {
                    final String logMsg = String.format("message|eventMesh2mq|REQ|ASYNC|send2MQCost=%s ms"
                                    + "|topic=%s|url=%s", System.currentTimeMillis() - startTime,
                            JsonUtils.serialize(unSubTopicList), unSubscribeUrl);
                    LOGGER.error(logMsg, e);
                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR, responseHeaderMap,
                            responseBodyMap, null);
                }
            } else {
                //remove
                try {
                    eventMeshHTTPServer.getConsumerManager()
                            .notifyConsumerManager(consumerGroup, null);
                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());

                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
                    // clean ClientInfo
                    eventMeshHTTPServer.localClientInfoMapping.keySet()
                            .removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // clean ConsumerGroupInfo
                    eventMeshHTTPServer.localConsumerGroupMapping.keySet()
                            .removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    final String logMsg = String.format("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms"
                                    + "|topic={}|bizSeqNo={}|uniqueId={}", System.currentTimeMillis() - startTime,
                            JsonUtils.serialize(unSubTopicList), unSubscribeUrl);
                    LOGGER.error(logMsg, e);
                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR, responseHeaderMap,
                            responseBodyMap, null);
                }
            }

            // Update service metadata
            updateMetadata();
        }
    }

    @Override
    public String[] paths() {
        return new String[]{RequestURI.UNSUBSCRIBE_LOCAL.getRequestURI()};
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
