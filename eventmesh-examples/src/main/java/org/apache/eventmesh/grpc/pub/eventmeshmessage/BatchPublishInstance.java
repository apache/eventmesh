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

package org.apache.eventmesh.grpc.pub.eventmeshmessage;

import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.grpc.GrpcAbstractDemo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BatchPublishInstance extends GrpcAbstractDemo {

    public static void main(String[] args) throws Exception {

        try (
                EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(
                        initEventMeshGrpcClientConfig(ExampleConstants.DEFAULT_EVENTMESH_TEST_PRODUCER_GROUP))) {

            Map<String, String> content = new HashMap<>();
            content.put("content", "testRequestReplyMessage");

            List<EventMeshMessage> messageList = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                messageList.add(buildEventMeshMessage(content,
                        ExampleConstants.EVENTMESH_GRPC_BROADCAT_TEST_TOPIC));
            }

            eventMeshGrpcProducer.publish(messageList);
            ThreadUtils.sleep(10, TimeUnit.SECONDS);
        }
    }
}
