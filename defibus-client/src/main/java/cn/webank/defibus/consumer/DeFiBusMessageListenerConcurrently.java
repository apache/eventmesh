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

import cn.webank.defibus.common.DeFiBusConstant;
import cn.webank.defibus.common.message.DeFiBusMessageConst;
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DeFiBusMessageListenerConcurrently implements MessageListenerConcurrently {
    private static final Logger LOG = LoggerFactory.getLogger(DeFiBusMessageListenerConcurrently.class);

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {

        List<MessageExt> nonTimeOutMsgs = new ArrayList<MessageExt>();
        try {
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
            ConsumeConcurrentlyStatus status = null;
            status = handleMessage(nonTimeOutMsgs, context);

            return status;
        } catch (Throwable e) {
            LOG.warn("handleMessage fail", e);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

    }

    public abstract ConsumeConcurrentlyStatus handleMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context);

}
