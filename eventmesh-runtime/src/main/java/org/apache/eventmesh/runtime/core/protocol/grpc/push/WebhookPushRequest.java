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

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.WebhookTopicConfig;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WebhookPushRequest extends AbstractPushRequest {

    private final Logger messageLogger = LoggerFactory.getLogger("message");

    private final Logger cmdLogger = LoggerFactory.getLogger("cmd");

    /**
     * Key: idc
     * Value: list of URLs
     **/
    private final Map<String, List<String>> urls;

    private final List<String> totalUrls;

    private final int startIdx;

    private String selectedPushUrl;

    public WebhookPushRequest(HandleMsgContext handleMsgContext,
                              Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext, waitingRequests);

        WebhookTopicConfig topicConfig = (WebhookTopicConfig) handleMsgContext.getConsumeTopicConfig();
        this.urls = topicConfig.getIdcUrls();
        this.totalUrls = topicConfig.getTotalUrls();
        this.startIdx = RandomUtils.nextInt(0, totalUrls.size());
    }

    @Override
    public void tryPushRequest() {
        if (simpleMessage == null) {
            return;
        }

        selectedPushUrl = getUrl();
        if (StringUtils.isBlank(selectedPushUrl)) {
            return;
        }

        this.lastPushTime = System.currentTimeMillis();

        HttpPost builder = new HttpPost(selectedPushUrl);

        String requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode());
        builder.addHeader(ProtocolKey.REQUEST_CODE, requestCode);
        builder.addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        builder.addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, eventMeshGrpcConfiguration.eventMeshCluster);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, eventMeshGrpcConfiguration.eventMeshIp);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, eventMeshGrpcConfiguration.eventMeshEnv);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, eventMeshGrpcConfiguration.eventMeshIDC);

        RequestHeader requestHeader = simpleMessage.getHeader();
        builder.addHeader(ProtocolKey.PROTOCOL_TYPE, requestHeader.getProtocolType());
        builder.addHeader(ProtocolKey.PROTOCOL_DESC, requestHeader.getProtocolDesc());
        builder.addHeader(ProtocolKey.PROTOCOL_VERSION, requestHeader.getProtocolVersion());
        builder.addHeader(ProtocolKey.CONTENT_TYPE, simpleMessage.getPropertiesOrDefault(ProtocolKey.CONTENT_TYPE,
            "application/cloudevents+json"));

        List<NameValuePair> body = new ArrayList<>();
        body.add(new BasicNameValuePair(PushMessageRequestBody.CONTENT, simpleMessage.getContent()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.BIZSEQNO, simpleMessage.getSeqNum()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.UNIQUEID, simpleMessage.getUniqueId()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.RANDOMNO, handleMsgContext.getMsgRandomNo()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.TOPIC, simpleMessage.getTopic()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.EXTFIELDS,
            JsonUtils.serialize(simpleMessage.getPropertiesMap())));

        simpleMessage = SimpleMessage.newBuilder(simpleMessage)
            .putProperties(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP, String.valueOf(lastPushTime))
            .build();

        builder.setEntity(new UrlEncodedFormEntity(body, StandardCharsets.UTF_8));

        //eventMeshHTTPServer.metrics.summaryMetrics.recordPushMsg();

        addToWaitingMap(this);

        cmdLogger.info("cmd={}|eventMesh2client|from={}|to={}", requestCode,
            IPUtils.getLocalAddress(), selectedPushUrl);

        try {
            eventMeshGrpcServer.getHttpClient().execute(builder, handleResponse());
            messageLogger
                .info("message|eventMesh2client|url={}|topic={}|bizSeqNo={}|uniqueId={}",
                    selectedPushUrl, simpleMessage.getTopic(), simpleMessage.getSeqNum(),
                    simpleMessage.getUniqueId());
        } catch (IOException e) {
            long cost = System.currentTimeMillis() - lastPushTime;
            messageLogger.error(
                "message|eventMesh2client|exception={} |emitter|topic={}|bizSeqNo={}"
                    + "|uniqueId={}|cost={}", e.getMessage(), simpleMessage.getTopic(),
                simpleMessage.getSeqNum(), simpleMessage.getUniqueId(), cost, e);
            removeWaitingMap(this);
            delayRetry();
        }
    }

    @Override
    public String toString() {
        return "asyncPushRequest={"
            + "bizSeqNo=" + simpleMessage.getSeqNum()
            + ",startIdx=" + startIdx
            + ",retryTimes=" + retryTimes
            + ",uniqueId=" + simpleMessage.getUniqueId()
            + ",executeTime="
            + DateFormatUtils.format(executeTime, Constants.DATE_FORMAT)
            + ",lastPushTime="
            + DateFormatUtils.format(lastPushTime, Constants.DATE_FORMAT)
            + ",createTime="
            + DateFormatUtils.format(createTime, Constants.DATE_FORMAT) + "}";
    }

    private ResponseHandler<Object> handleResponse() {
        return response -> {
            removeWaitingMap(WebhookPushRequest.this);
            long cost = System.currentTimeMillis() - lastPushTime;
            //eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPPushTimeCost(cost);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                //eventMeshHTTPServer.metrics.summaryMetrics.recordHttpPushMsgFailed();
                messageLogger.info(
                    "message|eventMesh2client|exception|url={}|topic={}|bizSeqNo={}"
                        + "|uniqueId={}|cost={}", selectedPushUrl, simpleMessage.getTopic(),
                    simpleMessage.getSeqNum(), simpleMessage.getUniqueId(), cost);

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
                ClientRetCode result = processResponseContent(res);
                messageLogger.info(
                    "message|eventMesh2client|{}|url={}|topic={}|bizSeqNo={}"
                        + "|uniqueId={}|cost={}", result, selectedPushUrl, simpleMessage.getTopic(),
                    simpleMessage.getSeqNum(), simpleMessage.getUniqueId(), cost);
                if (result == ClientRetCode.OK || result == ClientRetCode.FAIL) {
                    complete();
                } else if (result == ClientRetCode.RETRY || result == ClientRetCode.NOLISTEN) {
                    delayRetry();
                }
            }
            return new Object();
        };
    }

    private ClientRetCode processResponseContent(String content) {
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
        } catch (Exception e) {
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", selectedPushUrl,
                simpleMessage.getSeqNum(), simpleMessage.getUniqueId(), content);
            return ClientRetCode.FAIL;
        }
    }

    private String getUrl() {
        List<String> localIdcUrl = MapUtils.getObject(urls,
            eventMeshGrpcConfiguration.eventMeshIDC, null);
        if (CollectionUtils.isNotEmpty(localIdcUrl)) {
            return localIdcUrl.get((startIdx + retryTimes) % localIdcUrl.size());
        }

        if (CollectionUtils.isNotEmpty(totalUrls)) {
            return totalUrls.get((startIdx + retryTimes) % totalUrls.size());
        }
        return null;
    }
}
