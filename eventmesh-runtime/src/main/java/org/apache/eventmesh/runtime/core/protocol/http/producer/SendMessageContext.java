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

package org.apache.eventmesh.runtime.core.protocol.http.producer;

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.core.protocol.RetryContext;

import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendMessageContext extends RetryContext {

    private CloudEvent event;

    private String bizSeqNo;

    private EventMeshProducer eventMeshProducer;

    private long createTime = System.currentTimeMillis();

    private Map<String, String> props;

    public EventMeshHTTPServer eventMeshHTTPServer;

    private List<CloudEvent> eventList;

    public SendMessageContext(String bizSeqNo, CloudEvent event, EventMeshProducer eventMeshProducer, EventMeshHTTPServer eventMeshHTTPServer) {
        this.bizSeqNo = bizSeqNo;
        this.event = event;
        this.eventMeshProducer = eventMeshProducer;
        this.eventMeshHTTPServer = eventMeshHTTPServer;
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

    public List<CloudEvent> getEventList() {
        return eventList;
    }

    public void setEventList(List<CloudEvent> eventList) {
        this.eventList = eventList;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("sendMessageContext={")
            .append("bizSeqNo=").append(bizSeqNo)
            .append(",retryTimes=").append(retryTimes)
            .append(",producer=").append(eventMeshProducer != null ? eventMeshProducer.producerGroupConfig.getGroupName() : null)
            .append(",executeTime=").append(DateFormatUtils.format(executeTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS))
            .append(",createTime=").append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS)).append("}");
        return sb.toString();
    }

    @Override
    public void retry() throws Exception {
        if (eventMeshProducer == null) {
            log.error("Exception happends during retry. EventMeshProduceer is null.");
            return;
        }

        if (retryTimes > 0) { // retry once
            log.error("Exception happends during retry. The retryTimes > 0.");
            return;
        }

        retryTimes++;
        eventMeshProducer.send(this, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
            }

            @Override
            public void onException(OnExceptionContext context) {
                log.warn("", context.getException());
                eventMeshHTTPServer.getMetrics().getSummaryMetrics().recordSendBatchMsgFailed(1);
            }

        });
    }
}
