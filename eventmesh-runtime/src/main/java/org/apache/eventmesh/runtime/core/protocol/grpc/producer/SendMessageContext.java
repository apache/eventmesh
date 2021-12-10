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

package org.apache.eventmesh.runtime.core.protocol.grpc.producer;

import io.openmessaging.api.Message;
import io.openmessaging.api.OnExceptionContext;
import io.openmessaging.api.SendCallback;
import io.openmessaging.api.SendResult;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.RetryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SendMessageContext extends RetryContext {

    public static Logger logger = LoggerFactory.getLogger("retry");

    private Message msg;

    private String bizSeqNo;

    private EventMeshProducer eventMeshProducer;

    private long createTime = System.currentTimeMillis();

    private Map<String, String> props;

    public EventMeshGrpcServer eventMeshGrpcServer;

    private List<Message> messageList;

    public SendMessageContext(String bizSeqNo, Message msg, EventMeshProducer eventMeshProducer,
                              EventMeshGrpcServer eventMeshGrpcServer) {
        this.bizSeqNo = bizSeqNo;
        this.msg = msg;
        this.eventMeshProducer = eventMeshProducer;
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void addProp(String key, String val) {
        if (props == null) {
            props = new HashMap<>();
        }
        props.put(key, val);
    }

    public String getProp(String key) {
        return props.get(key);
    }

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public void setBizSeqNo(String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
    }

    public Message getMsg() {
        return msg;
    }

    public void setMsg(Message msg) {
        this.msg = msg;
    }

    public EventMeshProducer getEventMeshProducer() {
        return eventMeshProducer;
    }

    public void setEventMeshProducer(EventMeshProducer eventMeshProducer) {
        this.eventMeshProducer = eventMeshProducer;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public List<Message> getMessageList() {
        return messageList;
    }

    public void setMessageList(List<Message> messageList) {
        this.messageList = messageList;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("sendMessageContext={")
                .append("bizSeqNo=").append(bizSeqNo)
                .append(",retryTimes=").append(retryTimes)
                .append(",producer=")
                .append(eventMeshProducer != null ? eventMeshProducer : null)
                .append(",executeTime=")
                .append(DateFormatUtils.format(executeTime, Constants.DATE_FORMAT))
                .append(",createTime=")
                .append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT)).append("}");
        return sb.toString();
    }

    @Override
    public boolean retry() throws Exception {
        if (eventMeshProducer == null) {
            return false;
        }

        if (retryTimes > 0) { //retry once
            return false;
        }

        retryTimes++;
        eventMeshProducer.send(this, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(OnExceptionContext context) {
                logger.warn("", context.getException());
                //eventMeshHTTPServer.metrics.summaryMetrics.recordSendBatchMsgFailed(1);
            }
        });
        return true;
    }
}
