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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.fastjson.JSONObject;

import io.netty.channel.ChannelHandlerContext;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.command.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeRequestBody;
import org.apache.eventmesh.common.protocol.http.body.client.UnSubscribeResponseBody;
import org.apache.eventmesh.common.protocol.http.common.EventMeshRetCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeRequestHeader;
import org.apache.eventmesh.common.protocol.http.header.client.UnSubscribeResponseHeader;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.consumergroup.event.ConsumerGroupStateEvent;
import org.apache.eventmesh.runtime.core.protocol.http.async.AsyncContext;
import org.apache.eventmesh.runtime.core.protocol.http.async.CompleteHandler;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.ConsumerGroupManager;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.Client;
import org.apache.eventmesh.runtime.core.protocol.http.processor.inf.HttpRequestProcessor;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                EventMeshConstants.PROTOCOL_HTTP,
                RemotingHelper.parseChannelRemoteAddr(ctx.channel()), IPUtil.getLocalAddress());
        UnSubscribeRequestHeader unSubscribeRequestHeader = (UnSubscribeRequestHeader) asyncContext.getRequest().getHeader();
        UnSubscribeRequestBody unSubscribeRequestBody = (UnSubscribeRequestBody) asyncContext.getRequest().getBody();

        UnSubscribeResponseHeader unSubscribeResponseHeader =
                UnSubscribeResponseHeader.buildHeader(Integer.valueOf(asyncContext.getRequest().getRequestCode()), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshCluster,
                        IPUtil.getLocalAddress(), eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshEnv,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshRegion,
                        eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshDCN, eventMeshHTTPServer.getEventMeshHttpConfiguration().eventMeshIDC);


        //validate header
        if (StringUtils.isBlank(unSubscribeRequestHeader.getIdc())
                || StringUtils.isBlank(unSubscribeRequestHeader.getDcn())
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
                || CollectionUtils.isEmpty(unSubscribeRequestBody.getTopics())) {

            responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                    unSubscribeResponseHeader,
                    UnSubscribeResponseBody.buildBody(EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getRetCode(), EventMeshRetCode.EVENTMESH_PROTOCOL_BODY_ERR.getErrMsg()));
            asyncContext.onComplete(responseEventMeshCommand);
            return;
        }
        String env = unSubscribeRequestHeader.getEnv();
        String dcn = unSubscribeRequestHeader.getDcn();
        String idc = unSubscribeRequestHeader.getIdc();
        String sys = unSubscribeRequestHeader.getSys();
        String ip = unSubscribeRequestHeader.getIp();
        String pid = unSubscribeRequestHeader.getPid();
        String consumerGroup = EventMeshUtil.buildClientGroup(unSubscribeRequestHeader.getSys(),
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
                    eventMeshHTTPServer.sendResponse(ctx, httpCommand.httpResponse());
                    eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPReqResTimeCost(System.currentTimeMillis() - asyncContext.getRequest().getReqTime());
                } catch (Exception ex) {
                }
            }
        };

        synchronized (eventMeshHTTPServer.localClientInfoMapping) {
            boolean isChange = true;
            for (String unSubTopic : unSubTopicList) {
                List<Client> groupTopicClients = eventMeshHTTPServer.localClientInfoMapping.get(consumerGroup + "@" + unSubTopic);
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
                    synchronized (eventMeshHTTPServer.localConsumerGroupMapping) {
                        ConsumerGroupConf consumerGroupConf = eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup);
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
                        eventMeshHTTPServer.localConsumerGroupMapping.put(consumerGroup, consumerGroupConf);
                    }
                } else {
                    isChange = false;
                    break;
                }
            }
            long startTime = System.currentTimeMillis();
            if (isChange) {
                try {
                    eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup, eventMeshHTTPServer.localConsumerGroupMapping.get(consumerGroup),
                            eventMeshHTTPServer.localConsumerGroupMapping);

                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg());
                    asyncContext.onComplete(responseEventMeshCommand, handler);

                } catch (Exception e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody.buildBody(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            JSONObject.toJSONString(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            } else {
                //remove
                try {
                    eventMeshHTTPServer.getConsumerManager().notifyConsumerManager(consumerGroup, null, eventMeshHTTPServer.localConsumerGroupMapping);
                    responseEventMeshCommand = asyncContext.getRequest().createHttpCommandResponse(
                            EventMeshRetCode.SUCCESS.getRetCode(), EventMeshRetCode.SUCCESS.getErrMsg());
                    asyncContext.onComplete(responseEventMeshCommand, handler);
                    // 清理ClientInfo
                    eventMeshHTTPServer.localClientInfoMapping.keySet().removeIf(s -> StringUtils.contains(s, consumerGroup));
                    // 清理ConsumerGroupInfo
                    eventMeshHTTPServer.localConsumerGroupMapping.keySet().removeIf(s -> StringUtils.equals(consumerGroup, s));
                } catch (Exception e) {
                    HttpCommand err = asyncContext.getRequest().createHttpCommandResponse(
                            unSubscribeResponseHeader,
                            UnSubscribeResponseBody.buildBody(EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getRetCode(),
                                    EventMeshRetCode.EVENTMESH_UNSUBSCRIBE_ERR.getErrMsg() + EventMeshUtil.stackTrace(e, 2)));
                    asyncContext.onComplete(err);
                    long endTime = System.currentTimeMillis();
                    httpLogger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                            endTime - startTime,
                            JSONObject.toJSONString(unSubscribeRequestBody.getTopics()),
                            unSubscribeRequestBody.getUrl(), e);
                    eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgFailed();
                    eventMeshHTTPServer.metrics.summaryMetrics.recordSendMsgCost(endTime - startTime);
                }
            }
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
