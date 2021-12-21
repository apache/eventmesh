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

import org.apache.eventmesh.client.grpc.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionMode;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription.SubscriptionItem.SubscriptionType;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.grpc.pub.AsyncPublishInstance;
import org.apache.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

@Component
public class SubService implements InitializingBean {

    public static Logger logger = LoggerFactory.getLogger(SubService.class);

    private EventMeshGrpcConsumer eventMeshGrpcConsumer;

    final Properties properties = Utils.readPropertiesFile("application.properties");

    final SubscriptionItem subscriptionItem = SubscriptionItem.newBuilder()
        .setTopic("TEST-TOPIC-GRPC-ASYNC")
        .setMode(SubscriptionMode.CLUSTERING)
        .setType(SubscriptionType.ASYNC)
        .build();

    final String                 localIp           = IPUtils.getLocalAddress();
    final String                 localPort         = properties.getProperty("server.port");
    final String                 eventMeshIp       = properties.getProperty("eventmesh.ip");
    final String                 eventMeshGrpcPort = properties.getProperty("eventmesh.grpc.port");
    final String                 url               = "http://" + localIp + ":" + localPort + "/sub/test";
    final String                 env               = "P";
    final String                 idc               = "FT";
    final String                 subsys            = "1234";

    final Subscription subscription = Subscription.newBuilder()
        .setUrl(url)
        .addSubscriptionItems(subscriptionItem)
        .build();

    // CountDownLatch size is the same as messageSize in AsyncPublishInstance.java (Publisher)
    private CountDownLatch countDownLatch = new CountDownLatch(AsyncPublishInstance.messageSize);

    @Override
    public void afterPropertiesSet() throws Exception {

        EventMeshGrpcClientConfig eventMeshClientConfig = EventMeshGrpcClientConfig.builder()
            .serverAddr(eventMeshIp)
            .serverPort(Integer.parseInt(eventMeshGrpcPort))
            .consumerGroup("EventMeshTest-consumerGroup")
            .env(env).idc(idc)
            .sys(subsys).build();

        eventMeshGrpcConsumer = new EventMeshGrpcConsumer(eventMeshClientConfig);
        eventMeshGrpcConsumer.init();
        eventMeshGrpcConsumer.subscribe(subscription);

        // Wait for all messaged to be consumed
        Thread stopThread = new Thread(() -> {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("stopThread start....");
            System.exit(0);
        });
        stopThread.start();
    }

    @PreDestroy
    public void cleanup() {
        logger.info("start destory ....");
        try {
           /* List<String> unSubList = new ArrayList<>();
            for (SubscriptionItem item : topicList) {
                unSubList.add(item.getTopic());
            }*/
           // eventMeshHttpConsumer.unsubscribe(unSubList, url);
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
        logger.info("consume message {}", msg);
        countDownLatch.countDown();
        logger.info("remaining number of messages to be consumed {}", countDownLatch.getCount());
    }
}
