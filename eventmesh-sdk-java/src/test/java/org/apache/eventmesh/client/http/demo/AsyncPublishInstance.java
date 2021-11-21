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

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncPublishInstance {

    public static void main(String[] args) throws Exception {

        String eventMeshIPPort = "127.0.0.1:10105";
        final String topic = "TEST-TOPIC-HTTP-ASYNC";

        EventMeshHttpClientConfig eventMeshClientConfig = EventMeshHttpClientConfig.builder()
            .liteEventMeshAddr(eventMeshIPPort)
            .producerGroup("EventMeshTest-producerGroup")
            .env("env")
            .idc("idc")
            .ip(IPUtils.getLocalAddress())
            .sys("1234")
            .pid(String.valueOf(ThreadUtils.getPID())).build();

        EventMeshHttpProducer eventMeshHttpProducer = new EventMeshHttpProducer(eventMeshClientConfig);
        for (int i = 0; i < 1; i++) {
            EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .bizSeqNo(RandomStringUtils.generateNum(30))
                .content("testPublishMessage")
                .topic(topic)
                .uniqueId(RandomStringUtils.generateNum(30))
                .build()
                .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000));

            eventMeshHttpProducer.publish(eventMeshMessage);
            Thread.sleep(1000);
        }
        Thread.sleep(30000);
        try (EventMeshHttpProducer ignore = eventMeshHttpProducer) {
            // ignore
        }
    }
}
