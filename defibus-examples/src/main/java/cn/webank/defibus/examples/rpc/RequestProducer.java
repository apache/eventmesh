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

import cn.webank.defibus.client.common.DeFiBusClientConfig;
import cn.webank.defibus.producer.DeFiBusProducer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestProducer {
    private static final Logger logger = LoggerFactory.getLogger(RequestProducer.class);

    public static void main(String[] args) throws MQClientException {
        DeFiBusClientConfig clientConfig = new DeFiBusClientConfig();
        clientConfig.setClusterPrefix("XL");

        DeFiBusProducer deFiBusProducer = new DeFiBusProducer(clientConfig);
        deFiBusProducer.setNamesrvAddr("127.0.0.1:9876");

        deFiBusProducer.start();

        long ttl = 2000;
        String topic = "RequestTopic";

        final String content = "Hello world";
        Message msg = new Message(topic, content.getBytes());
        try {
            long time = System.currentTimeMillis();

            Message reply = deFiBusProducer.request(msg, ttl);
            long cost = System.currentTimeMillis() - time;

            if (reply == null) {
                logger.warn("request timeout. ");
            } else {
                logger.info("request success. cost: {}ms. reply msg: {}", cost, reply);
            }
        } catch (MQClientException | RemotingException | InterruptedException | MQBrokerException e) {
            logger.warn("{}", e);
        } catch (Exception e) {
            logger.warn("{}", e);
        } finally {
            // normally , we ONLY shutdown DeFiBusProducer when the application exits. In this sample, we shutdown the producer when message is sent.
            deFiBusProducer.shutdown();
        }
    }
}
