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

package com.webank.runtime.core.protocol.tcp.client.session.push;

import com.webank.defibus.consumer.DeFiBusPushConsumer;
import com.webank.runtime.constants.ProxyConstants;
import com.webank.runtime.core.plugin.MQConsumerWrapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import com.webank.runtime.patch.ProxyConsumeConcurrentlyContext;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PushContext {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    private SessionPusher sessionPusher;

    public AtomicLong deliveredMsgsCount = new AtomicLong(0);

    public AtomicLong ackedMsgsCount = new AtomicLong(0);

    public AtomicLong deliverFailMsgsCount = new AtomicLong(0);

    private ConcurrentHashMap<String /** seq */, ClientAckContext> unAckMsg = new ConcurrentHashMap<String, ClientAckContext>();

    private long createTime = System.currentTimeMillis();

    public PushContext(SessionPusher sessionPusher) {
        this.sessionPusher = sessionPusher;
    }

    public void deliveredMsgCount() {
        deliveredMsgsCount.incrementAndGet();
    }

    public void deliverFailMsgCount() {
        deliverFailMsgsCount.incrementAndGet();
    }

    public void unAckMsg(String seq, List<MessageExt> msg, ProxyConsumeConcurrentlyContext context, MQConsumerWrapper consumer) {
        ClientAckContext ackContext = new ClientAckContext(seq,context, msg, consumer);
        unAckMsg.put(seq, ackContext);
        logger.info("put msg in unAckMsg,seq:{},unAckMsgSize:{}", seq, getTotalUnackMsgs());
    }

    public int getTotalUnackMsgs() {
        return unAckMsg.size();
    }

    public void ackMsg(String seq) {
        if (unAckMsg.containsKey(seq)) {
            unAckMsg.get(seq).ackMsg();
            unAckMsg.remove(seq);
            ackedMsgsCount.incrementAndGet();
        }else{
            logger.warn("ackMsg failed,the seq:{} is not in unAckMsg map", seq);
        }
    }

    public ConcurrentHashMap<String, ClientAckContext> getUnAckMsg() {
        return unAckMsg;
    }

    @Override
    public String toString() {
        return "PushContext{" +
                "deliveredMsgsCount=" + deliveredMsgsCount.longValue() +
                ",deliverFailCount=" + deliverFailMsgsCount.longValue() +
                ",ackedMsgsCount=" + ackedMsgsCount.longValue() +
                ",unAckMsg=" + CollectionUtils.size(unAckMsg) +
                ",createTime=" + DateFormatUtils.format(createTime, ProxyConstants.DATE_FORMAT) + '}';
    }
}
