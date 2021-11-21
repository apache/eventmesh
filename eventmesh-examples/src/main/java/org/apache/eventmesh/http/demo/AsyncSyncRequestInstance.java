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

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.client.http.producer.RRCallback;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.util.Utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;

import com.google.common.base.Preconditions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncSyncRequestInstance {

    public static void main(String[] args) throws Exception {

        Properties properties = Utils.readPropertiesFile("application.properties");
        Preconditions.checkNotNull(properties, "application.properties");
        final String eventMeshIp = properties.getProperty("eventmesh.ip");
        final String eventMeshHttpPort = properties.getProperty("eventmesh.http.port");

        EventMeshHttpProducer eventMeshHttpProducer = null;
        try {
            String eventMeshIPPort = eventMeshIp + ":" + eventMeshHttpPort;
            final String topic = "TEST-TOPIC-TCP-ASYNC";
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

            final long startTime = System.currentTimeMillis();
            final EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .bizSeqNo(RandomStringUtils.generateNum(30))
                .content("testAsyncMessage")
                .topic(topic)
                .uniqueId(RandomStringUtils.generateNum(30)).build();

            eventMeshHttpProducer.request(eventMeshMessage, new RRCallback<EventMeshMessage>() {
                @Override
                public void onSuccess(EventMeshMessage o) {
                    log.debug("sendmsg : {}, return : {}, cost:{}ms", eventMeshMessage.getContent(), o.getContent(),
                        System.currentTimeMillis() - startTime);
                }

                @Override
                public void onException(Throwable e) {
                    log.debug("send msg failed", e);
                }
            }, 3000);

            Thread.sleep(2000);
        } catch (Exception e) {
            log.warn("async send msg failed", e);
        }

        Thread.sleep(30000);
        try (final EventMeshHttpProducer ignore = eventMeshHttpProducer) {
            // close producer
        } catch (Exception e1) {
            log.warn("producer shutdown exception", e1);
        }
    }
}
