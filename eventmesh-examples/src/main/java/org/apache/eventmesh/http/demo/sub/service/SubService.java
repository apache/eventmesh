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

package org.apache.eventmesh.http.demo.sub.service;

import static org.apache.eventmesh.common.ExampleConstants.ENV;
import static org.apache.eventmesh.common.ExampleConstants.IDC;
import static org.apache.eventmesh.common.ExampleConstants.SERVER_PORT;
import static org.apache.eventmesh.common.ExampleConstants.SUB_SYS;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.consumer.EventMeshHttpConsumer;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.http.demo.pub.eventmeshmessage.AsyncPublishInstance;
import org.apache.eventmesh.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

@Component
public class SubService implements InitializingBean {

    public static final Logger LOGGER = LoggerFactory.getLogger(SubService.class);

    private transient EventMeshHttpConsumer eventMeshHttpConsumer;

    private final transient Properties properties = Utils.readPropertiesFile(ExampleConstants.CONFIG_FILE_NAME);

    private final transient List<SubscriptionItem> topicList = Lists.newArrayList(
            new SubscriptionItem(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC, SubscriptionMode.CLUSTERING, SubscriptionType.ASYNC)
    );
    private final transient String localIp = IPUtils.getLocalAddress();
    private final transient String localPort = properties.getProperty(SERVER_PORT);
    private final transient String eventMeshIp = properties.getProperty(ExampleConstants.EVENTMESH_IP);
    private final transient String eventMeshHttpPort = properties.getProperty(ExampleConstants.EVENTMESH_HTTP_PORT);
    private final transient String url = "http://" + localIp + ":" + localPort + "/sub/test";

    // CountDownLatch size is the same as messageSize in AsyncPublishInstance.java (Publisher)
    private transient CountDownLatch countDownLatch = new CountDownLatch(AsyncPublishInstance.messageSize);

    @Override
    public void afterPropertiesSet() throws Exception {

        final String eventMeshIPPort = eventMeshIp + ":" + eventMeshHttpPort;
        EventMeshHttpClientConfig eventMeshClientConfig = EventMeshHttpClientConfig.builder()
                .liteEventMeshAddr(eventMeshIPPort)
                .consumerGroup(ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP)
                .env(ENV)
                .idc(IDC)
                .ip(IPUtils.getLocalAddress())
                .sys(SUB_SYS)
                .pid(String.valueOf(ThreadUtils.getPID())).build();

        eventMeshHttpConsumer = new EventMeshHttpConsumer(eventMeshClientConfig);
        eventMeshHttpConsumer.heartBeat(topicList, url);
        eventMeshHttpConsumer.subscribe(topicList, url);

        // Wait for all messaged to be consumed
        Thread stopThread = new Thread(() -> {
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                LOGGER.error("interrupted exception", e);
            }
            LOGGER.info("stopThread start....");
            throw new RuntimeException();
        });
        stopThread.start();
    }

    @PreDestroy
    public void cleanup() {
        LOGGER.info("start destory ....");
        try {
            List<String> unSubList = new ArrayList<>();
            for (SubscriptionItem item : topicList) {
                unSubList.add(item.getTopic());
            }
            eventMeshHttpConsumer.unsubscribe(unSubList, url);
        } catch (Exception e) {
            LOGGER.error("unsubscribe exception", e);
        }

        try (final EventMeshHttpConsumer ignore = eventMeshHttpConsumer) {
            // close consumer
        } catch (Exception e) {
            LOGGER.error("close exception", e);
        }

        LOGGER.info("end destory.");
    }

    /**
     * Count the message already consumed
     */
    public void consumeMessage(String msg) {
        LOGGER.info("consume message: {}", msg);
        countDownLatch.countDown();
        LOGGER.info("remaining number: {} of messages to be consumed", countDownLatch.getCount());
    }
}
