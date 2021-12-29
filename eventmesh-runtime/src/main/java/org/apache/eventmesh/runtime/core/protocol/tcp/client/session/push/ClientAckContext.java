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

package org.apache.eventmesh.runtime.core.protocol.tcp.client.session.push;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

public class ClientAckContext {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private String seq;

    private AbstractContext context;

    private long createTime;

    private long expireTime;

    private List<CloudEvent> events;

    private MQConsumerWrapper consumer;

    public ClientAckContext(String seq, AbstractContext context, List<CloudEvent> events, MQConsumerWrapper consumer) {
        this.seq = seq;
        this.context = context;
        this.events = events;
        this.consumer = consumer;
        this.createTime = System.currentTimeMillis();
        String ttlStr = events.get(0).getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL).toString();
        long ttl = StringUtils.isNumeric(ttlStr) ? Long.parseLong(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
        this.expireTime = System.currentTimeMillis() + ttl;
    }

    public boolean isExpire() {
        return System.currentTimeMillis() >= expireTime;
    }

    public String getSeq() {
        return seq;
    }

    public void setSeq(String seq) {
        this.seq = seq;
    }

    public AbstractContext getContext() {
        return context;
    }

    public void setContext(AbstractContext context) {
        this.context = context;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public List<CloudEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CloudEvent> events) {
        this.events = events;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public MQConsumerWrapper getConsumer() {
        return consumer;
    }

    public void ackMsg() {
        if (consumer != null && context != null && events != null) {
            consumer.updateOffset(events, context);
            //ConsumeMessageService consumeMessageService = consumer..getDefaultMQPushConsumerImpl().getConsumeMessageService();
            //((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgs, context);
            logger.info("ackMsg topic:{}, bizSeq:{}", events.get(0).getSubject(), EventMeshUtil.getMessageBizSeq(events.get(0)));
        } else {
            logger.warn("ackMsg failed,consumer is null:{}, context is null:{} , msgs is null:{}",
                    consumer == null, context == null, events == null);
        }
    }

    @Override
    public String toString() {
        return "ClientAckContext{"
                +
                ",seq=" + seq
                +
                //TODO               ",consumer=" + consumer.getDefaultMQPushConsumer().getMessageModel() +
                //               ",consumerGroup=" + consumer.getDefaultMQPushConsumer().getConsumerGroup() +
                ",topic=" + (CollectionUtils.size(events) > 0 ? events.get(0).getSubject() : null)
                +
                ",createTime=" + DateFormatUtils.format(createTime, EventMeshConstants.DATE_FORMAT)
                +
                ",expireTime=" + DateFormatUtils.format(expireTime, EventMeshConstants.DATE_FORMAT) + '}';
    }
}
