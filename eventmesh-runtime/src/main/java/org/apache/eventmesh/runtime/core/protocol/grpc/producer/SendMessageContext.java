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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.retry.RetryContext;

import org.apache.commons.lang3.time.DateFormatUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class SendMessageContext extends RetryContext {

    public static Logger logger = LoggerFactory.getLogger("retry");

    private CloudEvent event;

    private String bizSeqNo;

    private EventMeshProducer eventMeshProducer;

    private long createTime = System.currentTimeMillis();

    public EventMeshGrpcServer eventMeshGrpcServer;

    public SendMessageContext(String bizSeqNo, CloudEvent event, EventMeshProducer eventMeshProducer,
                              EventMeshGrpcServer eventMeshGrpcServer) {
        this.bizSeqNo = bizSeqNo;
        this.event = event;
        this.eventMeshProducer = eventMeshProducer;
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public void setBizSeqNo(String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
    }

    public CloudEvent getEvent() {
        return event;
    }

    public void setEvent(CloudEvent event) {
        this.event = event;
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
            }
        });
        return true;
    }
}
