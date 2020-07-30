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

package cn.webank.emesher.core.protocol.tcp.client.session.push;

import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.consumer.DeFiBusPushConsumer;
import cn.webank.emesher.constants.ProxyConstants;
import cn.webank.emesher.core.protocol.tcp.client.session.Session;
import cn.webank.emesher.util.ServerGlobal;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyContext;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageConcurrentlyService;
import org.apache.rocketmq.client.impl.consumer.ConsumeMessageService;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DownStreamMsgContext implements Delayed {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public String seq;

    public MessageExt msgExt;

    public Session session;

    public ConsumeMessageConcurrentlyContext consumeConcurrentlyContext;

    public DeFiBusPushConsumer consumer;

    public int retryTimes;

    private long executeTime;

    public long lastPushTime;

    private long createTime;

    private long expireTime;

    public boolean msgFromOtherProxy;

    public DownStreamMsgContext(MessageExt msgExt, Session session, DeFiBusPushConsumer consumer, ConsumeMessageConcurrentlyContext consumeConcurrentlyContext, boolean msgFromOtherProxy) {
        this.seq = String.valueOf(ServerGlobal.getInstance().getMsgCounter().incrementAndGet());
        this.msgExt = msgExt;
        this.session = session;
        this.retryTimes = 0;
        this.consumer = consumer;
        this.consumeConcurrentlyContext = consumeConcurrentlyContext;
        this.lastPushTime = System.currentTimeMillis();
        this.executeTime = System.currentTimeMillis();
        this.createTime = System.currentTimeMillis();
        this.expireTime = System.currentTimeMillis() + Long.valueOf(msgExt.getProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL));
        this.msgFromOtherProxy = msgFromOtherProxy;
    }

    public boolean isExpire() {
        return System.currentTimeMillis() >= expireTime;
    }

    public void ackMsg() {
        if (consumer != null && consumeConcurrentlyContext != null && msgExt != null) {
            List<MessageExt> msgs = new ArrayList<MessageExt>();
            msgs.add(msgExt);
            ConsumeMessageService consumeMessageService = consumer.getDefaultMQPushConsumer().getDefaultMQPushConsumerImpl().getConsumeMessageService();
            ((ConsumeMessageConcurrentlyService)consumeMessageService).updateOffset(msgs, consumeConcurrentlyContext);
            logger.info("ackMsg topic:{}, bizSeq:{}", msgs.get(0).getTopic(), msgs.get(0).getKeys());
        }else{
            logger.warn("ackMsg failed,consumer is null:{}, context is null:{} , msgs is null:{}",consumer == null, consumeConcurrentlyContext == null, msgExt == null);
        }
    }

    public void delay(long delay) {
        this.executeTime = System.currentTimeMillis() + (retryTimes + 1) * delay;
    }

    @Override
    public String toString() {
        return "DownStreamMsgContext{" +
                ",seq=" + seq +
                ",client=" + session.getClient() +
                ",retryTimes=" + retryTimes +
                ",consumer=" + consumer.getDefaultMQPushConsumer().getMessageModel() +
                ",consumerGroup=" + consumer.getDefaultMQPushConsumer().getConsumerGroup() +
                ",topic=" + msgExt.getTopic() +
                ",createTime=" + DateFormatUtils.format(createTime, ProxyConstants.DATE_FORMAT) +
                ",executeTime=" + DateFormatUtils.format(executeTime, ProxyConstants.DATE_FORMAT) +
                ",lastPushTime=" + DateFormatUtils.format(lastPushTime, ProxyConstants.DATE_FORMAT) + '}';
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
