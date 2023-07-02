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

import com.fasterxml.jackson.core.type.TypeReference;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
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
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.configuration.EventMeshHTTPConfiguration;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.RetryContext;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HandleMsgContext;
import org.apache.eventmesh.runtime.core.protocol.http.retry.HttpRetryer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.Lists;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.WebhookUtil;
import org.apache.http.*;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractHTTPPushRequest extends RetryContext {

    public static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    public static final Logger CMD_LOGGER = LoggerFactory.getLogger(EventMeshConstants.CMD);

    public static final Logger LOGGER = LoggerFactory.getLogger("AbstractHTTPPushRequest");

    public final EventMeshHTTPServer eventMeshHTTPServer;

    public final long createTime = System.currentTimeMillis();

    public long lastPushTime = System.currentTimeMillis();

    public final Map<String /** IDC*/, List<String>> urls;

    public final List<String> totalUrls;

    public volatile int startIdx;

    public final EventMeshHTTPConfiguration eventMeshHttpConfiguration;

    public final HttpRetryer retryer;

    public final int ttl;

    public final HandleMsgContext handleMsgContext;

    protected String currPushUrl;

    private final AtomicBoolean complete = new AtomicBoolean(Boolean.FALSE);

    public AbstractHTTPPushRequest(HandleMsgContext handleMsgContext) {
        this.eventMeshHTTPServer = handleMsgContext.getEventMeshHTTPServer();
        this.handleMsgContext = handleMsgContext;
        this.urls = handleMsgContext.getConsumeTopicConfig().getIdcUrls();
        this.totalUrls = Lists.newArrayList(handleMsgContext.getConsumeTopicConfig().getUrls());
        this.eventMeshHttpConfiguration = handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration();
        this.retryer = handleMsgContext.getEventMeshHTTPServer().getHttpRetryer();
        this.ttl = handleMsgContext.getTtl();
        this.startIdx = ThreadLocalRandom.current().nextInt(0, totalUrls.size());
    }

    public boolean tryHTTPRequest() {
        return false;
    }

    public void delayRetry(long delayTime) {
        if (retryTimes < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES && delayTime > 0) {
            retryTimes++;
            delay(delayTime);
            retryer.pushRetry(this);
        } else {
            complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
        }
    }

    public void delayRetry() {
        if (retryTimes < EventMeshConstants.DEFAULT_PUSH_RETRY_TIMES) {
            retryTimes++;
            delay((long) retryTimes * EventMeshConstants.DEFAULT_PUSH_RETRY_TIME_DISTANCE_IN_MILLSECONDS);
            retryer.pushRetry(this);
        } else {
            complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
        }
    }

    public String getUrl() {
        List<String> localIDCUrl = MapUtils.getObject(urls,
            eventMeshHttpConfiguration.getEventMeshIDC(), null);
        if (CollectionUtils.isNotEmpty(localIDCUrl)) {
            return localIDCUrl.get((startIdx + retryTimes) % localIDCUrl.size());
        }

        List<String> otherIDCUrl = new ArrayList<String>();
        for (List<String> tmp : urls.values()) {
            otherIDCUrl.addAll(tmp);
        }

        if (CollectionUtils.isNotEmpty(otherIDCUrl)) {
            return otherIDCUrl.get((startIdx + retryTimes) % otherIDCUrl.size());
        }

        return null;
    }

    public boolean isComplete() {
        return complete.get();
    }

    public void complete() {
        complete.compareAndSet(Boolean.FALSE, Boolean.TRUE);
    }

    public void timeout() {
        if (!isComplete() && System.currentTimeMillis() - lastPushTime >= ttl) {
            delayRetry();
        }
    }

    public HttpUriRequest buildHttpUriRequest() {
        currPushUrl = getUrl();

        if (StringUtils.isBlank(currPushUrl)) {
            return null;
        }

        HttpPost builder = new HttpPost(currPushUrl);

        String requestCode = "";
        if (SubscriptionType.SYNC == handleMsgContext.getSubscriptionItem().getType()) {
            requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode());
        } else {
            requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode());
        }
        String localAddress = IPUtils.getLocalAddress();
        builder.addHeader(ProtocolKey.REQUEST_CODE, requestCode);
        builder.addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        builder.addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
                handleMsgContext.getEventMeshHTTPServer()
                        .getEventMeshHttpConfiguration().getEventMeshCluster());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, localAddress);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
                handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration().getEventMeshEnv());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
                handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration().getEventMeshIDC());

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
                content = new String(httpEventWrapper.getBody(), Constants.DEFAULT_CHARSET);
                httpEventWrapper.getSysHeaderMap().forEach((k, v) -> {
                    if (!builder.containsHeader(k)) {
                        builder.addHeader(k, v.toString());
                    }
                });
            }

        } catch (Exception ex) {
            LOGGER.error("Failed to convert EventMeshMessage from CloudEvent", ex);
            return null;
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
                JsonUtils.toJSONString(EventMeshUtil.getEventProp(handleMsgContext.getEvent()))));

        HttpEntity httpEntity = new UrlEncodedFormEntity(body, Constants.DEFAULT_CHARSET);

        builder.setEntity(httpEntity);

        // for CloudEvents Webhook spec
        String urlAuthType = handleMsgContext.getConsumerGroupConfig().getConsumerGroupTopicConf()
                .get(handleMsgContext.getTopic()).getHttpAuthTypeMap().get(currPushUrl);

        WebhookUtil.setWebhookHeaders(builder, httpEntity.getContentType().getValue(),
                eventMeshHttpConfiguration.getEventMeshWebhookOrigin(),
                urlAuthType);

        eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordPushMsg();

        this.lastPushTime = System.currentTimeMillis();

        if (CMD_LOGGER.isInfoEnabled()) {
            CMD_LOGGER.info("cmd={}|eventMesh2client|from={}|to={}", requestCode,
                    localAddress, currPushUrl);
        }
        return builder;
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
                    JsonUtils.parseTypeReferenceObject(content, new TypeReference<Map<String, Object>>() {
                    });
            Integer retCode = (Integer) Objects.requireNonNull(ret).get(ProtocolKey.RETCODE);
            if (retCode != null && ClientRetCode.contains(retCode)) {
                return ClientRetCode.get(retCode);
            }

            return ClientRetCode.FAIL;
        } catch (NumberFormatException e) {
            if (MESSAGE_LOGGER.isWarnEnabled()) {
                MESSAGE_LOGGER.warn("url:{}, bizSeqno:{}, uniqueId:{}, httpResponse:{}", currPushUrl,
                        handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            }
            return ClientRetCode.FAIL;
        } catch (Exception e) {
            if (MESSAGE_LOGGER.isWarnEnabled()) {
                MESSAGE_LOGGER.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", currPushUrl,
                        handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            }
            return ClientRetCode.FAIL;
        }
    }
}
