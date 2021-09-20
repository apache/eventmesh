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
import org.apache.eventmesh.runtime.core.protocol.tcp.client.session.retry.RetryContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.apache.eventmesh.runtime.util.ServerGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

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

    public DownStreamMsgContext(Message msgExt, Session session, MQConsumerWrapper consumer, AbstractContext consumeConcurrentlyContext, boolean msgFromOtherEventMesh, SubscriptionItem subscriptionItem) {
        this.seq = String.valueOf(ServerGlobal.getInstance().getMsgCounter().incrementAndGet());
        this.msgExt = msgExt;
        this.session = session;
        this.consumer = consumer;
        this.consumeConcurrentlyContext = consumeConcurrentlyContext;
        this.lastPushTime = System.currentTimeMillis();
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
    public void retry() {
        try {
            logger.info("retry downStream msg start,seq:{},retryTimes:{},bizSeq:{}", this.seq, this.retryTimes, EventMeshUtil.getMessageBizSeq(this.msgExt));

            if (isRetryMsgTimeout(this)) {
                return;
            }
            this.retryTimes++;
            this.lastPushTime = System.currentTimeMillis();

            Session rechoosen = null;
            String topic = this.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION);
            if (!SubscriptionMode.BROADCASTING.equals(this.subscriptionItem.getMode())) {
                rechoosen = this.session.getClientGroupWrapper()
                        .get().getDownstreamDispatchStrategy().select(this.session.getClientGroupWrapper().get().getSysId()
                                , topic
                                , this.session.getClientGroupWrapper().get().getGroupConsumerSessions());
            } else {
                rechoosen = this.session;
            }

            if (rechoosen == null) {
                logger.warn("retry, found no session to downstream msg,seq:{}, retryTimes:{}, bizSeq:{}", this.seq, this.retryTimes, EventMeshUtil.getMessageBizSeq(this.msgExt));

//                //需要手动ack掉没有下发成功的消息
//                eventMeshAckMsg(downStreamMsgContext);

//                //重试找不到下发session不再回发broker或者重试其它eventMesh
//                String bizSeqNo = finalDownStreamMsgContext.msgExt.getKeys();
//                String uniqueId = MapUtils.getString(finalDownStreamMsgContext.msgExt.getProperties(), WeMQConstant.RMB_UNIQ_ID, "");
//                if(EventMeshTCPServer.getAccessConfiguration().eventMeshTcpSendBackEnabled){
//                    sendMsgBackToBroker(finalDownStreamMsgContext.msgExt, bizSeqNo, uniqueId);
//                }else{
//                    //TODO 将消息推给其它eventMesh，待定
//                    sendMsgToOtherEventMesh(finalDownStreamMsgContext.msgExt, bizSeqNo, uniqueId);
//                }
            } else {
                this.session = rechoosen;
                rechoosen.downstreamMsg(this);
                logger.info("retry downStream msg end,seq:{},retryTimes:{},bizSeq:{}", this.seq, this.retryTimes, EventMeshUtil.getMessageBizSeq(this.msgExt));
            }
        } catch (Exception e) {
            logger.error("retry-dispatcher error!", e);
        }
    }

    private boolean isRetryMsgTimeout(DownStreamMsgContext downStreamMsgContext) {
        boolean flag = false;
        String ttlStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.PROPERTY_MESSAGE_TTL);
        long ttl = StringUtils.isNumeric(ttlStr)? Long.parseLong(ttlStr) : EventMeshConstants.DEFAULT_TIMEOUT_IN_MILLISECONDS;;

        String storeTimeStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.STORE_TIME);
        long storeTimestamp = StringUtils.isNumeric(storeTimeStr)? Long.parseLong(storeTimeStr) : 0;
        String leaveTimeStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.LEAVE_TIME);
        long brokerCost = StringUtils.isNumeric(leaveTimeStr) ? Long.parseLong(leaveTimeStr) - storeTimestamp : 0;

        String arriveTimeStr = downStreamMsgContext.msgExt.getUserProperties(EventMeshConstants.ARRIVE_TIME);
        long accessCost = StringUtils.isNumeric(arriveTimeStr) ? System.currentTimeMillis() - Long.parseLong(arriveTimeStr) : 0;
        double elapseTime = brokerCost + accessCost;
        if (elapseTime >= ttl) {
            logger.warn("discard the retry because timeout, seq:{}, retryTimes:{}, bizSeq:{}", downStreamMsgContext.seq, downStreamMsgContext.retryTimes, EventMeshUtil.getMessageBizSeq(downStreamMsgContext.msgExt));
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
        List<Message> msgExts = new ArrayList<Message>();
        msgExts.add(downStreamMsgContext.msgExt);
        logger.warn("eventMeshAckMsg topic:{}, seq:{}, bizSeq:{}", downStreamMsgContext.msgExt.getSystemProperties(Constants.PROPERTY_MESSAGE_DESTINATION),
                downStreamMsgContext.seq, downStreamMsgContext.msgExt.getSystemProperties(EventMeshConstants.PROPERTY_MESSAGE_KEYS));
        downStreamMsgContext.consumer.updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
//        ConsumeMessageService consumeMessageService = downStreamMsgContext.consumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
//        ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgExts, downStreamMsgContext.consumeConcurrentlyContext);
    }

}
