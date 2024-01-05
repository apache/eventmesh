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

import org.apache.eventmesh.client.http.producer.EventMeshHttpProducer;
import org.apache.eventmesh.client.http.producer.RRCallback;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.RandomStringUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.http.demo.HttpAbstractDemo;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AsyncSyncRequestInstance extends HttpAbstractDemo {

    public static void main(String[] args) throws Exception {

        try (EventMeshHttpProducer eventMeshHttpProducer = new EventMeshHttpProducer(
            initEventMeshHttpClientConfig(ExampleConstants.DEFAULT_EVENTMESH_TEST_PRODUCER_GROUP))) {
            final long startTime = System.currentTimeMillis();
            final EventMeshMessage eventMeshMessage = EventMeshMessage.builder()
                .bizSeqNo(RandomStringUtils.generateNum(30))
                .content("testAsyncMessage")
                .topic(ExampleConstants.EVENTMESH_TCP_ASYNC_TEST_TOPIC)
                .uniqueId(RandomStringUtils.generateNum(30)).build();

            eventMeshHttpProducer.request(eventMeshMessage, new RRCallback<EventMeshMessage>() {

                @Override
                public void onSuccess(final EventMeshMessage o) {
                    log.debug("sendmsg: {}, return: {}, cost: {}ms",
                        eventMeshMessage.getContent(), o.getContent(), System.currentTimeMillis() - startTime);
                }

                @Override
                public void onException(Throwable e) {
                    log.debug("send msg failed", e);
                }
            }, 3_000);

            ThreadUtils.sleep(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("async send msg failed", e);
        }
        ThreadUtils.sleep(30, TimeUnit.SECONDS);

    }
}
