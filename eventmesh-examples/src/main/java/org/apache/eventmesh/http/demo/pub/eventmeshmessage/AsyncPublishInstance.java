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

package org.apache.eventmesh.http.demo.pub.eventmeshmessage;

import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.http.demo.HttpAbstractDemo;
import org.apache.eventmesh.util.Utils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncPublishInstance extends HttpAbstractDemo {

    // This messageSize is also used in SubService.java (Subscriber)
    public static final int MESSAGE_SIZE = 5;

    public static void main(String[] args) throws Exception {
        try (EventMeshHttpProducer eventMeshHttpProducer = new EventMeshHttpProducer(
                initEventMeshHttpClientConfig(ExampleConstants.DEFAULT_EVENTMESH_TEST_PRODUCER_GROUP))) {
            for (int i = 0; i < MESSAGE_SIZE; i++) {
                final Map<String, String> content = new HashMap<>();
                content.put("content", "testPublishMessage");

                final EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                        .bizSeqNo(RandomStringUtils.generateNum(30))
                        .content(JsonUtils.serialize(content))
                        .topic(ExampleConstants.EVENTMESH_HTTP_ASYNC_TEST_TOPIC)
                        .uniqueId(RandomStringUtils.generateNum(30))
                        .build()
                        .addProp(Constants.EVENTMESH_MESSAGE_CONST_TTL, String.valueOf(4 * 1000));
                eventMeshHttpProducer.publish(eventMeshMessage);
            }
            Thread.sleep(30_000);
        }
    }
}
