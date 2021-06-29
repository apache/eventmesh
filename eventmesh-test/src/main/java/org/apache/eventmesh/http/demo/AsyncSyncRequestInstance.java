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

package org.apache.eventmesh.http.demo;

import java.util.Properties;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.producer.LiteProducer;
import org.apache.eventmesh.client.http.producer.RRCallback;
import org.apache.eventmesh.common.IPUtil;
import org.apache.eventmesh.common.LiteMessage;
import org.apache.eventmesh.common.ThreadUtil;
import org.apache.eventmesh.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncSyncRequestInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncSyncRequestInstance.class);

    public static void main(String[] args) throws Exception {

        Properties properties = Utils.readPropertiesFile("application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final String eventMeshHttpPort = properties.getProperty("eventmesh.http.port");

        LiteProducer liteProducer = null;
        try {
//            String eventMeshIPPort = args[0];
            String eventMeshIPPort = eventMeshIp + ":" + eventMeshHttpPort;
//            final String topic = args[1];
            final String topic = "TEST-TOPIC-TCP-ASYNC";
            if (StringUtils.isBlank(eventMeshIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                eventMeshIPPort = "127.0.0.1:10105";
            }

            LiteClientConfig eventMeshClientConfig = new LiteClientConfig();
            eventMeshClientConfig.setLiteEventMeshAddr(eventMeshIPPort)
                    .setProducerGroup("EventMeshTest-producerGroup")
                    .setEnv("env")
                    .setIdc("idc")
                    .setIp(IPUtil.getLocalAddress())
                    .setSys("1234")
                    .setPid(String.valueOf(ThreadUtil.getPID()));

            liteProducer = new LiteProducer(eventMeshClientConfig);

            final long startTime = System.currentTimeMillis();
            final LiteMessage liteMessage = new LiteMessage();
            liteMessage.setBizSeqNo(RandomStringUtils.randomNumeric(30))
                    .setContent("testAsyncMessage")
                    .setTopic(topic)
                    .setUniqueId(RandomStringUtils.randomNumeric(30));

            liteProducer.request(liteMessage, new RRCallback() {
                @Override
                public void onSuccess(LiteMessage o) {
                    logger.debug("sendmsg : {}, return : {}, cost:{}ms", liteMessage.getContent(), o.getContent(), System.currentTimeMillis() - startTime);
                }

                @Override
                public void onException(Throwable e) {
                    logger.debug("sendmsg failed", e);
                }
            }, 3000);

            Thread.sleep(2000);
        } catch (Exception e) {
            logger.warn("async send msg failed", e);
        }

        try {
            Thread.sleep(30000);
            if (liteProducer != null) {
                liteProducer.shutdown();
            }
        } catch (Exception e1) {
            logger.warn("producer shutdown exception", e1);
        }
    }
}
