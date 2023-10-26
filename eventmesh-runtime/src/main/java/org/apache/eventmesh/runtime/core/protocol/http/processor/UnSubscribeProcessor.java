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

import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeResponseHeader;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.metrics.api.model.HttpSummaryMetrics;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumer.SubscriptionManager;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UnSubscribeProcessor implements HttpRequestProcessor {

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    public UnSubscribeProcessor(final EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(final ChannelHandlerContext ctx, final AsyncContext<HttpCommand> asyncContext)
        throws Exception {
        HttpCommand responseEventMeshCommand;
        final HttpCommand request = asyncContext.getRequest();
        final String localAddress = IPUtils.getLocalAddress();

        LogUtils.info(log, "cmd={}|{}|client2eventMesh|from={}|to={}",
            RequestCode.get(Integer.valueOf(request.getRequestCode())),
            EventMeshConstants.PROTOCOL_HTTP,
            RemotingHelper.parseChannelRemoteAddr(ctx.channel()), localAddress);

        final UnSubscribeRequestHeader unSubscribeRequestHeader = (UnSubscribeRequestHeader) request.getHeader();
        final UnSubscribeRequestBody unSubscribeRequestBody = (UnSubscribeRequestBody) request.getBody();
        EventMeshHTTPConfiguration eventMeshHttpConfiguration = eventMeshHTTPServer.getEventMeshHttpConfiguration();
        final UnSubscribeResponseHeader unSubscribeResponseHeader =
            UnSubscribeResponseHeader
                .buildHeader(Integer.valueOf(request.getRequestCode()),
                    eventMeshHttpConfiguration.getEventMeshCluster(),
                    localAddress, eventMeshHttpConfiguration.getEventMeshEnv(),
                    eventMeshHttpConfiguration.getEventMeshIDC());

        // validate header
        if (StringUtils.isAnyBlank(unSubscribeRequestHeader.getIdc(), unSubscribeRequestHeader.getPid(),
            unSubscribeRequestHeader.getSys())
            || !StringUtils.isNumeric(unSubscribeRequestHeader.getPid())) {
            completeResponse(request, asyncContext, unSubscribeResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR, null, UnSubscribeResponseBody.class);
            return;
        }

        // validate body
        if (StringUtils.isAnyBlank(unSubscribeRequestBody.getUrl(), unSubscribeRequestBody.getConsumerGroup())
            || CollectionUtils.isEmpty(unSubscribeRequestBody.getTopics())) {
            completeResponse(request, asyncContext, unSubscribeResponseHeader,
                EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR, null, UnSubscribeResponseBody.class);
            return;
        }

        final String pid = unSubscribeRequestHeader.getPid();
        final String consumerGroup = unSubscribeRequestBody.getConsumerGroup();
        final String unSubscribeUrl = unSubscribeRequestBody.getUrl();
        final List<String> unSubTopicList = unSubscribeRequestBody.getTopics();
        HttpSummaryMetrics summaryMetrics = eventMeshHTTPServer.getMetrics().getSummaryMetrics();
        final CompleteHandler<HttpCommand> handler = httpCommand -> {
            try {
                LogUtils.debug(log, "{}", httpCommand);
                eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - request.getReqTime());
            } catch (Exception ex) {
                // ignore
            }
        };

        SubscriptionManager subscriptionManager = eventMeshHTTPServer.getSubscriptionManager();
        ConcurrentHashMap<String, ConsumerGroupConf> localConsumerGroupMap =
            subscriptionManager.getLocalConsumerGroupMapping();
        synchronized (subscriptionManager.getLocalClientInfoMapping()) {
            boolean isChange = true;

            registerClient(unSubscribeRequestHeader, consumerGroup, unSubTopicList, unSubscribeUrl);

            for (final String unSubTopic : unSubTopicList) {
                final List<Client> groupTopicClients = subscriptionManager.getLocalClientInfoMapping()
                    .get(consumerGroup + "@" + unSubTopic);

                final Iterator<Client> clientIterator = groupTopicClients.iterator();
                while (clientIterator.hasNext()) {
                    final Client client = clientIterator.next();
                    if (StringUtils.equals(client.getPid(), pid)
                        && StringUtils.equals(client.getUrl(), unSubscribeUrl)) {
                        LogUtils.warn(log, "client {} start unsubscribe", JsonUtils.toJSONString(client));
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
                            idcUrls.computeIfAbsent(client.getIdc(), k -> new ArrayList<>());
                        }

                    }

                    synchronized (localConsumerGroupMap) {
                        final ConsumerGroupConf consumerGroupConf = localConsumerGroupMap.get(consumerGroup);

                        final Map<String, ConsumerGroupTopicConf> map = consumerGroupConf.getConsumerGroupTopicConf();
                        for (final Map.Entry<String, ConsumerGroupTopicConf> topicConf : map.entrySet()) {
                            // only modify the topic to subscribe
                            if (StringUtils.equals(unSubTopic, topicConf.getKey())) {
                                final ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                                latestTopicConf.setConsumerGroup(consumerGroup);
                                latestTopicConf.setTopic(unSubTopic);
                                latestTopicConf.setSubscriptionItem(topicConf.getValue().getSubscriptionItem());
                                latestTopicConf.setUrls(clientUrls);
                                latestTopicConf.setIdcUrls(idcUrls);
                                map.put(unSubTopic, latestTopicConf);
                            }
                        }
                        localConsumerGroupMap.put(consumerGroup, consumerGroupConf);
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
                        localConsumerGroupMap.get(consumerGroup));

                    responseEventMeshCommand = request.createHttpCommandResponse(EventMeshRetCode.SUCCESS);
                    asyncContext.onComplete(responseEventMeshCommand, handler);

                } catch (Exception e) {
                    completeResponse(request, asyncContext, unSubscribeResponseHeader,
                        EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR,
                        EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2),
                        UnSubscribeResponseBody.class);
                    final long endTime = System.currentTimeMillis();
                    log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|url={}",
                        endTime - startTime,
                        JsonUtils.toJSONString(unSubscribeRequestBody.getTopics()),
                        unSubscribeRequestBody.getUrl(), e);
                    summaryMetrics.recordSendMsgFailed();
                    summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            } else {
                // remove
                try {
                    eventMeshHTTPServer.getConsumerManager()
                        .notifyConsumerManager(consumerGroup, null);
                    responseEventMeshCommand =
                        request.createHttpCommandResponse(EventMeshRetCode.SUCCESS);
                    asyncContext.onComplete(responseEventMeshCommand, handler);
                    // clean ClientInfo
                    subscriptionManager.getLocalClientInfoMapping().keySet()
                        .removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // clean ConsumerGroupInfo
                    localConsumerGroupMap.keySet()
                        .removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    completeResponse(request, asyncContext, unSubscribeResponseHeader,
                        EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR,
                        EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2),
                        UnSubscribeResponseBody.class);
                    final long endTime = System.currentTimeMillis();
                    log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms"
                            + "|topic={}|url={}", endTime - startTime,
                        JsonUtils.toJSONString(unSubscribeRequestBody.getTopics()),
                        unSubscribeRequestBody.getUrl(), e);
                    summaryMetrics.recordSendMsgFailed();
                    summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            }
            eventMeshHTTPServer.getSubscriptionManager().updateMetaData();
        }
    }

    private void registerClient(final UnSubscribeRequestHeader unSubscribeRequestHeader,
        final String consumerGroup,
        final List<String> topicList,
        final String url) {
        for (final String topic : topicList) {
            final Client client = new Client();
            client.setEnv(unSubscribeRequestHeader.getEnv());
            client.setIdc(unSubscribeRequestHeader.getIdc());
            client.setSys(unSubscribeRequestHeader.getSys());
            client.setIp(unSubscribeRequestHeader.getIp());
            client.setPid(unSubscribeRequestHeader.getPid());
            client.setConsumerGroup(consumerGroup);
            client.setTopic(topic);
            client.setUrl(url);
            client.setLastUpTime(new Date());

            final String groupTopicKey = client.getConsumerGroup() + "@" + client.getTopic();
            ConcurrentHashMap<String, List<Client>> localClientInfoMap = eventMeshHTTPServer.getSubscriptionManager()
                .getLocalClientInfoMapping();
            if (localClientInfoMap.containsKey(groupTopicKey)) {
                final List<Client> localClients = localClientInfoMap.get(groupTopicKey);
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
            } else {
                final List<Client> clients = new ArrayList<>();
                clients.add(client);
                localClientInfoMap.put(groupTopicKey, clients);
            }
        }
    }
}
