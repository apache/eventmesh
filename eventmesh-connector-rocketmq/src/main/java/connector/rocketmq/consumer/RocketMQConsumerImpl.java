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

package connector.rocketmq.consumer;

import com.webank.runtime.configuration.CommonConfiguration;
import com.webank.runtime.core.plugin.impl.MeshMQConsumer;
import com.webank.runtime.patch.ProxyConsumeConcurrentlyContext;
import io.openmessaging.*;
import io.openmessaging.consumer.MessageListener;
import io.openmessaging.consumer.PushConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RocketMQConsumerImpl implements MeshMQConsumer {

    public Logger logger = LoggerFactory.getLogger(this.getClass());

    public Logger messageLogger = LoggerFactory.getLogger("message");

    public final String DEFAULT_ACCESS_DRIVER = "connector.rocketmq.MessagingAccessPointImpl";

    private PushConsumer pushConsumer;

    @Override
    public synchronized void init(boolean isBroadcast, CommonConfiguration commonConfiguration,
                                  String consumerGroup) throws Exception {
        KeyValue properties = OMS.newKeyValue().put(OMSBuiltinKeys.DRIVER_IMPL, DEFAULT_ACCESS_DRIVER);
        properties.put("ACCESS_POINTS", commonConfiguration.namesrvAddr)
                .put("REGION", "namespace")
                .put(OMSBuiltinKeys.CONSUMER_ID, consumerGroup);
        MessagingAccessPoint messagingAccessPoint = OMS.getMessagingAccessPoint(commonConfiguration.namesrvAddr, properties);
        pushConsumer = messagingAccessPoint.createPushConsumer();
    }

    @Override
    public void setInstanceName(String instanceName) {
        ((DefaultMQPushConsumer)pushConsumer).setInstanceName(instanceName);
    }

    @Override
    public void registerMessageListener(MessageListenerConcurrently listener) {
        ((DefaultMQPushConsumer)pushConsumer).setMessageListener(listener);
    }

    @Override
    public synchronized void start() throws Exception {
//        ThreadUtil.randomSleep(50);
//
//        if (this.defaultMQPushConsumer.getMessageListener() == null) {
//            throw new Exception("no messageListener has been registered");
//        }

        pushConsumer.startup();

//        defaultMQPushConsumer.unsubscribe(MixAll.getRetryTopic(defaultMQPushConsumer.getConsumerGroup()));
    }

    @Override
    public void subscribe(String topic) throws Exception {
//        defaultMQPushConsumer.subscribe(topic, "*");
        pushConsumer.attachQueue(topic, new MessageListener() {
            @Override
            public void onReceived(Message message, Context context) {
                context.ack();
            }
        });
    }

    @Override
    public void unsubscribe(String topic) throws Exception {
//        defaultMQPushConsumer.unsubscribe(topic);
        pushConsumer.detachQueue(topic);
    }

    @Override
    public boolean isPause() {
        return pushConsumer.isSuspended();
    }

    @Override
    public void pause() {
//        defaultMQPushConsumer.getDefaultMQPushConsumerImpl().setPause(true);
        pushConsumer.suspend();
    }

    @Override
    public synchronized void shutdown() throws Exception {
//        defaultMQPushConsumer.shutdown();
        pushConsumer.shutdown();
    }

    @Override
    public void updateOffset(List<MessageExt> msgs, ProxyConsumeConcurrentlyContext context) {
//        ConsumeMessageService consumeMessageService = defaultMQPushConsumer.getDefaultMQPushConsumerImpl().getConsumeMessageService();
//        ((ConsumeMessageConcurrentlyService) consumeMessageService).updateOffset(msgs, context);
        MessageListenerConcurrently pushConsumerMessageListener = (MessageListenerConcurrently) ((DefaultMQPushConsumer)pushConsumer).getMessageListener();
        pushConsumerMessageListener.consumeMessage(msgs, context);
    }
}
