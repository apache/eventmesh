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

package cn.webank.defibus.consumer;

import cn.webank.defibus.client.common.DeFiBusClientUtil;
import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.message.DeFiBusMessageConst;
import cn.webank.defibus.producer.DeFiBusProducer;
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DeFiBusMessageListenerConcurrentlyWithReply implements MessageListenerConcurrently {
    private static final Logger LOG = LoggerFactory.getLogger(DeFiBusMessageListenerConcurrentlyWithReply.class);
    private DeFiBusProducer deFiBusProducer;

    public DeFiBusMessageListenerConcurrentlyWithReply(DeFiBusProducer deFiBusProducer) {
        this.deFiBusProducer = deFiBusProducer;
        if (!deFiBusProducer.isStart()) {
            try {
                deFiBusProducer.start();
            } catch (MQClientException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        List<MessageExt> nonTimeOutMsgs = new ArrayList<MessageExt>();
        for (MessageExt msgExt : msgs) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("begin to receive message: " + msgExt);
            }

            long ttl = 0;
            try {
                ttl = Long.valueOf(msgExt.getUserProperty(DeFiBusConstant.PROPERTY_MESSAGE_TTL));
            } catch (NumberFormatException e) {
                LOG.warn("receive illegal message, ttl format err, ack immediately." + msgExt.toString());
                continue;
            }

            long storeTimestamp = msgExt.getStoreTimestamp();
            long leaveTime = -1;
            if (msgExt.getProperties().get(DeFiBusMessageConst.LEAVE_TIME) != null) {
                leaveTime = Long.valueOf(msgExt.getProperties().get(DeFiBusMessageConst.LEAVE_TIME));
            }

            double elapseTime = 0L;
            if (leaveTime != -1) {
                elapseTime = leaveTime - storeTimestamp;
            }
            if (elapseTime >= ttl) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("discard timeout message : " + msgExt.toString());
                }
                continue;
            }
            nonTimeOutMsgs.add(msgExt);
        }

        if (nonTimeOutMsgs.size() == 0) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        ConsumeConcurrentlyStatus status = ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        try {
            for (MessageExt msg : nonTimeOutMsgs) {
                String replyContent = handleMessage(msg, context);
                Message replyMsg = DeFiBusClientUtil.createReplyMessage(msg, replyContent.getBytes());
                deFiBusProducer.reply(replyMsg, null);
            }
        } catch (Throwable e) {
            LOG.info("handleMessage fail", e);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        return status;
    }

    public abstract String handleMessage(MessageExt msg, ConsumeConcurrentlyContext context);

}
