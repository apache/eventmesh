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

package org.apache.eventmesh.runtime.core.protocol.grpc.consumer;

import io.cloudevents.CloudEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

public class HandleMsgContext {

    private final String msgRandomNo;
    private final SubscriptionMode subscriptionMode;
    private final EventMeshGrpcServer eventMeshGrpcServer;
    private final ConsumerGroupTopicConfig consumeTopicConfig;
    public Logger messageLogger = LoggerFactory.getLogger("message");
    private String consumerGroup;
    private EventMeshConsumer eventMeshConsumer;
    private String bizSeqNo;
    private String uniqueId;
    private String topic;
    private CloudEvent event;
    private int ttl;
    private long createTime = System.currentTimeMillis();
    private AbstractContext context;

    public HandleMsgContext(String msgRandomNo, String consumerGroup, EventMeshConsumer eventMeshConsumer,
                            String topic, CloudEvent event, SubscriptionMode subscriptionMode,
                            AbstractContext context, EventMeshGrpcServer eventMeshGrpcServer,
                            String bizSeqNo, String uniqueId, ConsumerGroupTopicConfig consumeTopicConfig) {
        this.msgRandomNo = msgRandomNo;
        this.consumerGroup = consumerGroup;
        this.eventMeshConsumer = eventMeshConsumer;
        this.topic = topic;
        this.subscriptionMode = subscriptionMode;
        this.event = event;
        this.context = context;
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.bizSeqNo = bizSeqNo;
        this.uniqueId = uniqueId;
        this.consumeTopicConfig = consumeTopicConfig;

        String ttlStr = (String) event.getExtension(Constants.PROPERTY_MESSAGE_TIMEOUT);
        this.ttl = StringUtils.isNumeric(ttlStr) ? Integer.parseInt(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
    }

    public String getMsgRandomNo() {
        return msgRandomNo;
    }

    public ConsumerGroupTopicConfig getConsumeTopicConfig() {
        return consumeTopicConfig;
    }

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public void setBizSeqNo(String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public EventMeshConsumer getEventMeshConsumer() {
        return eventMeshConsumer;
    }

    public void setEventMeshConsumer(EventMeshConsumer eventMeshConsumer) {
        this.eventMeshConsumer = eventMeshConsumer;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public CloudEvent getEvent() {
        return event;
    }

    public void setEvent(CloudEvent event) {
        this.event = event;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public AbstractContext getContext() {
        return context;
    }

    public void setContext(AbstractContext context) {
        this.context = context;
    }

    public EventMeshGrpcServer getEventMeshGrpcServer() {
        return eventMeshGrpcServer;
    }

    public void finish() {
        if (eventMeshConsumer != null && context != null && event != null) {
            try {
                eventMeshConsumer.updateOffset(subscriptionMode, Collections.singletonList(event), context);
            } catch (Exception e) {
                messageLogger.error("Error in updating offset in EventMeshConsumer", e);
            }
        }
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    @Override
    public String toString() {
        return "handleMsgContext={"
            + "consumerGroup=" + consumerGroup
            + ",topic=" + topic
            + ",subscriptionMode=" + subscriptionMode
            + ",consumeTopicConfig=" + consumeTopicConfig
            + ",bizSeqNo=" + bizSeqNo
            + ",uniqueId=" + uniqueId
            + ",ttl=" + ttl
            + ",createTime=" + DateFormatUtils.format(createTime, Constants.DATE_FORMAT) + "}";
    }

}
