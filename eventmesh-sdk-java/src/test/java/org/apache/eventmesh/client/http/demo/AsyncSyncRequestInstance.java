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

package org.apache.eventmesh.client.http.demo;


import org.apache.eventmesh.client.http.conf.LiteClientConfig;
import org.apache.eventmesh.client.http.producer.LiteProducer;
import org.apache.eventmesh.client.http.producer.RRCallback;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncSyncRequestInstance {

    public static Logger logger = LoggerFactory.getLogger(AsyncSyncRequestInstance.class);

    public static void main(String[] args) throws Exception {

        LiteProducer liteProducer = null;
        try {
//            String eventMeshIPPort = args[0];
            String eventMeshIPPort = "";
//            final String topic = args[1];
            final String topic = "TEST-TOPIC-HTTP-ASYNC";
            if (StringUtils.isBlank(eventMeshIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                eventMeshIPPort = "127.0.0.1:10105";
            }

            LiteClientConfig eventMeshClientConfig = LiteClientConfig.builder()
                .liteEventMeshAddr(eventMeshIPPort)
                .producerGroup("EventMeshTest-producerGroup")
                .env("env")
                .idc("idc")
                .ip(IPUtils.getLocalAddress())
                .sys("1234")
                .pid(String.valueOf(ThreadUtils.getPID())).build();

            liteProducer = new LiteProducer(eventMeshClientConfig);

            final long startTime = System.currentTimeMillis();
            final EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .bizSeqNo(RandomStringUtils.generateNum(30))
                .content("testAsyncMessage")
                .topic(topic)
                .uniqueId(RandomStringUtils.generateNum(30)).build();

            liteProducer.request(eventMeshMessage, new RRCallback<EventMeshMessage>() {
                @Override
                public void onSuccess(EventMeshMessage o) {
                    logger.debug("sendmsg : {}, return : {}, cost:{}ms", eventMeshMessage.getContent(), o.getContent(),
                        System.currentTimeMillis() - startTime);
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

        Thread.sleep(30000);
        try (final LiteProducer ignore = liteProducer) {
            // close producer
        } catch (Exception e1) {
            logger.warn("producer shutdown exception", e1);
        }
    }
}
