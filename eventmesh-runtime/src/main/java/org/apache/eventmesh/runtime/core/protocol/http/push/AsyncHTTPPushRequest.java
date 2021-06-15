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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Sets;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.protocol.http.body.message.PushMessageRequestBody;
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.common.protocol.http.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.http.common.ProtocolVersion;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HandleMsgContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.OMSUtil;
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

public class AsyncHTTPPushRequest extends AbstractHTTPPushRequest {

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public Logger cmdLogger = LoggerFactory.getLogger("cmd");

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String, Set<AbstractHTTPPushRequest>> waitingRequests;

    public String currPushUrl;

    private final static RandomStringGenerator randomGenerator = new RandomStringGenerator.Builder()
            .withinRange('0', '9').build();

    public AsyncHTTPPushRequest(HandleMsgContext handleMsgContext, Map<String, Set<AbstractHTTPPushRequest>> waitingRequests) {
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

        if (EventMeshUtil.isService(handleMsgContext.getTopic())) {
            requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_SYNC.getRequestCode());
        } else {
            requestCode = String.valueOf(RequestCode.HTTP_PUSH_CLIENT_ASYNC.getRequestCode());
        }

        builder.addHeader(ProtocolKey.REQUEST_CODE, requestCode);
        builder.addHeader(ProtocolKey.LANGUAGE, Constants.LANGUAGE_JAVA);
        builder.addHeader(ProtocolKey.VERSION, ProtocolVersion.V1.getVersion());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHCLUSTER, handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration().eventMeshCluster);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIP, IPUtil.getLocalAddress());
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHENV, handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration().eventMeshEnv);
        builder.addHeader(ProtocolKey.EventMeshInstanceKey.EVENTMESHIDC, handleMsgContext.getEventMeshHTTPServer().getEventMeshHttpConfiguration().eventMeshIDC);

        handleMsgContext.getMsg().getUserProperties().put(EventMeshConstants.REQ_EVENTMESH2C_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        String content = "";
        try {
            content = new String(handleMsgContext.getMsg().getBody(), EventMeshConstants.DEFAULT_CHARSET);
        } catch (Exception ex) {
            return;
        }

        List<NameValuePair> body = new ArrayList<NameValuePair>();
        body.add(new BasicNameValuePair(PushMessageRequestBody.CONTENT, content));
        if (StringUtils.isBlank(handleMsgContext.getBizSeqNo())) {
            body.add(new BasicNameValuePair(PushMessageRequestBody.BIZSEQNO, randomGenerator.generate(20)));
        } else {
            body.add(new BasicNameValuePair(PushMessageRequestBody.BIZSEQNO, handleMsgContext.getBizSeqNo()));
        }
        if (StringUtils.isBlank(handleMsgContext.getUniqueId())) {
            body.add(new BasicNameValuePair(PushMessageRequestBody.UNIQUEID, randomGenerator.generate(20)));
        } else {
            body.add(new BasicNameValuePair(PushMessageRequestBody.UNIQUEID, handleMsgContext.getUniqueId()));
        }

        body.add(new BasicNameValuePair(PushMessageRequestBody.RANDOMNO, handleMsgContext.getMsgRandomNo()));
        body.add(new BasicNameValuePair(PushMessageRequestBody.TOPIC, handleMsgContext.getTopic()));

        body.add(new BasicNameValuePair(PushMessageRequestBody.EXTFIELDS, JSON.toJSONString(OMSUtil.getMessageProp(handleMsgContext.getMsg()))));

        try {
            builder.setEntity(new UrlEncodedFormEntity(body));
        } catch (UnsupportedEncodingException e) {
            return;
        }

        eventMeshHTTPServer.metrics.summaryMetrics.recordPushMsg();

        this.lastPushTime = System.currentTimeMillis();

        addToWaitingMap(this);

        cmdLogger.info("cmd={}|eventMesh2client|from={}|to={}", requestCode,
                IPUtil.getLocalAddress(), currPushUrl);

        try {
            httpClientPool.getClient().execute(builder, new ResponseHandler<Object>() {
                @Override
                public Object handleResponse(HttpResponse response) {
                    removeWaitingMap(AsyncHTTPPushRequest.this);
                    long cost = System.currentTimeMillis() - lastPushTime;
                    eventMeshHTTPServer.metrics.summaryMetrics.recordHTTPPushTimeCost(cost);
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        eventMeshHTTPServer.metrics.summaryMetrics.recordHttpPushMsgFailed();
                        messageLogger.info("message|eventMesh2client|exception|url={}|topic={}|bizSeqNo={}|uniqueId={}|cost={}", currPushUrl, handleMsgContext.getTopic(),
                                handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);

                        delayRetry();
                        if (isComplete()) {
                            handleMsgContext.finish();
                        }
                    } else {
                        String res = "";
                        try {
                            res = EntityUtils.toString(response.getEntity(), Charset.forName(EventMeshConstants.DEFAULT_CHARSET));
                        } catch (IOException e) {
                            handleMsgContext.finish();
                            return new Object();
                        }
                        ClientRetCode result = processResponseContent(res);
                        messageLogger.info("message|eventMesh2client|{}|url={}|topic={}|bizSeqNo={}|uniqueId={}|cost={}", result, currPushUrl, handleMsgContext.getTopic(),
                                handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);
                        if (result == ClientRetCode.OK) {
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
                    }
                    return new Object();
                }
            });

            if (messageLogger.isDebugEnabled()) {
                messageLogger.debug("message|eventMesh2client|url={}|topic={}|msg={}", currPushUrl, handleMsgContext.getTopic(),
                        handleMsgContext.getMsg());
            } else {
                messageLogger.info("message|eventMesh2client|url={}|topic={}|bizSeqNo={}|uniqueId={}", currPushUrl, handleMsgContext.getTopic(),
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
                .append(",executeTime=").append(DateFormatUtils.format(executeTime, Constants.DATE_FORMAT))
                .append(",lastPushTime=").append(DateFormatUtils.format(lastPushTime, Constants.DATE_FORMAT))
                .append(",createTime=").append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT)).append("}");
        return sb.toString();
    }

    ClientRetCode processResponseContent(String content) {
        if (StringUtils.isBlank(content)) {
            return ClientRetCode.FAIL;
        }

        try {
            JSONObject ret = JSONObject.parseObject(content);
            Integer retCode = ret.getInteger("retCode");
            if (retCode != null && ClientRetCode.contains(retCode)) {
                return ClientRetCode.get(retCode);
            }

            return ClientRetCode.FAIL;
        } catch (NumberFormatException e) {
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{}, httpResponse:{}", currPushUrl, handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            return ClientRetCode.FAIL;
        } catch (JSONException e) {
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", currPushUrl, handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            return ClientRetCode.FAIL;
        } catch (Throwable t) {
            messageLogger.warn("url:{}, bizSeqno:{}, uniqueId:{},  httpResponse:{}", currPushUrl, handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), content);
            return ClientRetCode.FAIL;
        }
    }

    private void addToWaitingMap(AsyncHTTPPushRequest request) {
        if (waitingRequests.containsKey(request.handleMsgContext.getConsumerGroup())) {
            waitingRequests.get(request.handleMsgContext.getConsumerGroup()).add(request);
            return;
        }
        waitingRequests.put(request.handleMsgContext.getConsumerGroup(), Sets.newConcurrentHashSet());
        waitingRequests.get(request.handleMsgContext.getConsumerGroup()).add(request);
        return;
    }

    private void removeWaitingMap(AsyncHTTPPushRequest request) {
        if (waitingRequests.containsKey(request.handleMsgContext.getConsumerGroup())) {
            waitingRequests.get(request.handleMsgContext.getConsumerGroup()).remove(request);
            return;
        }
    }

    @Override
    public boolean retry() {
        tryHTTPRequest();
        return true;
    }
}
