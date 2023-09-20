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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@EventMeshTrace(isEnable = false)
public class LocalUnSubscribeEventProcessor extends AbstractEventProcessor {

    public LocalUnSubscribeEventProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        super(eventMeshHTTPServer);
    }

    @Override
    public void handler(final HandlerService.HandlerSpecific handlerSpecific, final HttpRequest httpRequest) throws Exception {

        final AsyncContext<HttpEventWrapper> asyncContext = handlerSpecific.getAsyncContext();

        final ChannelHandlerContext ctx = handlerSpecific.getCtx();

        final HttpEventWrapper requestWrapper = asyncContext.getRequest();

        String localAddress = IPUtils.getLocalAddress();
        String remoteAddr = RemotingHelper.parseChannelRemoteAddr(ctx.channel());
        if (log.isInfoEnabled()) {
            log.info("uri={}|{}|client2eventMesh|from={}|to={}", requestWrapper.getRequestURI(),
                EventMeshConstants.PROTOCOL_HTTP, remoteAddr, localAddress);
        }

        // user request header
        requestWrapper.getHeaderMap().put(ProtocolKey.ClientInstanceKey.IP.getKey(), remoteAddr);

        // build sys header
        requestWrapper.buildSysHeaderForClient();

        final Map<String, Object> responseHeaderMap = builderResponseHeaderMap(requestWrapper);
        final Map<String, Object> sysHeaderMap = requestWrapper.getSysHeaderMap();
        final Map<String, Object> responseBodyMap = new HashMap<>();

        // validate header
        if (validateSysHeader(sysHeaderMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        // validate body
        final byte[] requestBody = requestWrapper.getBody();

        final Map<String, Object> requestBodyMap = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            new String(requestBody, Constants.DEFAULT_CHARSET),
            new TypeReference<HashMap<String, Object>>() {
            })).orElseGet(Maps::newHashMap);

        if (validatedRequestBodyMap(requestBodyMap)) {
            handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, responseHeaderMap,
                responseBodyMap, null);
            return;
        }

        final String unSubscribeUrl = requestBodyMap.get(EventMeshConstants.URL).toString();
        final String consumerGroup = requestBodyMap.get(EventMeshConstants.CONSUMER_GROUP).toString();

        // unSubscriptionItem
        final List<String> unSubTopicList = Optional.ofNullable(JsonUtils.parseTypeReferenceObject(
            JsonUtils.toJSONString(requestBodyMap.get(EventMeshConstants.MANAGE_TOPIC)),
            new TypeReference<List<String>>() {
            })).orElseGet(Collections::emptyList);

        final String pid = sysHeaderMap.get(ProtocolKey.ClientInstanceKey.PID.getKey()).toString();

        synchronized (eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping()) {
            boolean isChange = true;

            registerClient(requestWrapper, consumerGroup, unSubTopicList, unSubscribeUrl);

            for (final String unSubTopic : unSubTopicList) {
                final List<Client> groupTopicClients = eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping()
                    .get(consumerGroup + "@" + unSubTopic);
                final Iterator<Client> clientIterator = groupTopicClients.iterator();
                while (clientIterator.hasNext()) {
                    final Client client = clientIterator.next();
                    if (StringUtils.equals(client.getPid(), pid)
                        && StringUtils.equals(client.getUrl(), unSubscribeUrl)) {
                        if (log.isWarnEnabled()) {
                            log.warn("client {} start unsubscribe", JsonUtils.toJSONString(client));
                        }
                        clientIterator.remove();
                    }
                }

                if (CollectionUtils.isNotEmpty(groupTopicClients)) {
                    // change url
                    final Map<String, List<String>> idcUrls = new HashMap<>();
                    final Set<String> clientUrls = new HashSet<>();
                    for (final Client client : groupTopicClients) {
                        // remove subscribed url
                        if (!StringUtils.equals(unSubscribeUrl, client.getUrl())) {
                            clientUrls.add(client.getUrl());

                            List<String> urls = idcUrls.computeIfAbsent(client.getIdc(), list -> new ArrayList<>());
                            urls.add(StringUtils.deleteWhitespace(client.getUrl()));
                        }

                    }

                    synchronized (eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping()) {
                        final ConsumerGroupConf consumerGroupConf =
                            eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().get(consumerGroup);
                        final Map<String, ConsumerGroupTopicConf> map =
                            consumerGroupConf.getConsumerGroupTopicConf();
                        for (final Map.Entry<String, ConsumerGroupTopicConf> entry : map.entrySet()) {
                            // only modify the topic to subscribe
                            if (StringUtils.equals(unSubTopic, entry.getKey())) {
                                final ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                                latestTopicConf.setConsumerGroup(consumerGroup);
                                latestTopicConf.setTopic(unSubTopic);
                                latestTopicConf.setSubscriptionItem(entry.getValue().getSubscriptionItem());
                                latestTopicConf.setUrls(clientUrls);
                                latestTopicConf.setIdcUrls(idcUrls);
                                map.put(unSubTopic, latestTopicConf);
                            }
                        }
                        eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().put(consumerGroup, consumerGroupConf);
                    }
                } else {
                    isChange = false;
                    break;
                }
            }
            final long startTime = System.currentTimeMillis();
            if (isChange) {
                try {
                    eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup,
                        eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().get(consumerGroup));

                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());

                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);

                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms"
                            + "|topic={}|url={}", System.currentTimeMillis() - startTime,
                            JsonUtils.toJSONString(unSubTopicList), unSubscribeUrl, e);
                    }
                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR, responseHeaderMap,
                        responseBodyMap, null);
                }
            } else {
                // remove
                try {
                    eventMeshHTTPServer.getConsumerManager()
                        .notifyConsumerManager(consumerGroup, null);
                    responseBodyMap.put(EventMeshConstants.RET_CODE, EventMeshRetCode.SUCCESS.getRetCode());
                    responseBodyMap.put(EventMeshConstants.RET_MSG, EventMeshRetCode.SUCCESS.getErrMsg());

                    handlerSpecific.sendResponse(responseHeaderMap, responseBodyMap);
                    // clean ClientInfo
                    eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().keySet()
                        .removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // clean ConsumerGroupInfo
                    eventMeshHTTPServer.getSubscriptionManager().getLocalConsumerGroupMapping().keySet()
                        .removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms"
                            + "|topic={}|url={}", System.currentTimeMillis() - startTime,
                            JsonUtils.toJSONString(unSubTopicList), unSubscribeUrl, e);
                    }
                    handlerSpecific.sendErrorResponse(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR, responseHeaderMap,
                        responseBodyMap, null);
                }
            }

            // Update service metadata
            eventMeshHTTPServer.getSubscriptionManager().updateMetaData();
        }
    }

    @Override
    public String[] paths() {
        return new String[]{RequestURI.UNSUBSCRIBE_LOCAL.getRequestURI()};
    }

    private void registerClient(final HttpEventWrapper requestWrapper,
                                final String consumerGroup,
                                final List<String> topicList, final String url) {
        Objects.requireNonNull(requestWrapper, "requestWrapper can not be null");
        Objects.requireNonNull(consumerGroup, "consumerGroup can not be null");
        Objects.requireNonNull(topicList, "topicList can not be null");
        Objects.requireNonNull(url, "url can not be null");

        final Map<String, Object> requestHeaderMap = requestWrapper.getSysHeaderMap();
        for (final String topic : topicList) {
            final Client client = new Client();
            client.setEnv(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.ENV.getKey()).toString());
            client.setIdc(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IDC.getKey()).toString());
            client.setSys(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.SYS.getKey()).toString());
            client.setIp(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.IP.getKey()).toString());
            client.setPid(requestHeaderMap.get(ProtocolKey.ClientInstanceKey.PID.getKey()).toString());
            client.setConsumerGroup(consumerGroup);
            client.setTopic(topic);
            client.setUrl(url);
            client.setLastUpTime(new Date());

            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();

            List<Client> localClients =
                eventMeshHTTPServer.getSubscriptionManager().getLocalClientInfoMapping().computeIfAbsent(groupTopicKey,
                    list -> new ArrayList<>());

            boolean isContains = false;
            for (final Client localClient : localClients) {
                if (StringUtils.equals(localClient.getUrl(), client.getUrl())) {
                    isContains = true;
                    localClient.setLastUpTime(client.getLastUpTime());
                    break;
                }
            }
            if (!isContains) {
                localClients.add(client);
            }

        }
    }
}
