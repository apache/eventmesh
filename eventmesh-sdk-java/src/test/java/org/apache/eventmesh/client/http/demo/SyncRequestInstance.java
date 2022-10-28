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

import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncRequestInstance {

    public static final Logger logger = LoggerFactory.getLogger(SyncRequestInstance.class);

    public static void main(String[] args) throws Exception {

        EventMeshHttpProducer eventMeshHttpProducer = null;
        try {
            String eventMeshIPPort = args[0];

            final String topic = args[1];

            if (StringUtils.isBlank(eventMeshIPPort)) {
                // if has multi value, can config as: 127.0.0.1:10105;127.0.0.2:10105
                eventMeshIPPort = "127.0.0.1:10105";
            }

            EventMeshHttpClientConfig eventMeshClientConfig = EventMeshHttpClientConfig.builder()
                    .liteEventMeshAddr(eventMeshIPPort)
                    .producerGroup("EventMeshTest-producerGroup")
                    .env("env")
                    .idc("idc")
                    .ip(IPUtils.getLocalAddress())
                    .sys("1234")
                    .pid(String.valueOf(ThreadUtils.getPID())).build();

            eventMeshHttpProducer = new EventMeshHttpProducer(eventMeshClientConfig);

            long startTime = System.currentTimeMillis();
            EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                    .bizSeqNo(RandomStringUtils.generateNum(30))
                    .content("contentStr with special protocal")
                    .topic(topic)
                    .uniqueId(RandomStringUtils.generateNum(30)).build();

            EventMeshMessage rsp = eventMeshHttpProducer.request(eventMeshMessage, 10000);
            if (logger.isDebugEnabled()) {
                logger.debug("sendmsg : {}, return : {}, cost:{}ms", eventMeshMessage.getContent(), rsp.getContent(),
                        System.currentTimeMillis() - startTime);
            }
        } catch (Exception e) {
            logger.warn("send msg failed", e);
        }

        Thread.sleep(30000);
        try (final EventMeshHttpProducer closed = eventMeshHttpProducer) {
            // close producer
        } catch (Exception e1) {
            logger.warn("producer shutdown exception", e1);
        }
    }
}
