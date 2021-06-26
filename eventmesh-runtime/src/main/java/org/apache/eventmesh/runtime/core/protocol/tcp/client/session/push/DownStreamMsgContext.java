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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import io.openmessaging.api.Message;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.ServerGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownStreamMsgContext implements Delayed {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public String seq;

    public Message msgExt;

    public Session session;

    public AbstractContext consumeConcurrentlyContext;

    public MQConsumerWrapper consumer;

    public int retryTimes;

    public SubscriptionItem subscriptionItem;

    private long executeTime;

    public long lastPushTime;

    private long createTime;

    private long expireTime;

    public boolean msgFromOtherEventMesh;

    public DownStreamMsgContext(Message msgExt, Session session, MQConsumerWrapper consumer, AbstractContext consumeConcurrentlyContext, boolean msgFromOtherEventMesh, SubscriptionItem subscriptionItem) {
        this.seq = String.valueOf(ServerGlobal.getInstance().getMsgCounter().incrementAndGet());
        this.msgExt = msgExt;
        this.session = session;
        this.retryTimes = 0;
        this.consumer = consumer;
        this.consumeConcurrentlyContext = consumeConcurrentlyContext;
        this.lastPushTime = System.currentTimeMillis();
        this.executeTime = System.currentTimeMillis();
        this.createTime = System.currentTimeMillis();
        this.subscriptionItem = subscriptionItem;
        String ttlStr = msgExt.getUserProperties("TTL");
        long ttl = StringUtils.isNumeric(ttlStr) ? Long.parseLong(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
        this.expireTime = System.currentTimeMillis() + ttl;
        this.msgFromOtherEventMesh = msgFromOtherEventMesh;
    }

    public boolean isExpire() {
        return System.currentTimeMillis() >= expireTime;
    }

    public void ackMsg() {
        if (consumer != null && consumeConcurrentlyContext != null && msgExt != null) {
            List<Message> msgs = new ArrayList<Message>();
            msgs.add(msgExt);
            consumer.updateOffset(msgs, consumeConcurrentlyContext);
//            ConsumeMessageService consumeMessageService = consumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
//            ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgs, consumeConcurrentlyContext);
            logger.info("ackMsg seq:{}, topic:{}, bizSeq:{}", seq, msgs.get(0).getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION),
                    msgs.get(0).getSystemProperties(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        } else {
            logger.warn("ackMsg seq:{} failed,consumer is null:{}, context is null:{} , msgs is null:{}", seq, consumer == null, consumeConcurrentlyContext == null, msgExt == null);
        }
    }

    public void delay(long delay) {
        this.executeTime = System.currentTimeMillis() + (retryTimes + 1) * delay;
    }

    @Override
    public String toString() {
        return "DownStreamMsgContext{" +
                ",seq=" + seq +
                ",client=" + (session == null ? null : session.getClient()) +
                ",retryTimes=" + retryTimes +
                ",consumer=" + consumer +
//  todo              ",consumerGroup=" + consumer.getClass().getConsumerGroup() +
                ",topic=" + msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION) +
                ",subscriptionItem=" + subscriptionItem +
                ",createTime=" + DateFormatUtils.format(createTime, EventMeshConstants.DATE_FORMAT) +
                ",executeTime=" + DateFormatUtils.format(executeTime, EventMeshConstants.DATE_FORMAT) +
                ",lastPushTime=" + DateFormatUtils.format(lastPushTime, EventMeshConstants.DATE_FORMAT) + '}';
    }

    @Override
    public int compareTo(Delayed delayed) {
        DownStreamMsgContext context = (DownStreamMsgContext) delayed;
        if (this.executeTime > context.executeTime) {
            return 1;
        } else if (this.executeTime == context.executeTime) {
            return 0;
        } else {
            return -1;
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(this.executeTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
    }
}
