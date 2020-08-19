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

package com.webank.emesher.core.protocol.tcp.client.session.push;

import com.webank.defibus.common.DeFiBusConstant;
import com.webank.defibus.consumer.DeFiBusPushConsumer;
import com.webank.emesher.constants.ProxyConstants;
import com.webank.emesher.util.ProxyUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyContext;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ClientAckContext {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private String seq;

    private ConsumeMessageConcurrentlyContext context;

    private long createTime;

    private long expireTime;

    private List<MessageExt> msgs;

    private DeFiBusPushConsumer consumer;

    public ClientAckContext(String seq, ConsumeMessageConcurrentlyContext context, List<MessageExt> msgs, DeFiBusPushConsumer consumer) {
        this.seq = seq;
        this.context = context;
        this.msgs = msgs;
        this.consumer = consumer;
        this.createTime = System.currentTimeMillis();
        this.expireTime = System.currentTimeMillis() + Long.valueOf(msgs.get(0).getProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL));
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

    public ConsumeMessageConcurrentlyContext getContext() {
        return context;
    }

    public void setContext(ConsumeMessageConcurrentlyContext context) {
        this.context = context;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public List<MessageExt> getMsgs() {
        return msgs;
    }

    public void setMsgs(List<MessageExt> msgs) {
        this.msgs = msgs;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public DeFiBusPushConsumer getConsumer() {
        return consumer;
    }

    public void ackMsg() {
        if (consumer != null && context != null && msgs != null) {
            ConsumeMessageService consumeMessageService = consumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
            ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgs, context);
            logger.info("ackMsg topic:{}, bizSeq:{}", msgs.get(0).getTopic(), ProxyUtil.getMessageBizSeq(msgs.get(0)));
        }else{
            logger.warn("ackMsg failed,consumer is null:{}, context is null:{} , msgs is null:{}",consumer == null, context == null, msgs == null);
        }
    }

    @Override
    public String toString() {
        return "ClientAckContext{" +
                ",seq=" + seq +
                ",consumer=" + consumer.getDefaultMQPushConsumer().getMessageModel() +
                ",consumerGroup=" + consumer.getDefaultMQPushConsumer().getConsumerGroup() +
                ",topic=" + (CollectionUtils.size(msgs) > 0 ? msgs.get(0).getTopic() : null) +
                ",createTime=" + DateFormatUtils.format(createTime, ProxyConstants.DATE_FORMAT) +
                ",expireTime=" + DateFormatUtils.format(expireTime, ProxyConstants.DATE_FORMAT) + '}';
    }
}
