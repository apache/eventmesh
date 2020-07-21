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

package cn.webank.defibus.examples.rpc;

import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.consumer.DeFiBusMessageListenerConcurrentlyWithReply;
import cn.webank.defibus.consumer.DeFiBusPushConsumer;
import cn.webank.defibus.producer.DeFiBusProducer;

/**
 * the example of responder with auto reply
 */
public class ResponseConsumerAutoReply {
    public static void main(String[] args) {
        DeFiBusClientConfig deFiBusClientConfig = new DeFiBusClientConfig();
        deFiBusClientConfig.setNamesrvAddr("");
        DeFiBusProducer deFiBusProducer = new DeFiBusProducer(deFiBusClientConfig);
        DeFiBusPushConsumer deFiBusPushConsumer = new DeFiBusPushConsumer(deFiBusClientConfig);
        deFiBusPushConsumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        deFiBusPushConsumer.getDefaultMQPushConsumer().setMessageModel(MessageModel.CLUSTERING);
        deFiBusPushConsumer.registerMessageListener(new DeFiBusMessageListenerConcurrentlyWithReply(deFiBusProducer) {
            @Override
            public String handleMessage(MessageExt msg, ConsumeConcurrentlyContext context) {
                //1. biz handle logic

                //2. create reply content

                return "A reply message content";
            }
        });
        try {
            deFiBusProducer.start();
            deFiBusPushConsumer.subscribe("REQUEST_REPLY_TOPIC");
            deFiBusPushConsumer.start();
        } catch (MQClientException e) {
            e.printStackTrace();
        } finally {
            deFiBusProducer.shutdown();
        }

        //shutdown the consumer when application exits.
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                deFiBusPushConsumer.shutdown();
            }
        });

    }
}
