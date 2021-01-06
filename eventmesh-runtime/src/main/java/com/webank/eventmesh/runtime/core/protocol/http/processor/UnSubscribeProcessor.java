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

package com.webank.eventmesh.runtime.core.protocol.http.processor;

import com.alibaba.fastjson.JSONObject;
import com.webank.eventmesh.common.protocol.http.body.client.SubscribeResponseBody;
import com.webank.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import com.webank.eventmesh.common.protocol.http.body.client.UnSubscribeResponseBody;
import com.webank.eventmesh.common.protocol.http.common.ProxyRetCode;
import com.webank.eventmesh.common.protocol.http.header.client.SubscribeResponseHeader;
import com.webank.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import com.webank.eventmesh.common.protocol.http.header.client.UnSubscribeResponseHeader;
import com.webank.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import com.webank.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import com.webank.eventmesh.runtime.core.consumergroup.event.ConsumerGroupStateEvent;
import com.webank.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import com.webank.eventmesh.runtime.core.protocol.http.consumer.ConsumerGroupManager;
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import com.webank.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import com.webank.eventmesh.runtime.boot.ProxyHTTPServer;
import com.webank.eventmesh.runtime.constants.ProxyConstants;
import com.webank.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import com.webank.eventmesh.common.IPUtil;
import com.webank.eventmesh.common.command.HttpCommand;
import com.webank.eventmesh.common.protocol.http.common.RequestCode;
import com.webank.eventmesh.runtime.util.ProxyUtil;
import com.webank.eventmesh.runtime.util.RemotingHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UnSubscribeProcessor implements HttpRequestProcessor {

    public Logger httpLogger = LoggerFactory.getLogger("http");

    private ProxyHTTPServer proxyHTTPServer;

    public UnSubscribeProcessor(ProxyHTTPServer proxyHTTPServer) {
        this.proxyHTTPServer = proxyHTTPServer;
    }

    @Override
    public void processRequest(ChannelHandlerContext ctx, AsyncContext<HttpCommand> asyncContext) throws Exception {
        HttpCommand responseProxyCommand;
        httpLogger.info("cmd={}|{}|client2proxy|from={}|to={}", RequestCode.get(Integer.valueOf(asyncContext.getRequest().getRequestCode())),
                ProxyConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());
        UnSubscribeRequestHeader unSubscribeRequestHeader = (UnSubscribeRequestHeader) asyncContext.getRequest().getHeader();
        UnSubscribeRequestBody unSubscribeRequestBody = (UnSubscribeRequestBody) asyncContext.getRequest().getBody();

        UnSubscribeResponseHeader unSubscribeResponseHeader =
                UnSubscribeResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), proxyHTTPServer.getProxyConfiguration().proxyCluster,
                        IPUtil.getLocalAddress(), proxyHTTPServer.getProxyConfiguration().proxyEnv,
                        proxyHTTPServer.getProxyConfiguration().proxyRegion,
                        proxyHTTPServer.getProxyConfiguration().proxyDCN, proxyHTTPServer.getProxyConfiguration().proxyIDC);


        //validate header
        if (StringUtils.isBlank(unSubscribeRequestHeader.getIdc())
                || StringUtils.isBlank(unSubscribeRequestHeader.getDcn())
                || StringUtils.isBlank(unSubscribeRequestHeader.getPid())
                || !StringUtils.isNumeric(unSubscribeRequestHeader.getPid())
                || StringUtils.isBlank(unSubscribeRequestHeader.getSys())) {
            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    unSubscribeResponseHeader,
                    UnSubscribeResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_HEADER_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }

        //validate body
        if (StringUtils.isBlank(unSubscribeRequestBody.getUrl())
                || CollectionUtils.isEmpty(unSubscribeRequestBody.getTopics())) {

            responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                    unSubscribeResponseHeader,
                    UnSubscribeResponseBody.buildBody(ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getRetCode(), ProxyRetCode.PROXY_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseProxyCommand);
            return;
        }
        String env = unSubscribeRequestHeader.getEnv();
        String dcn = unSubscribeRequestHeader.getDcn();
        String idc = unSubscribeRequestHeader.getIdc();
        String sys = unSubscribeRequestHeader.getSys();
        String ip = unSubscribeRequestHeader.getIp();
        String pid = unSubscribeRequestHeader.getPid();
        String consumerGroup = ProxyUtil.buildClientGroup(unSubscribeRequestHeader.getSys(),
                unSubscribeRequestHeader.getDcn());
        String unSubscribeUrl = unSubscribeRequestBody.getUrl();
        List<String> unSubTopicList = unSubscribeRequestBody.getTopics();

        final CompleteHandler<HttpCommand> handler = new CompleteHandler<HttpCommand>() {
            @Override
            public void onResponse(HttpCommand httpCommand) {
                try {
                    if (httpLogger.isDebugEnabled()) {
                        httpLogger.debug("{}", httpCommand);
                    }
                    proxyHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    proxyHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                }
            }
        };

        synchronized (proxyHTTPServer.localClientInfoMapping) {
            boolean isChange = true;
            for (String unSubTopic : unSubTopicList) {
                List<Client> groupTopicClients = proxyHTTPServer.localClientInfoMapping.get(consumerGroup + "@" + unSubTopic);
                Iterator<Client> clientIterator = groupTopicClients.iterator();
                while (clientIterator.hasNext()) {
                    Client client = clientIterator.next();
                    if (StringUtils.equals(client.ip, ip)) {
                        httpLogger.warn("client {} start unsubscribe", JSONObject.toJSONString(client));
                        clientIterator.remove();
                    }
                }
                if (groupTopicClients.size() > 0) {
                    //change url
                    Map<String, List<String>> idcUrls = new HashMap<>();
                    Set<String> clientUrls = new HashSet<>();
                    for (Client client : groupTopicClients) {
                        // 去除订阅的url
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
                    synchronized (proxyHTTPServer.localConsumerGroupMapping) {
                        ConsumerGroupConf consumerGroupConf = proxyHTTPServer.localConsumerGroupMapping.get(consumerGroup);
                        Map<String, ConsumerGroupTopicConf> map = consumerGroupConf.getConsumerGroupTopicConf();
                        for (String topicKey : map.keySet()) {
                            // 仅修改去订阅的topic
                            if (StringUtils.equals(unSubTopic, topicKey)) {
                                ConsumerGroupTopicConf latestTopicConf = new ConsumerGroupTopicConf();
                                latestTopicConf.setConsumerGroup(consumerGroup);
                                latestTopicConf.setTopic(unSubTopic);
                                latestTopicConf.setUrls(clientUrls);

                                latestTopicConf.setIdcUrls(idcUrls);

                                map.put(unSubTopic, latestTopicConf);
                            }
                        }
                        proxyHTTPServer.localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
                    }
                } else {
                    isChange = false;
                    break;
                }
            }
            long startTime = System.currentTimeMillis();
            if (isChange) {
                try {
                    proxyHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup, proxyHTTPServer.localConsumerGroupMapping.get(consumerGroup),
                            proxyHTTPServer.localConsumerGroupMapping);

                    responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                            ProxyRetCode.SUCCESS.getRetCode(), ProxyRetCode.SUCCESS.getErrMsg());
                    asyncContext.onComplete(responseProxyCommand, handler);

                } catch (Exception e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody.buildBody(ProxyRetCode.PROXY_UNSUBSCRIBE_ERR.getRetCode(),
                                    ProxyRetCode.PROXY_UNSUBSCRIBE_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error("message|proxy2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            JSONObject.toJSONString(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    proxyHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
                    proxyHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            } else {
                //remove
                try {
                    proxyHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup, null, proxyHTTPServer.localConsumerGroupMapping);
                    responseProxyCommand = asyncContext.getRequest().createHttpCommandResponse(
                            ProxyRetCode.SUCCESS.getRetCode(), ProxyRetCode.SUCCESS.getErrMsg());
                    asyncContext.onComplete(responseProxyCommand, handler);
                    // 清理ClientInfo
                    proxyHTTPServer.localClientInfoMapping.keySet().removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // 清理ConsumerGroupInfo
                    proxyHTTPServer.localConsumerGroupMapping.keySet().removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody.buildBody(ProxyRetCode.PROXY_UNSUBSCRIBE_ERR.getRetCode(),
                                    ProxyRetCode.PROXY_UNSUBSCRIBE_ERR.getErrMsg() + ProxyUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error("message|proxy2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            JSONObject.toJSONString(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    proxyHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
                    proxyHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            }
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }

    /**
     * notify ConsumerManager 组级别
     */
    private void notifyConsumerManager(String consumerGroup, ConsumerGroupConf latestConsumerGroupConfig,
                                       ConcurrentHashMap<String, ConsumerGroupConf> localConsumerGroupMapping) throws Exception {
        ConsumerGroupManager cgm = proxyHTTPServer.getConsumerManager().getConsumer(consumerGroup);
        if (cgm == null) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.NEW;
            notification.consumerGroup = consumerGroup;
            notification.consumerGroupConfig = latestConsumerGroupConfig;
            proxyHTTPServer.getEventBus().post(notification);
            return;
        }

        if (!latestConsumerGroupConfig.equals(cgm.getConsumerGroupConfig())) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.CHANGE;
            notification.consumerGroup = consumerGroup;
            notification.consumerGroupConfig = latestConsumerGroupConfig;
            proxyHTTPServer.getEventBus().post(notification);
            return;
        }

        if (latestConsumerGroupConfig == null) {
            ConsumerGroupStateEvent notification = new ConsumerGroupStateEvent();
            notification.action = ConsumerGroupStateEvent.ConsumerGroupStateAction.DELETE;
            notification.consumerGroup = consumerGroup;
            proxyHTTPServer.getEventBus().post(notification);
        }
        return;
    }
}
