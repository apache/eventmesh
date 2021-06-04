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

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.consumer.LiteConsumer;
import org.apache.eventmesh.common.EventMeshException;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.ThreadUtil;
import org.apache.eventmesh.http.demo.AsyncPublishInstance;
import org.apache.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;

@Component
public class SubService implements InitializingBean {

    public static Logger logger = LoggerFactory.getLogger(SubService.class);

    private LiteConsumer liteConsumer;

    private String eventMeshIPPort = "";

    final Properties properties = Utils.readPropertiesFile("application.properties");

    final List<String> topicList = Arrays.asList("FT0-e-80010001-01-1");
    final String localIp = IPUtil.getLocalAddress();
    final String localPort = properties.getProperty("server.port");
    final String eventMeshIp = properties.getProperty("eventmesh.ip");
    final String eventMeshHttpPort = properties.getProperty("eventmesh.http.port");
    final String url = "http://" + localIp + ":" + localPort + "/sub/test";
    final String env = "P";
    final String idc = "FT";
    final String dcn = "FT0";
    final String subsys = "1234";

    // CountDownLatch size is the same as messageSize in AsyncPublishInstance.java (Publisher)
    private CountDownLatch countDownLatch = new CountDownLatch(AsyncPublishInstance.messageSize);

    @Override
    public void afterPropertiesSet() throws Exception {

        if (StringUtils.isBlank(eventMeshIPPort)) {
            // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
            eventMeshIPPort = eventMeshIp + ":" + eventMeshHttpPort;
        }
        LiteClientConfig eventMeshClientConfig = new LiteClientConfig();
        eventMeshClientConfig.setLiteEventMeshAddr(eventMeshIPPort)
                .setEnv(env)
                .setIdc(idc)
                .setDcn(dcn)
                .setIp(IPUtil.getLocalAddress())
                .setSys(subsys)
                .setPid(String.valueOf(ThreadUtil.getPID()));

        liteConsumer = new LiteConsumer(eventMeshClientConfig);
        liteConsumer.start();
        liteConsumer.heartBeat(topicList, url);
        liteConsumer.subscribe(topicList, url);

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
            liteConsumer.unsubscribe(topicList, url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            liteConsumer.shutdown();
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
