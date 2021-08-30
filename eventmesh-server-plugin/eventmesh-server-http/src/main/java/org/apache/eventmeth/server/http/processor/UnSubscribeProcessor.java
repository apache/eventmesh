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

package org.apache.eventmeth.server.http.processor;

import com.alibaba.fastjson.JSONObject;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.utils.IPUtil;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeResponseHeader;
import org.apache.eventmeth.server.http.EventMeshHTTPServer;
import org.apache.eventmeth.server.http.config.HttpProtocolConstants;
import org.apache.eventmeth.server.http.consumer.ConsumerGroupConf;
import org.apache.eventmeth.server.http.consumer.ConsumerGroupTopicConf;
import org.apache.eventmeth.server.http.model.AsyncContext;
import org.apache.eventmeth.server.http.model.Client;
import org.apache.eventmeth.server.http.model.CompleteHandler;
import org.apache.eventmeth.server.http.utils.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UnSubscribeProcessor implements HttpRequestProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private EventMeshHTTPServer eventMeshHTTPServer;

    public UnSubscribeProcessor(EventMeshHTTPServer eventMeshHTTPServer) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseEventMeshCommand;
        httpLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                HttpProtocolConstants.PROTOCOL_HTTP,
                HttpUtils.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());
        UnSubscribeRequestHeader unSubscribeRequestHeader = (UnSubscribeRequestHeader) asyncContext.getRequest().getHeader();
        UnSubscribeRequestBody unSubscribeRequestBody = (UnSubscribeRequestBody) asyncContext.getRequest().getBody();

        UnSubscribeResponseHeader unSubscribeResponseHeader =
                UnSubscribeResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), CommonConfiguration.eventMeshCluster,
                        IPUtil.getLocalAddress(), CommonConfiguration.eventMeshEnv, CommonConfiguration.eventMeshIDC);

        //validate header
        if (StringUtils.isBlank(unSubscribeRequestHeader.getIdc())
                || StringUtils.isBlank(unSubscribeRequestHeader.getPid())
                || !StringUtils.isNumeric(unSubscribeRequestHeader.getPid())
                || StringUtils.isBlank(unSubscribeRequestHeader.getSys())) {
            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    unSubscribeResponseHeader,
                    UnSubscribeResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(unSubscribeRequestBody.getUrl())
                || CollectionUtils.isEmpty(unSubscribeRequestBody.getTopics())
                || StringUtils.isBlank(unSubscribeRequestBody.getConsumerGroup())) {

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    unSubscribeResponseHeader,
                    UnSubscribeResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }
        String env = unSubscribeRequestHeader.getEnv();
        String idc = unSubscribeRequestHeader.getIdc();
        String sys = unSubscribeRequestHeader.getSys();
        String ip = unSubscribeRequestHeader.getIp();
        String pid = unSubscribeRequestHeader.getPid();
        String consumerGroup = unSubscribeRequestBody.getConsumerGroup();
        String unSubscribeUrl = unSubscribeRequestBody.getUrl();
        List<String> unSubTopicList = unSubscribeRequestBody.getTopics();

        CompleteHandler<HttpCommand> handler = httpCommand -> {
            try {
                if (httpLogger.isDebugEnabled()) {
                    httpLogger.debug("{}", httpCommand);
                }
                ctx.writeAndFlush(httpCommand.httpResponse()).addListener((ChannelFutureListener) f -> {
                    if (!f.isSuccess()) {
                        httpLogger.warn("send response to [{}] fail, will close this channel", HttpUtils.parseChannelRemoteAddr(f.channel()));
                        f.channel().close();
                        return;
                    }
                });
                eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
            } catch (Exception ex) {
            }
        };

        synchronized (eventMeshHTTPServer.getLocalClientInfoMapping()) {
            boolean isChange = true;
            registerClient(unSubscribeRequestHeader, consumerGroup, unSubTopicList, unSubscribeUrl);

            ConcurrentHashMap<String, List<Client>> localClientInfoMapping = eventMeshHTTPServer.getLocalClientInfoMapping();
            for (String unSubTopic : unSubTopicList) {
                List<Client> groupTopicClients = localClientInfoMapping.get(consumerGroup + "@" + unSubTopic);
                Iterator<Client> clientIterator = groupTopicClients.iterator();
                while (clientIterator.hasNext()) {
                    Client client = clientIterator.next();
                    if (StringUtils.equals(client.pid, pid) && StringUtils.equals(client.url, unSubscribeUrl)) {
                        httpLogger.warn("client {} start unsubscribe", JSONObject.toJSONString(client));
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
                                idcUrls.get(client.idc).add(StringUtils.deleteWhitespace(client.url));
                            } else {
                                List<String> urls = new ArrayList<>();
                                urls.add(client.url);
                                idcUrls.put(client.idc, urls);
                            }
                        }

                    }
                    ConcurrentHashMap<String, ConsumerGroupConf> localConsumerGroupMapping = eventMeshHTTPServer.getLocalConsumerGroupMapping();
                    synchronized (localConsumerGroupMapping) {
                        ConsumerGroupConf consumerGroupConf = localConsumerGroupMapping.get(consumerGroup);
                        Map<String, ConsumerGroupTopicConf> map = consumerGroupConf.getConsumerGroupTopicConf();
                        for (String topicKey : map.keySet()) {
                            // only modify the topic to subscribe
                            if (StringUtils.equals(unSubTopic, topicKey)) {
                                ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                                latestTopicConf.setConsumerGroup(consumerGroup);
                                latestTopicConf.setTopic(unSubTopic);
                                latestTopicConf.setSubscriptionItem(map.get(topicKey).getSubscriptionItem());
                                latestTopicConf.setUrls(clientUrls);

                                latestTopicConf.setIdcUrls(idcUrls);

                                map.put(unSubTopic, latestTopicConf);
                            }
                        }
                        localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
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
                            eventMeshHTTPServer.getLocalConsumerGroupMapping().get(consumerGroup), eventMeshHTTPServer.getLocalConsumerGroupMapping());

                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg());
                    asyncContext.onComplete(responseEventMeshCommand, handler);

                } catch (Exception e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody.buildBody(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + e.getLocalizedMessage()));
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            JSONObject.toJSONString(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgFailed();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            } else {
                //remove
                try {
                    eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup, null, eventMeshHTTPServer.getLocalConsumerGroupMapping());
                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg());
                    asyncContext.onComplete(responseEventMeshCommand, handler);
                    // clean ClientInfo
                    eventMeshHTTPServer.getLocalClientInfoMapping().keySet().removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // clean ConsumerGroupInfo
                    eventMeshHTTPServer.getLocalConsumerGroupMapping().keySet().removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody.buildBody(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + e.getLocalizedMessage()));
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            JSONObject.toJSONString(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgFailed();
                    eventMeshHTTPServer.getMetricsServer().summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            }
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    private void registerClient(UnSubscribeRequestHeader unSubscribeRequestHeader, String consumerGroup,
                                        List<String> topicList, String url) {
        for(String topic: topicList) {
            Client client = new Client();
            client.env = unSubscribeRequestHeader.getEnv();
            client.idc = unSubscribeRequestHeader.getIdc();
            client.sys = unSubscribeRequestHeader.getSys();
            client.ip = unSubscribeRequestHeader.getIp();
            client.pid = unSubscribeRequestHeader.getPid();
            client.consumerGroup = consumerGroup;
            client.topic = topic;
            client.url = url;
            client.lastUpTime = new Date();

            String groupTopicKey = client.consumerGroup + "@" + client.topic;
            ConcurrentHashMap<String, List<Client>> localClientInfoMapping = eventMeshHTTPServer.getLocalClientInfoMapping();
            if (localClientInfoMapping.containsKey(groupTopicKey)) {
                List<Client> localClients = localClientInfoMapping.get(groupTopicKey);
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
                localClientInfoMapping.put(groupTopicKey, clients);
            }
        }
    }
}
