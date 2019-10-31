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

package cn.webank.defibus.examples.simple;

import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.consumer.DeFiBusMessageListenerConcurrently;
import cn.webank.defibus.consumer.DeFiBusPushConsumer;
import cn.webank.defibus.producer.DeFiBusProducer;
import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubConsumer {
    private static final Logger logger = LoggerFactory.getLogger(SubConsumer.class);

    public static void main(String[] args) throws MQClientException {
        String topic = "PublishTopic";
        DeFiBusClientConfig deFiBusClientConfig = new DeFiBusClientConfig();
        deFiBusClientConfig.setConsumerGroup("Your-group-name");
        deFiBusClientConfig.setPullBatchSize(32);
        deFiBusClientConfig.setThreadPoolCoreSize(12);
        deFiBusClientConfig.setClusterPrefix("XL");

        DeFiBusProducer deFiBusProducer = new DeFiBusProducer(deFiBusClientConfig);
        deFiBusProducer.setNamesrvAddr("127.0.0.1:9876");
        deFiBusProducer.start();

        DeFiBusPushConsumer deFiBusPushConsumer = new DeFiBusPushConsumer(deFiBusClientConfig);
        deFiBusPushConsumer.setNamesrvAddr("127.0.0.1:9876");
        deFiBusPushConsumer.registerMessageListener(new DeFiBusMessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus handleMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                for (MessageExt msg : msgs) {
                    logger.info("begin handle: " + msg.toString());
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        deFiBusPushConsumer.subscribe(topic);
        deFiBusPushConsumer.start();
    }
}
