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

package org.apache.eventmesh.api.connector.storage.reply;

import org.apache.eventmesh.api.connector.storage.data.CloudEventInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Setter;

public class ReplyOperationService {

    protected static final Logger messageLogger = LoggerFactory.getLogger("message");

    @Setter
    private Executor executor;

    protected Map<ReplyOperation, Map<String, Map<Long, RequestReplyInfo>>> replyOperationMap = new ConcurrentHashMap<>();

    public void setRequestReplyInfo(ReplyOperation replyOperation, String topic, Long id,
                                    RequestReplyInfo requestReplyInfo) {
        Map<String, Map<Long, RequestReplyInfo>> replyMap = replyOperationMap.get(replyOperation);
        if (Objects.isNull(replyMap)) {
            replyMap = replyOperationMap.computeIfAbsent(replyOperation, k -> new ConcurrentHashMap<>());
        }
        Map<Long, RequestReplyInfo> requestReplyInfoMap = replyMap.get(topic);
        if (Objects.isNull(requestReplyInfoMap)) {
            requestReplyInfoMap = replyMap.computeIfAbsent(topic, k -> new ConcurrentHashMap<>());
        }
        requestReplyInfoMap.put(id, requestReplyInfo);
    }

    public void reply(ReplyOperation replyOperation, Map<String, Map<Long, RequestReplyInfo>> replyMap) {
        if (replyMap.isEmpty()) {
            return;
        }
        long time = System.currentTimeMillis();
        List<ReplyRequest> replyRequestList = new ArrayList<>();
        for (Entry<String, Map<Long, RequestReplyInfo>> entry : replyMap.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            ReplyRequest replyRequest = new ReplyRequest();
            List<Long> list = new ArrayList<>();
            for (Entry<Long, RequestReplyInfo> entry2 : entry.getValue().entrySet()) {
                if (entry2.getValue().getTimeOut() > time) {
                    list.add(entry2.getKey());
                } else {
                    replyMap.remove(entry.getKey());
                    messageLogger.warn("");
                    RuntimeException runtimeException = new RuntimeException();
                    entry2.getValue().getRequestReplyCallback().onException(runtimeException);
                }
            }
            replyRequest.setTopic(entry.getKey());
            replyRequest.setIdList(list);
            replyRequestList.add(replyRequest);
        }
        if (replyRequestList.isEmpty()) {
            messageLogger.info("");
            return;
        }
        try {
            List<CloudEventInfo> cloudEventList = replyOperation.queryReplyCloudEvent(replyRequestList);
            if (cloudEventList.isEmpty()) {
                messageLogger.warn("");
            }
            for (CloudEventInfo cloudEventInfo : cloudEventList) {
                RequestReplyInfo replyInfo = null;
                try {
                    cloudEventInfo.getCloudEventInfoId();

                    replyInfo = replyMap.get(cloudEventInfo.getCloudEventTopic()).remove(Long.valueOf(cloudEventInfo.getCloudEventInfoId()));
                    if (Objects.isNull(replyInfo)) {
                        continue;
                    }
                    cloudEventInfo.getCloudEventReplyData();
                    replyInfo.getRequestReplyCallback().onSuccess(null);
                } catch (Exception e) {
                    if (Objects.nonNull(replyInfo)) {
                        replyInfo.getRequestReplyCallback().onException(e);
                    }
                    messageLogger.error(e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            messageLogger.error(e.getMessage(), e);
        }
    }

    public void execute() {
        if (replyOperationMap.isEmpty()) {
            return;
        }
        for (Entry<ReplyOperation, Map<String, Map<Long, RequestReplyInfo>>> entry : replyOperationMap.entrySet()) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    reply(entry.getKey(), entry.getValue());
                }
            });

        }

    }
}
