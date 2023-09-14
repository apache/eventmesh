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
import org.apache.eventmesh.runtime.core.retry.RetryContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.Session;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.ServerGlobal;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.cloudevents.CloudEvent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DownStreamMsgContext extends RetryContext {

    @Getter
    @Setter
    private Session session;

    private final AbstractContext consumeConcurrentlyContext;

    private final MQConsumerWrapper consumer;

    @Getter
    private final SubscriptionItem subscriptionItem;

    private long lastPushTime;

    private final long createTime;

    private final long expireTime;

    public final boolean msgFromOtherEventMesh;

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
            List<CloudEvent> events = new ArrayList<>();
            events.add(event);
            consumer.updateOffset(events, consumeConcurrentlyContext);
            log.info("ackMsg seq:{}, topic:{}, bizSeq:{}", seq, events.get(0).getSubject(),
                events.get(0).getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        } else {
            log.warn("ackMsg seq:{} failed,consumer is null:{}, context is null:{} , msgs is null:{}", seq,
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
            log.info("retry downStream msg start,seq:{},retryTimes:{},bizSeq:{}", this.seq, this.retryTimes,
                EventMeshUtil.getMessageBizSeq(this.event));

            if (isRetryMsgTimeout(this)) {
                return;
            }
            this.retryTimes++;
            this.lastPushTime = System.currentTimeMillis();

            Session rechoosen;
            String topic = this.event.getSubject();
            if (SubscriptionMode.BROADCASTING != this.subscriptionItem.getMode()) {
                rechoosen = Objects.requireNonNull(this.session.getClientGroupWrapper().get())
                    .getDownstreamDispatchStrategy().select(Objects.requireNonNull(this.session.getClientGroupWrapper().get()).getSysId(),
                        topic, Objects.requireNonNull(this.session.getClientGroupWrapper().get()).getGroupConsumerSessions());
            } else {
                rechoosen = this.session;
            }

            if (rechoosen == null) {
                log.warn("retry, found no session to downstream msg,seq:{}, retryTimes:{}, bizSeq:{}", this.seq,
                    this.retryTimes, EventMeshUtil.getMessageBizSeq(this.event));
            } else {
                this.session = rechoosen;
                rechoosen.downstreamMsg(this);
                log.info("retry downStream msg end,seq:{},retryTimes:{},bizSeq:{}", this.seq, this.retryTimes,
                    EventMeshUtil.getMessageBizSeq(this.event));
            }
        } catch (Exception e) {
            log.error("retry-dispatcher error!", e);
        }
    }

    private boolean isRetryMsgTimeout(DownStreamMsgContext downStreamMsgContext) {
        boolean flag = false;
        String ttlStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL);
        long ttl = StringUtils.isNumeric(ttlStr) ? Long.parseLong(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;

        String storeTimeStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.STORE_TIME);
        long storeTimestamp = StringUtils.isNumeric(storeTimeStr) ? Long.parseLong(storeTimeStr) : 0;
        String leaveTimeStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.LEAVE_TIME);
        long brokerCost = StringUtils.isNumeric(leaveTimeStr) ? Long.parseLong(leaveTimeStr) - storeTimestamp : 0;

        String arriveTimeStr = (String) downStreamMsgContext.event.getExtension(EventMeshConstants.ARRIVE_TIME);
        long accessCost = StringUtils.isNumeric(arriveTimeStr) ? System.currentTimeMillis() - Long.parseLong(arriveTimeStr)
            : 0;

        double elapseTime = brokerCost + accessCost;
        if (elapseTime >= ttl) {
            log.warn("discard the retry because timeout, seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq,
                downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.event));
            flag = true;
            eventMeshAckMsg(downStreamMsgContext);
        }
        return flag;
    }

    /**
     * eventMesh ack msg
     *
     * @param downStreamMsgContext Down Stream Message Context
     */
    private void eventMeshAckMsg(DownStreamMsgContext downStreamMsgContext) {
        List<CloudEvent> msgExts = new ArrayList<>();
        msgExts.add(downStreamMsgContext.event);
        log.warn("eventMeshAckMsg topic:{}, seq:{}, bizSeq:{}", downStreamMsgContext.event.getSubject(),
            downStreamMsgContext.seq, downStreamMsgContext.event.getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        downStreamMsgContext.consumer.updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
    }

}
