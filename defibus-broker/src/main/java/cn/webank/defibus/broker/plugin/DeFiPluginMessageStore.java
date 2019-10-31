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

package cn.webank.defibus.broker.plugin;

import cn.webank.defibus.broker.consumequeue.ConsumeQueueManager;
import cn.webank.defibus.common.DeFiBusConstant;
import org.apache.rocketmq.broker.plugin.AbstractPluginMessageStore;
import org.apache.rocketmq.broker.plugin.MessageStorePluginContext;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExtBatch;
import org.apache.rocketmq.store.GetMessageResult;
import org.apache.rocketmq.store.GetMessageStatus;
import org.apache.rocketmq.store.MessageExtBrokerInner;
import org.apache.rocketmq.store.MessageFilter;
import org.apache.rocketmq.store.MessageStore;
import org.apache.rocketmq.store.PutMessageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeFiPluginMessageStore extends AbstractPluginMessageStore {
    private final static Logger LOG = LoggerFactory.getLogger(LoggerName.BROKER_LOGGER_NAME);
    protected MessageStore next = null;
    protected MessageStorePluginContext context;
    private ConsumeQueueManager deFiQueueManager = ConsumeQueueManager.onlyInstance();
    private final PluginStoreStatService pluginStoreStatService = new PluginStoreStatService();
    private final String clusterName = deFiQueueManager.getBrokerController().getBrokerConfig().getBrokerClusterName();
    private final String brokerName = deFiQueueManager.getBrokerController().getBrokerConfig().getBrokerName();

    public DeFiPluginMessageStore(MessageStorePluginContext context, MessageStore next) {
        super(context, next);
        this.next = next;
        this.context = context;
    }

    @Override
    public void start() throws Exception {
        pluginStoreStatService.start();
        next.start();
    }

    @Override
    public void shutdown() {
        next.shutdown();
        pluginStoreStatService.shutdown();
    }

    @Override
    public PutMessageResult putMessage(MessageExtBrokerInner msg) {
        long startTime = System.nanoTime();
        MessageAccessor.putProperty(msg, DeFiBusConstant.PROPERTY_MESSAGE_CLUSTER, clusterName);
        MessageAccessor.putProperty(msg, DeFiBusConstant.PROPERTY_MESSAGE_BROKER, brokerName);
        msg.setPropertiesString(MessageDecoder.messageProperties2String(msg.getProperties()));

        PutMessageResult result = next.putMessage(msg);
        long eclipseNanoTime = System.nanoTime() - startTime;
        pluginStoreStatService.recordPutTime(eclipseNanoTime);
        return result;
    }

    @Override
    public PutMessageResult putMessages(MessageExtBatch messageExtBatch) {
        long startTime = System.nanoTime();
        PutMessageResult result = next.putMessages(messageExtBatch);
        long eclipseNanoTime = System.nanoTime() - startTime;
        pluginStoreStatService.recordPutTime(eclipseNanoTime);
        return result;
    }

    @Override
    public GetMessageResult getMessage(String group, String topic, int queueId, long offset,
        int maxMsgNums, final MessageFilter messageFilter) {
        long startTime = System.nanoTime();
        GetMessageResult getMessageResult
            = next.getMessage(group, topic, queueId, offset, maxMsgNums, messageFilter);

        if (getMessageResult.getStatus().equals(GetMessageStatus.FOUND)) {
            this.deFiQueueManager.recordLastDeliverOffset(group, topic, queueId, getMessageResult.getNextBeginOffset());
        }

        long eclipseNanoTime = System.nanoTime() - startTime;
        pluginStoreStatService.recordGetTime(eclipseNanoTime);

        return getMessageResult;
    }

    public MessageStore getDefaultMessageStore() {
        return next;
    }
}