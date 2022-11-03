/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.grpc.sub.app.service;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.grpc.pub.eventmeshmessage.AsyncPublishInstance;
import org.apache.eventmesh.util.Utils;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import static org.apache.eventmesh.common.ExampleConstants.SERVER_PORT;
import static org.apache.eventmesh.common.ExampleConstants.ENV;
import static org.apache.eventmesh.common.ExampleConstants.IDC;
import static org.apache.eventmesh.common.ExampleConstants.SUB_SYS;

@Component
public class SubService implements InitializingBean {

    public static final Logger logger = LoggerFactory.getLogger(SubService.class);

    private EventMeshGrpcConsumer eventMeshGrpcConsumer;

    final Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);

    final SubscriptionItem subscriptionItem = new SubscriptionItem();

    final String localIp = IPUtils.getLocalAddress();
    final String localPort = properties.getProperty(SERVER_PORT);
    final String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
    final String eventMeshGrpcPort = properties.getProperty(ExampleConstants.EVENTMESH_GRPC_PORT);
    final String url = "http://" + localIp + ":" + localPort + "/sub/test";

    // CountDownLatch size is the same as messageSize in AsyncPublishInstance.java (Publisher)
    private CountDownLatch countDownLatch = new CountDownLatch(AsyncPublishInstance.messageSize);

    @Override
    public void afterPropertiesSet() throws Exception {

        EventMeshGrpcClientConfig eventMeshClientConfig = EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .consumerGroup(ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
            .env(ENV).idc(IDC)
            .sys(SUB_SYS).build();

        eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);
        eventMeshGrpcConsumer.init();

        subscriptionItem.setTopic(ExampleConstants.EVENTMESH_GRPC_ASYNC_TEST_TOPIC);
        subscriptionItem.setMode(SubscriptionMode.CLUSTERING);
        subscriptionItem.setType(SubscriptionType.ASYNC);

        eventMeshGrpcConsumer.subscribe(Collections.singletonList(subscriptionItem), url);

        // Wait for all messaged to be consumed
        Thread stopThread = new Thread(() -> {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("stopThread start....");
            throw new RuntimeException();
        });
        stopThread.start();
    }

    @PreDestroy
    public void cleanup() {
        logger.info("start destory ....");
        try {
            eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(subscriptionItem), url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try (final EventMeshGrpcConsumer ignore = eventMeshGrpcConsumer) {
            // close consumer
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("end destory.");
    }

    /**
     * Count the message already consumed
     */
    public void consumeMessage(String msg) {
        logger.info("consume message: {}", msg);
        countDownLatch.countDown();
        logger.info("remaining number of messages to be consumed: {}", countDownLatch.getCount());
    }
}
