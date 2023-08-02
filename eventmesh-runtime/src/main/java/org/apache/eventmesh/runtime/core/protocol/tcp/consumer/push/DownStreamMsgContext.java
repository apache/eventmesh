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

package org.apache.eventmesh.runtime.core.protocol.tcp.consumer.push;

import org.apache.eventmesh.api.AbstractContext;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.plugin.MQConsumerWrapper;
import org.apache.eventmesh.runtime.core.protocol.RetryContext;
import org.apache.eventmesh.runtime.core.protocol.tcp.session.Session;
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
        super(event, String.valueOf(ServerGlobal.getInstance().getMsgCounter().incrementAndGet()));
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
        if (consumer != null && consumeConcurrentlyContext != null && getEvent() != null) {
            List<CloudEvent> events = new ArrayList<>();
            events.add(getEvent());
            consumer.updateOffset(events, consumeConcurrentlyContext);
            log.info("ackMsg seq:{}, topic:{}, bizSeq:{}", getSeq(), events.get(0).getSubject(),
                events.get(0).getExtension(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        } else {
            log.warn("ackMsg seq:{} failed,consumer is null:{}, context is null:{} , msgs is null:{}", getSeq(),
                consumer == null, consumeConcurrentlyContext == null, getEvent() == null);
        }
    }

    @Override
    public String toString() {
        return "DownStreamMsgContext{"
            +
            ",seq=" + getSeq()
            +
            ",client=" + (session == null ? null : session.getClient())
            +
            ",retryTimes=" + getRetryTimes()
            +
            ",consumer=" + consumer
            +
            //  todo              ",consumerGroup=" + consumer.getClass().getConsumerGroup() +
            ",topic=" + getEvent().getSubject()
            +
            ",subscriptionItem=" + subscriptionItem
            +
            ",createTime=" + DateFormatUtils.format(createTime, EventMeshConstants.DATE_FORMAT)
            +
            ",executeTime=" + DateFormatUtils.format(getExecuteTime(), EventMeshConstants.DATE_FORMAT)
            +
            ",lastPushTime=" + DateFormatUtils.format(lastPushTime, EventMeshConstants.DATE_FORMAT)
            + '}';
    }

    @Override
    public void retry() {
        try {
            log.info("retry downStream msg start,seq:{},retryTimes:{},bizSeq:{}", getSeq(), getRetryTimes(),
                this.getSeq());

            if (isRetryMsgTimeout(this)) {
                return;
            }
            increaseRetryTimes();
            this.lastPushTime = System.currentTimeMillis();

            Session rechoosen;
            String topic = getEvent().getSubject();
            if (SubscriptionMode.BROADCASTING != this.subscriptionItem.getMode()) {
                rechoosen = Objects.requireNonNull(this.session.getSessionMap().get())
                    .getDownstreamDispatchStrategy().select(Objects.requireNonNull(this.session.getSessionMap().get()).getSysId(),
                        topic, Objects.requireNonNull(this.session.getSessionMap().get()).getConsumerMap().getGroupConsumerSessions());
            } else {
                rechoosen = this.session;
            }

            if (rechoosen == null) {
                log.warn("retry, found no session to downstream msg,seq:{}, retryTimes:{}, bizSeq:{}", getSeq(),
                    getEvent(), EventMeshUtil.getMessageBizSeq(getEvent()));
            } else {
                this.session = rechoosen;
                rechoosen.downstreamMsg(this);
                log.info("retry downStream msg end,seq:{},retryTimes:{},bizSeq:{}", getSeq(), getRetryTimes(),
                        EventMeshUtil.getMessageBizSeq(getEvent()));
            }
        } catch (Exception e) {
            log.error("retry-dispatcher error!", e);
        }
    }

    private boolean isRetryMsgTimeout(DownStreamMsgContext downStreamMsgContext) {
        boolean flag = false;
        String ttlStr = (String) downStreamMsgContext.getEvent().getExtension(EventMeshConstants.PROPERTY_MESSAGE_TTL);
        long ttl = StringUtils.isNumeric(ttlStr) ? Long.parseLong(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;

        String storeTimeStr = (String) downStreamMsgContext.getEvent().getExtension(EventMeshConstants.STORE_TIME);
        long storeTimestamp = StringUtils.isNumeric(storeTimeStr) ? Long.parseLong(storeTimeStr) : 0;
        String leaveTimeStr = (String) downStreamMsgContext.getEvent().getExtension(EventMeshConstants.LEAVE_TIME);
        long brokerCost = StringUtils.isNumeric(leaveTimeStr) ? Long.parseLong(leaveTimeStr) - storeTimestamp : 0;

        String arriveTimeStr = (String) downStreamMsgContext.getEvent().getExtension(EventMeshConstants.ARRIVE_TIME);
        long accessCost = StringUtils.isNumeric(arriveTimeStr) ? System.currentTimeMillis() - Long.parseLong(arriveTimeStr)
            : 0;

        double elapseTime = brokerCost + accessCost;
        if (elapseTime >= ttl) {
            log.warn("discard the retry because timeout, seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.getSeq(),
                downStreamMsgContext.getRetryTimes(), EventMeshUtil.getMessageBizSeq(downStreamMsgContext.getEvent()));
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
        msgExts.add(downStreamMsgContext.getEvent());
        log.warn("eventMeshAckMsg topic:{}, seq:{}, bizSeq:{}", downStreamMsgContext.getEvent().getSubject(),
            downStreamMsgContext.getSeq(), EventMeshUtil.getMessageBizSeq(downStreamMsgContext.getEvent()));
        downStreamMsgContext.consumer.updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
    }

}
