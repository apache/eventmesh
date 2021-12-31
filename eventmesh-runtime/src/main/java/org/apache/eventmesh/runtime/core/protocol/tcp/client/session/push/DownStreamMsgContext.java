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

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.RetryContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.ServerGlobal;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class DownStreamMsgContext extends RetryContext {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public Session session;

    public AbstractContext consumeConcurrentlyContext;

    public MQConsumerWrapper consumer;

    public SubscriptionItem subscriptionItem;

    public long lastPushTime;

    private long createTime;

    private long expireTime;

    public boolean msgFromOtherEventMesh;

    public DownStreamMsgContext(CloudEvent event, Session session, MQConsumerWrapper consumer,
                                AbstractContext consumeConcurrentlyContext, boolean msgFromOtherEventMesh,
                                SubscriptionItem subscriptionItem) {
        this.seq = String.valueOf(ServerGlobal.getInstance().getMsgCounter().incrementAndGet());
        this.event = event;
        this.session = session;
        this.consumer = consumer;
        this.consumeConcurrentlyContext = consumeConcurrentlyContext;
        this.lastPushTime = System.currentTimeMillis();
        this.createTime = System.currentTimeMillis();
        this.subscriptionItem = subscriptionItem;
        String ttlStr = (String) event.getExtension("TTL");
        //String ttlStr = msgExt.getUserProperties("TTL");
        long ttl = StringUtils.isNumeric(ttlStr) ? Long.parseLong(ttlStr) :
                EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
        this.expireTime = System.currentTimeMillis() + ttl;
        this.msgFromOtherEventMesh = msgFromOtherEventMesh;
    }

    public boolean isExpire() {
        return System.currentTimeMillis() >= expireTime;
    }

    public void ackMsg() {
        if (consumer != null && consumeConcurrentlyContext != null && event != null) {
            List<CloudEvent> events = new ArrayList<CloudEvent>();
            events.add(event);
            consumer.updateOffset(events, consumeConcurrentlyContext);
            //ConsumeMessageService consumeMessageService =
            // consumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
            //((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgs, consumeConcurrentlyContext);
            logger.info("ackMsg seq:{}, topic:{}, bizSeq:{}", seq, events.get(0).getSubject(),
                    events.get(0).getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        } else {
            logger.warn("ackMsg seq:{} failed,consumer is null:{}, context is null:{} , msgs is null:{}", seq,
                    consumer == null, consumeConcurrentlyContext == null, event == null);
        }
    }

    @Override
    public String toString() {
        return "DownStreamMsgContext{"
                +
                ",seq=" + seq
                +
                ",client=" + (session == null ? null : session.getClient())
                +
                ",retryTimes=" + retryTimes
                +
                ",consumer=" + consumer
                +
                //  todo              ",consumerGroup=" + consumer.getClass().getConsumerGroup() +
                ",topic=" + event.getSubject()
                +
                ",subscriptionItem=" + subscriptionItem
                +
                ",createTime=" + DateFormatUtils.format(createTime, EventMeshConstants.DATE_FORMAT)
                +
                ",executeTime=" + DateFormatUtils.format(executeTime, EventMeshConstants.DATE_FORMAT)
                +
                ",lastPushTime=" + DateFormatUtils.format(lastPushTime, EventMeshConstants.DATE_FORMAT)
                + '}';
    }

    @Override
    public void retry() {
        try {
            logger.info("retry downStream msg start,seq:{},retryTimes:{},bizSeq:{}", this.seq, this.retryTimes,
                    EventMeshUtil.getMessageBizSeq(this.event));

            if (isRetryMsgTimeout(this)) {
                return;
            }
            this.retryTimes++;
            this.lastPushTime = System.currentTimeMillis();

            Session rechoosen = null;
            String topic = this.event.getSubject();
            if (!SubscriptionMode.BROADCASTING.equals(this.subscriptionItem.getMode())) {
                rechoosen = this.session.getClientGroupWrapper()
                        .get().getDownstreamDispatchStrategy().select(this.session.getClientGroupWrapper().get().getSysId(),
                                topic, this.session.getClientGroupWrapper().get().getGroupConsumerSessions());
            } else {
                rechoosen = this.session;
            }

            if (rechoosen == null) {
                logger.warn("retry, found no session to downstream msg,seq:{}, retryTimes:{}, bizSeq:{}", this.seq,
                        this.retryTimes, EventMeshUtil.getMessageBizSeq(this.event));
            } else {
                this.session = rechoosen;
                rechoosen.downstreamMsg(this);
                logger.info("retry downStream msg end,seq:{},retryTimes:{},bizSeq:{}", this.seq, this.retryTimes,
                        EventMeshUtil.getMessageBizSeq(this.event));
            }
        } catch (Exception e) {
            logger.error("retry-dispatcher error!", e);
        }
    }

    private boolean isRetryMsgTimeout(DownStreamMsgContext downStreamMsgContext) {
        boolean flag = false;
        String ttlStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL);
        long ttl = StringUtils.isNumeric(ttlStr) ? Long.parseLong(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;
        ;

        String storeTimeStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.STORE_TIME);
        long storeTimestamp = StringUtils.isNumeric(storeTimeStr) ? Long.parseLong(storeTimeStr) : 0;
        String leaveTimeStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.LEAVE_TIME);
        long brokerCost = StringUtils.isNumeric(leaveTimeStr) ? Long.parseLong(leaveTimeStr) - storeTimestamp : 0;

        String arriveTimeStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.ARRIVE_TIME);
        long accessCost = StringUtils.isNumeric(arriveTimeStr) ? System.currentTimeMillis() - Long.parseLong(arriveTimeStr)
                : 0;

        double elapseTime = brokerCost + accessCost;
        if (elapseTime >= ttl) {
            logger.warn("discard the retry because timeout, seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq,
                    downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.event));
            flag = true;
            eventMeshAckMsg(downStreamMsgContext);
        }
        return flag;
    }

    /**
     * eventMesh ack msg
     *
     * @param downStreamMsgContext
     */
    private void eventMeshAckMsg(DownStreamMsgContext downStreamMsgContext) {
        List<CloudEvent> msgExts = new ArrayList<CloudEvent>();
        msgExts.add(downStreamMsgContext.event);
        logger.warn("eventMeshAckMsg topic:{}, seq:{}, bizSeq:{}", downStreamMsgContext.event.getSubject(),
                downStreamMsgContext.seq, downStreamMsgContext.event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        downStreamMsgContext.consumer.updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
    }

}
