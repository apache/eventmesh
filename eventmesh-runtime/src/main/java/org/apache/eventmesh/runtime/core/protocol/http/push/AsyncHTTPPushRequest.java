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

package org.apache.eventmesh.runtime.core.protocol.http.push;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.exception.JsonException;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.HttpEventWrapper;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HandleMsgContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.WebhookUtil;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;

public class AsyncHTTPPushRequest extends AbstractHTTPPushRequest {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger logger = LoggerFactory.getLogger(this.getClass());
    public String currPushUrl;
    private Map<String, Set<AbstractHTTPPushRequest>> waitingRequests;

    public AsyncHTTPPushRequest(HandleMsgContext handleMsgContext,
                                Map<String, Set<AbstractHTTPPushRequest>> waitingRequests) {
        super(handleMsgContext);
        this.waitingRequests = waitingRequests;
    }

    @Override
    public void tryHTTPRequest() {

        currPushUrl = getUrl();

        if (StringUtils.isBlank(currPushUrl)) {
            return;
        }

        HttpPost builder = new HttpPost(currPushUrl);

        String requestCode = "";

        if (SubscriptionType.SYNC.equals(handleMsgContext.getSubscriptionItem().getType())) {
            requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode());
        } else {
            requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode());
        }

        builder.addHeader(ProtocolKey.REQUEST_CODE, requestCode);
        builder.addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        builder.addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
            handleMsgContext.getEventMeshHTTPServer()
                .getEventMeshHttpConfiguration().eventMeshCluster);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, IPUtils.getLocalAddress());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
            handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration().eventMeshEnv);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
            handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration().eventMeshIDC);

        CloudEvent event = CloudEventBuilder.from(handleMsgContext.getEvent())
            .withExtension(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP,
                String.valueOf(System.currentTimeMillis()))
            .withExtension(EventMeshConstants.RSP_URL, currPushUrl)
            .withExtension(EventMeshConstants.RSP_GROUP, handleMsgContext.getConsumerGroup())
            .build();
        handleMsgContext.setEvent(event);

        String content = "";
        try {
            String protocolType = Objects.requireNonNull(event.getExtension(Constants.PROTOCOL_TYPE)).toString();

            ProtocolAdaptor<ProtocolTransportObject> protocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);

            ProtocolTransportObject protocolTransportObject =
                protocolAdaptor.fromCloudEvent(handleMsgContext.getEvent());
            if (protocolTransportObject instanceof HttpCommand) {
                content = ((HttpCommand) protocolTransportObject).getBody().toMap().get("content").toString();
            } else {
                HttpEventWrapper httpEventWrapper = (HttpEventWrapper) protocolTransportObject;
                Map<String, Object> sysHeaderMap = httpEventWrapper.getSysHeaderMap();
                content = new String(httpEventWrapper.getBody(), StandardCharsets.UTF_8);
                for (String header : sysHeaderMap.keySet()) {
                    if (!builder.containsHeader(header)) {
                        builder.addHeader(header, sysHeaderMap.get(header).toString());
                    }
                }
            }

        } catch (Exception ex) {
            return;
        }

        List<NameValuePair> body = new ArrayList<>();
        body.add(new BasicNameValuePair(PushMessageRequestBody.CONTENT, content));
        if (StringUtils.isBlank(handleMsgContext.getBizSeqNo())) {
            body.add(new BasicNameValuePair(PushMessageRequestBody.BIZSEQNO,
                RandomStringUtils.generateNum(20)));
        } else {
            body.add(new BasicNameValuePair(PushMessageRequestBody.BIZSEQNO,
                handleMsgContext.getBizSeqNo()));
        }
        if (StringUtils.isBlank(handleMsgContext.getUniqueId())) {
            body.add(new BasicNameValuePair(PushMessageRequestBody.UNIQUEID,
                RandomStringUtils.generateNum(20)));
        } else {
            body.add(new BasicNameValuePair(PushMessageRequestBody.UNIQUEID,
                handleMsgContext.getUniqueId()));
        }

        body.add(new BasicNameValuePair(PushMessageRequestBody.RANDOMNO,
            handleMsgContext.getMsgRandomNo()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.TOPIC, handleMsgContext.getTopic()));

        body.add(new BasicNameValuePair(PushMessageRequestBody.EXTFIELDS,
            JsonUtils.serialize(EventMeshUtil.getEventProp(handleMsgContext.getEvent()))));

        HttpEntity httpEntity = new UrlEncodedFormEntity(body, StandardCharsets.UTF_8);

        builder.setEntity(httpEntity);

        // for CloudEvents Webhook spec
        String urlAuthType = handleMsgContext.getConsumerGroupConfig().getConsumerGroupTopicConf()
            .get(handleMsgContext.getTopic()).getHttpAuthTypeMap().get(currPushUrl);

        WebhookUtil.setWebhookHeaders(builder, httpEntity.getContentType().getValue(), eventMeshHttpConfiguration.eventMeshWebhookOrigin,
            urlAuthType);

        eventMeshHTTPServer.metrics.getSummaryMetrics().recordPushMsg();

        this.lastPushTime = System.currentTimeMillis();

        addToWaitingMap(this);

        cmdLogger.info("cmd={}|eventMesh2client|from={}|to={}", requestCode,
            IPUtils.getLocalAddress(), currPushUrl);

        try {
            eventMeshHTTPServer.httpClientPool.getClient().execute(builder, new ResponseHandler<Object>() {
                @Override
                public Object handleResponse(HttpResponse response) {
                    removeWaitingMap(AsyncHTTPPushRequest.this);
                    long cost = System.currentTimeMillis() - lastPushTime;
                    eventMeshHTTPServer.metrics.getSummaryMetrics().recordHTTPPushTimeCost(cost);

                    if (processResponseStatus(response.getStatusLine().getStatusCode(), response)) {
                        // this is successful response, process response payload
                        String res = "";
                        try {
                            res = EntityUtils.toString(response.getEntity(),
                                Charset.forName(EventMeshConstants.DEFAULT_CHARSET));
                        } catch (IOException e) {
                            handleMsgContext.finish();
                            return new Object();
                        }
                        ClientRetCode result = processResponseContent(res);
                        messageLogger.info(
                            "message|eventMesh2client|{}|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}",
                            result, currPushUrl, handleMsgContext.getTopic(),
                            handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);
                        if (result == ClientRetCode.OK || result == ClientRetCode.REMOTE_OK) {
                            complete();
                            if (isComplete()) {
                                handleMsgContext.finish();
                            }
                        } else if (result == ClientRetCode.RETRY) {
                            delayRetry();
                            if (isComplete()) {
                                handleMsgContext.finish();
                            }
                        } else if (result == ClientRetCode.NOLISTEN) {
                            delayRetry();
                            if (isComplete()) {
                                handleMsgContext.finish();
                            }
                        } else if (result == ClientRetCode.FAIL) {
                            complete();
                            if (isComplete()) {
                                handleMsgContext.finish();
                            }
                        }
                    } else {
                        eventMeshHTTPServer.metrics.getSummaryMetrics().recordHttpPushMsgFailed();
                        messageLogger.info(
                            "message|eventMesh2client|exception|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}", currPushUrl, handleMsgContext.getTopic(),
                            handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);

                        if (isComplete()) {
                            handleMsgContext.finish();
                        }
                    }
                    return new Object();
                }
            });

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("message|eventMesh2client|url={}|topic={}|event={}", currPushUrl,
                    handleMsgContext.getTopic(),
                    handleMsgContext.getEvent());
            } else {
                messageLogger
                    .info("message|eventMesh2client|url={}|topic={}|bizSeqNo={}|uniqueId={}",
                        currPushUrl, handleMsgContext.getTopic(),
                        handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId());
            }
        } catch (IOException e) {
            messageLogger.error("push2client err", e);
            removeWaitingMap(this);
            delayRetry();
            if (isComplete()) {
                handleMsgContext.finish();
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("asyncPushRequest={")
            .append("bizSeqNo=").append(handleMsgContext.getBizSeqNo())
            .append(",startIdx=").append(startIdx)
            .append(",retryTimes=").append(retryTimes)
            .append(",uniqueId=").append(handleMsgContext.getUniqueId())
            .append(",executeTime=")
            .append(DateFormatUtils.format(executeTime, Constants.DATE_FORMAT))
            .append(",lastPushTime=")
            .append(DateFormatUtils.format(lastPushTime, Constants.DATE_FORMAT))
            .append(",createTime=")
            .append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT)).append("}");
        return sb.toString();
    }

    boolean processResponseStatus(int httpStatus, HttpResponse httpResponse) {
        if (httpStatus == HttpStatus.SC_OK || httpStatus == HttpStatus.SC_CREATED
            || httpStatus == HttpStatus.SC_NO_CONTENT || httpStatus == HttpStatus.SC_ACCEPTED) {
            // success http response
            return true;
        } else if (httpStatus == 429) {
            // failed with customer retry interval

            // Response Status code is 429 Too Many Requests
            // retry after the time specified by the header
            Optional<Header> optHeader = Arrays.stream(httpResponse.getHeaders("Retry-After")).findAny();
            if (optHeader.isPresent() && StringUtils.isNumeric(optHeader.get().getValue())) {
                delayRetry(Long.parseLong(optHeader.get().getValue()));
            }
            return false;
        } else if (httpStatus == HttpStatus.SC_GONE || httpStatus == HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE) {
            // failed with no retry
            return false;
        }

        // failed with default retry
        delayRetry();
        return false;
    }

    ClientRetCode processResponseContent(String content) {
        if (StringUtils.isBlank(content)) {
            return ClientRetCode.FAIL;
        }

        try {
            Map<String, Object> ret =
                JsonUtils.deserialize(content, new TypeReference<Map<String, Object>>() {
                });
            Integer retCode = (Integer) ret.get("retCode");
            if (retCode != null && ClientRetCode.contains(retCode)) {
                return ClientRetCode.get(retCode);
            }

            return ClientRetCode.FAIL;
        } catch (NumberFormatException e) {
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{}, httpResponse:{}", currPushUrl,
                handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            return ClientRetCode.FAIL;
        } catch (JsonException e) {
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", currPushUrl,
                handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            return ClientRetCode.FAIL;
        } catch (Throwable t) {
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", currPushUrl,
                handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            return ClientRetCode.FAIL;
        }
    }

    private void addToWaitingMap(AsyncHTTPPushRequest request) {
        if (waitingRequests.containsKey(request.handleMsgContext.getConsumerGroup())) {
            waitingRequests.get(request.handleMsgContext.getConsumerGroup()).add(request);
            return;
        }
        waitingRequests
            .put(request.handleMsgContext.getConsumerGroup(), Sets.newConcurrentHashSet());
        waitingRequests.get(request.handleMsgContext.getConsumerGroup()).add(request);
    }

    private void removeWaitingMap(AsyncHTTPPushRequest request) {
        if (waitingRequests.containsKey(request.handleMsgContext.getConsumerGroup())) {
            waitingRequests.get(request.handleMsgContext.getConsumerGroup()).remove(request);
        }
    }

    @Override
    public boolean retry() {
        tryHTTPRequest();
        return true;
    }
}
