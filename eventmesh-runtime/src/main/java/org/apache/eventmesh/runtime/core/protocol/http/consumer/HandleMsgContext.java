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

package org.apache.eventmesh.runtime.core.protocol.http.consumer;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupConf;
import org.apache.eventmesh.runtime.core.consumergroup.ConsumerGroupTopicConf;
import org.apache.eventmesh.runtime.core.protocol.consumer.HandleMessageContext;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HandleMsgContext implements HandleMessageContext {

    public static final Logger MESSAGE_LOGGER = LoggerFactory.getLogger(EventMeshConstants.MESSAGE);

    private String msgRandomNo;

    private String consumerGroup;

    private EventMeshConsumer eventMeshConsumer;

    private String bizSeqNo;

    private String uniqueId;

    private String topic;

    private SubscriptionItem subscriptionItem;

    private CloudEvent event;

    private int ttl;

    private long createTime = System.currentTimeMillis();

    private AbstractContext context;

    private ConsumerGroupConf consumerGroupConfig;

    private final transient EventMeshHTTPServer eventMeshHTTPServer;

    private ConsumerGroupTopicConf consumeTopicConfig;

    private Map<String, String> props;

    public HandleMsgContext(final String msgRandomNo,
        final String consumerGroup,
        final EventMeshConsumer eventMeshConsumer,
        final String topic,
        final CloudEvent event,
        final SubscriptionItem subscriptionItem,
        final AbstractContext context,
        final ConsumerGroupConf consumerGroupConfig,
        final EventMeshHTTPServer eventMeshHTTPServer,
        final String bizSeqNo,
        final String uniqueId,
        final ConsumerGroupTopicConf consumeTopicConfig) {
        this.msgRandomNo = msgRandomNo;
        this.consumerGroup = consumerGroup;
        this.eventMeshConsumer = eventMeshConsumer;
        this.topic = topic;
        this.event = event;
        this.subscriptionItem = subscriptionItem;
        this.context = context;
        this.consumerGroupConfig = consumerGroupConfig;
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.bizSeqNo = bizSeqNo;
        this.uniqueId = uniqueId;
        this.consumeTopicConfig = consumeTopicConfig;

        final String ttlStr = (String) event.getExtension(Constants.PROPERTY_MESSAGE_TIMEOUT);
        this.ttl = StringUtils.isNumeric(ttlStr) ? Integer.parseInt(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
    }

    public void addProp(final String key, final String val) {
        if (props == null) {
            props = new HashMap<>();
        }
        props.put(key, val);
    }

    public String getProp(final String key) {
        return props.get(key);
    }

    public String getMsgRandomNo() {
        return msgRandomNo;
    }

    public void setMsgRandomNo(final String msgRandomNo) {
        this.msgRandomNo = msgRandomNo;
    }

    public ConsumerGroupTopicConf getConsumeTopicConfig() {
        return consumeTopicConfig;
    }

    public void setConsumeTopicConfig(final ConsumerGroupTopicConf consumeTopicConfig) {
        this.consumeTopicConfig = consumeTopicConfig;
    }

    public String getBizSeqNo() {
        return bizSeqNo;
    }

    public void setBizSeqNo(final String bizSeqNo) {
        this.bizSeqNo = bizSeqNo;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(final String consumerGroup) {
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

    public void setTopic(final String topic) {
        this.topic = topic;
    }

    public CloudEvent getEvent() {
        return event;
    }

    public void setEvent(final CloudEvent event) {
        this.event = event;
    }

    public SubscriptionItem getSubscriptionItem() {
        return subscriptionItem;
    }

    public void setSubscriptionItem(final SubscriptionItem subscriptionItem) {
        this.subscriptionItem = subscriptionItem;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(final long createTime) {
        this.createTime = createTime;
    }

    public AbstractContext getContext() {
        return context;
    }

    public void setContext(final AbstractContext context) {
        this.context = context;
    }

    public ConsumerGroupConf getConsumerGroupConfig() {
        return consumerGroupConfig;
    }

    public void setConsumerGroupConfig(final ConsumerGroupConf consumerGroupConfig) {
        this.consumerGroupConfig = consumerGroupConfig;
    }

    public EventMeshHTTPServer getEventMeshHTTPServer() {
        return eventMeshHTTPServer;
    }

    public void finish() {
        if (Objects.nonNull(eventMeshConsumer) && Objects.nonNull(context) && Objects.nonNull(event)) {
            MESSAGE_LOGGER.info("messageAcked|group={}|topic={}|bizSeq={}|uniqId={}|msgRandomNo={}|queueId={}|queueOffset={}",
                consumerGroup, topic, bizSeqNo, uniqueId, msgRandomNo, event.getExtension(Constants.PROPERTY_MESSAGE_QUEUE_ID),
                event.getExtension(Constants.PROPERTY_MESSAGE_QUEUE_OFFSET));
            eventMeshConsumer.updateOffset(topic, subscriptionItem.getMode(), Collections.singletonList(event), context);
        }
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(final String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(final int ttl) {
        this.ttl = ttl;
    }

    @Override
    public String toString() {
        return new StringBuilder()
            .append("handleMsgContext={")
            .append("consumerGroup=")
            .append(consumerGroup)
            .append(",topic=")
            .append(topic)
            .append(",subscriptionItem=")
            .append(subscriptionItem)
            .append(",consumeTopicConfig=")
            .append(consumeTopicConfig)
            .append(",bizSeqNo=")
            .append(bizSeqNo)
            .append(",uniqueId=")
            .append(uniqueId)
            .append(",ttl=")
            .append(ttl)
            .append(",createTime=")
            .append(DateFormatUtils.format(createTime, Constants.DATE_FORMAT_INCLUDE_MILLISECONDS))
            .append('}')
            .toString();
    }

}
