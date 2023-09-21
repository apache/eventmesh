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

package org.apache.eventmesh.runtime.core.protocol.grpc.push;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.WebhookTopicConfig;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

public class WebhookPushRequest extends AbstractPushRequest {

    private static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private static final Logger CMD_LOGGER = LoggerFactory.getLogger(EventMeshConstants.CMD);

    /**
     * Key: idc Value: list of URLs
     **/
    private final Map<String, List<String>> urls;

    private final List<String> totalUrls;

    private final int startIdx;

    private final SubscriptionMode subscriptionMode;

    public WebhookPushRequest(HandleMsgContext handleMsgContext,
        Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext, waitingRequests);

        WebhookTopicConfig topicConfig = (WebhookTopicConfig) handleMsgContext.getConsumeTopicConfig();

        this.subscriptionMode = topicConfig.getSubscriptionMode();

        this.urls = topicConfig.getIdcUrls();
        this.totalUrls = topicConfig.getTotalUrls();
        this.startIdx = RandomUtils.nextInt(0, totalUrls.size());
    }

    @Override
    public void tryPushRequest() {
        if (eventMeshCloudEvent == null) {
            return;
        }

        List<String> selectedPushUrls = getUrl();
        for (String selectedPushUrl : selectedPushUrls) {

            this.lastPushTime = System.currentTimeMillis();

            HttpPost builder = new HttpPost(selectedPushUrl);

            String requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode());
            builder.addHeader(ProtocolKey.REQUEST_CODE, requestCode);
            builder.addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
            builder.addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
            builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
                eventMeshGrpcConfiguration.getEventMeshCluster());
            builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, eventMeshGrpcConfiguration.getEventMeshIp());
            builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, eventMeshGrpcConfiguration.getEventMeshEnv());
            builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, eventMeshGrpcConfiguration.getEventMeshIDC());

            builder.addHeader(ProtocolKey.PROTOCOL_TYPE, EventMeshCloudEventUtils.getProtocolType(eventMeshCloudEvent));
            builder.addHeader(ProtocolKey.PROTOCOL_DESC, EventMeshCloudEventUtils.getProtocolDesc(eventMeshCloudEvent));
            builder.addHeader(ProtocolKey.PROTOCOL_VERSION, EventMeshCloudEventUtils.getProtocolVersion(eventMeshCloudEvent));
            builder.addHeader(ProtocolKey.CONTENT_TYPE, EventMeshCloudEventUtils.getContentType(eventMeshCloudEvent,
                Constants.CONTENT_TYPE_CLOUDEVENTS_JSON));

            List<NameValuePair> body = new ArrayList<>();
            body.add(new BasicNameValuePair(PushMessageRequestBody.CONTENT, EventMeshCloudEventUtils.getDataContent(eventMeshCloudEvent)));
            body.add(new BasicNameValuePair(PushMessageRequestBody.BIZSEQNO, EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent)));
            body.add(new BasicNameValuePair(PushMessageRequestBody.UNIQUEID, EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent)));
            body.add(new BasicNameValuePair(PushMessageRequestBody.RANDOMNO, handleMsgContext.getMsgRandomNo()));
            body.add(new BasicNameValuePair(PushMessageRequestBody.TOPIC, EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent)));
            body.add(new BasicNameValuePair(PushMessageRequestBody.EXTFIELDS,
                JsonUtils.toJSONString(EventMeshCloudEventUtils.getAttributes(eventMeshCloudEvent))));

            eventMeshCloudEvent = CloudEvent.newBuilder(eventMeshCloudEvent).putAttributes(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP,
                CloudEventAttributeValue.newBuilder().setCeString(String.valueOf(lastPushTime)).build()).build();

            builder.setEntity(new UrlEncodedFormEntity(body, StandardCharsets.UTF_8));

            addToWaitingMap(this);

            CMD_LOGGER.info("cmd={}|eventMesh2client|from={}|to={}", requestCode,
                IPUtils.getLocalAddress(), selectedPushUrl);

            try {
                eventMeshGrpcServer.getHttpClient().execute(builder, handleResponse(selectedPushUrl));
                MESSAGE_LOGGER
                    .info("message|eventMesh2client|url={}|topic={}|bizSeqNo={}|uniqueId={}",
                        selectedPushUrl, EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent),
                        EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent),
                        EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent));
            } catch (IOException e) {
                long cost = System.currentTimeMillis() - lastPushTime;
                MESSAGE_LOGGER.error(
                    "message|eventMesh2client|exception={} |emitter|topic={}|bizSeqNo={}"
                        + "|uniqueId={}|cost={}",
                    e.getMessage(), EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent),
                    EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent), EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), cost, e);
                removeWaitingMap(this);
                delayRetry();
            }
        }
    }

    @Override
    public String toString() {
        return "asyncPushRequest={"
            + "bizSeqNo=" + EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent)
            + ",startIdx=" + startIdx
            + ",retryTimes=" + retryTimes
            + ",uniqueId=" + EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent)
            + ",executeTime="
            + DateFormatUtils.format(executeTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS)
            + ",lastPushTime="
            + DateFormatUtils.format(lastPushTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS)
            + ",createTime="
            + DateFormatUtils.format(createTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS) + "}";
    }

    private ResponseHandler<Object> handleResponse(String selectedPushUrl) {
        return response -> {
            removeWaitingMap(WebhookPushRequest.this);
            long cost = System.currentTimeMillis() - lastPushTime;

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {

                MESSAGE_LOGGER.info(
                    "message|eventMesh2client|exception|url={}|topic={}|bizSeqNo={}"
                        + "|uniqueId={}|cost={}",
                    selectedPushUrl, EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent),
                    EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent), EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), cost);

                delayRetry();
            } else {
                String res = "";
                try {
                    res = EntityUtils.toString(response.getEntity(),
                        Charset.forName(EventMeshConstants.DEFAULT_CHARSET));
                } catch (IOException e) {
                    complete();
                    return new Object();
                }
                ClientRetCode result = processResponseContent(res, selectedPushUrl);
                MESSAGE_LOGGER.info(
                    "message|eventMesh2client|{}|url={}|topic={}|bizSeqNo={}"
                        + "|uniqueId={}|cost={}",
                    result, selectedPushUrl, EventMeshCloudEventUtils.getSubject(eventMeshCloudEvent),
                    EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent), EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), cost);
                switch (result) {
                    case OK:
                    case FAIL:
                        complete();
                        break;
                    case RETRY:
                    case NOLISTEN:
                        delayRetry();
                        break;
                    default:
                        // do nothing
                }
            }
            return new Object();
        };
    }

    private ClientRetCode processResponseContent(String content, String selectedPushUrl) {
        if (StringUtils.isBlank(content)) {
            return ClientRetCode.FAIL;
        }

        try {
            Map<String, Object> ret =
                JsonUtils.parseTypeReferenceObject(content, new TypeReference<Map<String, Object>>() {
                });
            Integer retCode = (Integer) Objects.requireNonNull(ret).get("retCode");
            if (retCode != null && ClientRetCode.contains(retCode)) {
                return ClientRetCode.get(retCode);
            }
            return ClientRetCode.FAIL;
        } catch (Exception e) {
            MESSAGE_LOGGER.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", selectedPushUrl,
                EventMeshCloudEventUtils.getSeqNum(eventMeshCloudEvent), EventMeshCloudEventUtils.getUniqueId(eventMeshCloudEvent), content);
            return ClientRetCode.FAIL;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> getUrl() {
        List<String> localIdcUrl = MapUtils.getObject(urls,
            eventMeshGrpcConfiguration.getEventMeshIDC(), null);
        if (CollectionUtils.isNotEmpty(localIdcUrl)) {
            return getStringList(localIdcUrl);
        } else if (CollectionUtils.isNotEmpty(totalUrls)) {
            return getStringList(totalUrls);
        }
        MESSAGE_LOGGER.error("No event emitters from subscriber, no message returning.");
        return Collections.emptyList();
    }

    private List<String> getStringList(List<String> stringList) {
        switch (subscriptionMode) {
            case CLUSTERING:
                return Collections.singletonList(stringList.get((startIdx + retryTimes) % stringList.size()));
            case BROADCASTING:
                return stringList;
            default:
                MESSAGE_LOGGER.error("Invalid Subscription Mode, no message returning back to subscriber.");
                return Collections.emptyList();
        }
    }
}
