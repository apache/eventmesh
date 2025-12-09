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

package org.apache.eventmesh.a2a.demo.ce;

import org.apache.eventmesh.a2a.demo.A2AAbstractDemo;
import org.apache.eventmesh.client.http.conf.EventMeshHttpClientConfig;
import org.apache.eventmesh.client.http.consumer.EventMeshHttpConsumer;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventsProvider extends A2AAbstractDemo {

    public static void main(String[] args) throws Exception {
        EventMeshHttpClientConfig config = initEventMeshHttpClientConfig("CloudEventsProviderGroup");
        try (EventMeshHttpConsumer consumer = new EventMeshHttpConsumer(config)) {
            
            List<SubscriptionItem> topicList = new ArrayList<>();
            
            // 1. Subscribe to RPC Topic
            SubscriptionItem rpcItem = new SubscriptionItem();
            rpcItem.setTopic("rpc-topic");
            rpcItem.setMode(SubscriptionMode.CLUSTERING);
            rpcItem.setType(SubscriptionType.ASYNC);
            topicList.add(rpcItem);

            // 2. Subscribe to Broadcast Topic
            SubscriptionItem broadcastItem = new SubscriptionItem();
            broadcastItem.setTopic("broadcast.topic");
            broadcastItem.setMode(SubscriptionMode.BROADCASTING); // Broadcast mode
            broadcastItem.setType(SubscriptionType.ASYNC);
            topicList.add(broadcastItem);
            
            // 3. Subscribe to Stream Topic
            SubscriptionItem streamItem = new SubscriptionItem();
            streamItem.setTopic("stream-topic");
            streamItem.setMode(SubscriptionMode.CLUSTERING);
            streamItem.setType(SubscriptionType.ASYNC);
            topicList.add(streamItem);

            consumer.heartBeat(topicList, "http://127.0.0.1:8088/ce/callback");
            
            log.info("CloudEvents Provider started. Listening for A2A messages...");
            
            while (true) {
                Thread.sleep(10000);
            }
        }
    }
    
    // Simulation of WebController logic
    public static void handleCallback(CloudEvent event) {
        try {
            String protocol = (String) event.getExtension("protocol");
            if (!"A2A".equals(protocol)) {
                return;
            }
            
            String subject = event.getSubject();
            String data = new String(event.getData().toBytes(), StandardCharsets.UTF_8);
            
            log.info("Received Native CloudEvent: Subject={}, Type={}, Data={}", subject, event.getType(), data);
            
            if ("stream-topic".equals(subject)) {
                String seq = (String) event.getExtension("seq");
                String sessionId = (String) event.getExtension("sessionid");
                log.info("Stream processing: Session={}, Seq={}", sessionId, seq);
            }
            
        } catch (Exception e) {
            log.error("Error handling callback", e);
        }
    }
}
