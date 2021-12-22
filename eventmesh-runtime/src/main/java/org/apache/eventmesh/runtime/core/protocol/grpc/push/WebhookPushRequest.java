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
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.WebhookTopicConfig;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.http.HttpResponse;
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
import java.util.HashSet;
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

    private String currPushUrl;

    public WebhookPushRequest(HandleMsgContext handleMsgContext,
                              Map<String, Set<AbstractPushRequest>> waitingRequests) {
        super(handleMsgContext, waitingRequests);

        this.urls = ((WebhookTopicConfig) handleMsgContext.getConsumeTopicConfig()).getIdcUrls();
        this.totalUrls = buildTotalUrls();
        this.startIdx = RandomUtils.nextInt(0, totalUrls.size());
    }

    @Override
    public void tryPushRequest() {

        currPushUrl = getUrl();

        if (StringUtils.isBlank(currPushUrl)) {
            return;
        }

        HttpPost builder = new HttpPost(currPushUrl);

        String requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode());

        builder.addHeader(ProtocolKey.REQUEST_CODE, requestCode);
        builder.addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        builder.addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER,
            handleMsgContext.getEventMeshGrpcServer()
                .getEventMeshGrpcConfiguration().eventMeshCluster);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, IPUtils.getLocalAddress());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV,
            handleMsgContext.getEventMeshGrpcServer().getEventMeshGrpcConfiguration().eventMeshEnv);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC,
            handleMsgContext.getEventMeshGrpcServer().getEventMeshGrpcConfiguration().eventMeshIDC);

        String content = "";
        EventMeshMessage eventMeshMessage = getEventMeshMessage(event);
        if (eventMeshMessage == null) {
            return;
        } else {
            content = eventMeshMessage.getContent();
        }

        List<NameValuePair> body = new ArrayList<>();
        body.add(new BasicNameValuePair(PushMessageRequestBody.CONTENT, content));
        body.add(new BasicNameValuePair(PushMessageRequestBody.BIZSEQNO, bizSeqNum));
        body.add(new BasicNameValuePair(PushMessageRequestBody.UNIQUEID, uniqueId));
        body.add(new BasicNameValuePair(PushMessageRequestBody.RANDOMNO,
            handleMsgContext.getMsgRandomNo()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.TOPIC, topic));
        body.add(new BasicNameValuePair(PushMessageRequestBody.EXTFIELDS,
            JsonUtils.serialize(EventMeshUtil.getEventProp(event))));

        builder.setEntity(new UrlEncodedFormEntity(body, StandardCharsets.UTF_8));

        //eventMeshHTTPServer.metrics.summaryMetrics.recordPushMsg();

        this.lastPushTime = System.currentTimeMillis();

        addToWaitingMap(this);

        cmdLogger.info("cmd={}|eventMesh2client|from={}|to={}", requestCode,
            IPUtils.getLocalAddress(), currPushUrl);

        try {
            eventMeshGrpcServer.getHttpClient().execute(builder, new ResponseHandler<Object>() {
                @Override
                public Object handleResponse(HttpResponse response) {
                    removeWaitingMap(WebhookPushRequest.this);
                    long cost = System.currentTimeMillis() - lastPushTime;
                    //eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPPushTimeCost(cost);
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        //eventMeshHTTPServer.metrics.summaryMetrics.recordHttpPushMsgFailed();
                        messageLogger.info(
                            "message|eventMesh2client|exception|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}", currPushUrl, topic, bizSeqNum, uniqueId, cost);

                        delayRetry();
                        if (isComplete()) {
                            finish();
                        }
                    } else {
                        String res = "";
                        try {
                            res = EntityUtils.toString(response.getEntity(),
                                Charset.forName(EventMeshConstants.DEFAULT_CHARSET));
                        } catch (IOException e) {
                            finish();
                            return new Object();
                        }
                        ClientRetCode result = processResponseContent(res);
                        messageLogger.info(
                            "message|eventMesh2client|{}|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}",
                            result, currPushUrl, topic, bizSeqNum, uniqueId, cost);
                        if (result == ClientRetCode.OK || result == ClientRetCode.FAIL) {
                            complete();
                            finish();
                        } else if (result == ClientRetCode.RETRY || result == ClientRetCode.NOLISTEN) {
                            delayRetry();
                            if (isComplete()) {
                                finish();
                            }
                        }
                    }
                    return new Object();
                }
            });

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("message|eventMesh2client|url={}|topic={}|event={}", currPushUrl,
                    topic, event);
            } else {
                messageLogger
                    .info("message|eventMesh2client|url={}|topic={}|bizSeqNo={}|uniqueId={}",
                        currPushUrl, topic, bizSeqNum, uniqueId);
            }
        } catch (IOException e) {
            messageLogger.error("push2client err", e);
            removeWaitingMap(this);
            delayRetry();
            if (isComplete()) {
                finish();
            }
        }
    }

    @Override
    public String toString() {
        return "asyncPushRequest={"
            + "bizSeqNo=" + bizSeqNum
            + ",startIdx=" + startIdx
            + ",retryTimes=" + retryTimes
            + ",uniqueId=" + uniqueId
            + ",executeTime="
            + DateFormatUtils.format(executeTime, Constants.DATE_FORMAT)
            + ",lastPushTime="
            + DateFormatUtils.format(lastPushTime, Constants.DATE_FORMAT)
            + ",createTime="
            + DateFormatUtils.format(createTime, Constants.DATE_FORMAT) + "}";
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
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", currPushUrl,
                bizSeqNum, uniqueId, content);
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

    private List<String> buildTotalUrls() {
        Set<String> totalUrls = new HashSet<>();
        for (List<String> idcUrls : urls.values()) {
            totalUrls.addAll(idcUrls);
        }
        return new ArrayList<>(totalUrls);
    }
}
