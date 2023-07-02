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
import org.apache.eventmesh.common.protocol.http.common.ClientRetCode;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.http.consumer.HandleMsgContext;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

public class AsyncHTTPPushRequest extends AbstractHTTPPushRequest {
    public static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    public String currPushUrl;
    private final Map<String, Set<AbstractHTTPPushRequest>> waitingRequests;

    public AsyncHTTPPushRequest(HandleMsgContext handleMsgContext,
        Map<String, Set<AbstractHTTPPushRequest>> waitingRequests) {
        super(handleMsgContext);
        this.waitingRequests = waitingRequests;
    }

    @Override
    public boolean tryHTTPRequest() {
        HttpUriRequest builder = buildHttpUriRequest();
        if (builder == null) {
            return false;
        }

        addToWaitingMap(this);

        try {
            eventMeshHTTPServer.getHttpClientPool().getClient().execute(builder, response -> {
                removeWaitingMap(AsyncHTTPPushRequest.this);
                long cost = System.currentTimeMillis() - lastPushTime;
                eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHTTPPushTimeCost(cost);

                if (processResponseStatus(response.getStatusLine().getStatusCode(), response)) {
                    // this is successful response, process response payload
                    String res;
                    try {
                        res = EntityUtils.toString(response.getEntity(), Charset.forName(EventMeshConstants.DEFAULT_CHARSET));
                    } catch (IOException e) {
                        handleMsgContext.finish();
                        return new Object();
                    }
                    ClientRetCode result = processResponseContent(res);
                    if (MESSAGE_LOGGER.isInfoEnabled()) {
                        MESSAGE_LOGGER.info(
                            "message|eventMesh2client|{}|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}",
                            result, currPushUrl, handleMsgContext.getTopic(),
                            handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);
                    }
                    switch (result) {
                        case OK:
                        case REMOTE_OK:
                        case FAIL:
                            complete();
                            if (isComplete()) {
                                handleMsgContext.finish();
                            }
                            break;
                        case RETRY:
                        case NOLISTEN:
                            delayRetry();
                            if (isComplete()) {
                                handleMsgContext.finish();
                            }
                            break;
                        default: // do nothing
                    }
                } else {
                    eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordHttpPushMsgFailed();
                    if (MESSAGE_LOGGER.isInfoEnabled()) {
                        MESSAGE_LOGGER.info(
                            "message|eventMesh2client|exception|url={}|topic={}|bizSeqNo={}"
                                + "|uniqueId={}|cost={}", currPushUrl, handleMsgContext.getTopic(),
                            handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId(), cost);
                    }

                    if (isComplete()) {
                        handleMsgContext.finish();
                    }
                }
                return new Object();
            });

            if (MESSAGE_LOGGER.isDebugEnabled()) {
                MESSAGE_LOGGER.debug("message|eventMesh2client|url={}|topic={}|event={}", currPushUrl,
                    handleMsgContext.getTopic(),
                    handleMsgContext.getEvent());
            } else {
                if (MESSAGE_LOGGER.isInfoEnabled()) {
                    MESSAGE_LOGGER
                        .info("message|eventMesh2client|url={}|topic={}|bizSeqNo={}|uniqueId={}",
                            currPushUrl, handleMsgContext.getTopic(),
                            handleMsgContext.getBizSeqNo(), handleMsgContext.getUniqueId());
                }
            }
        } catch (IOException e) {
            MESSAGE_LOGGER.error("push2client err", e);
            removeWaitingMap(this);
            delayRetry();
            if (isComplete()) {
                handleMsgContext.finish();
            }
        }
        return false;
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
            .append(DateFormatUtils.format(executeTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS))
            .append(",lastPushTime=")
            .append(DateFormatUtils.format(lastPushTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS))
            .append(",createTime=")
            .append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS)).append("}");
        return sb.toString();
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
    public void retry() {
        tryHTTPRequest();
    }
}
