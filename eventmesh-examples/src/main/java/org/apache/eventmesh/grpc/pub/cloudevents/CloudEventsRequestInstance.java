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

package org.apache.eventmesh.grpc.pub.cloudevents;

import org.apache.eventmesh.client.grpc.producer.EventMeshGrpcProducer;
import org.apache.eventmesh.client.tcp.common.EventMeshCommon;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.grpc.GrpcAbstractDemo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudEventsRequestInstance extends GrpcAbstractDemo {

    public static final int MESSAGE_SIZE = 5;

    public static void main(String[] args) throws Exception {

        try (
                EventMeshGrpcProducer eventMeshGrpcProducer = new EventMeshGrpcProducer(
                        initEventMeshGrpcClientConfig(ExampleConstants.DEFAULT_EVENTMESH_TEST_PRODUCER_GROUP))) {
            final Map<String, String> content = new HashMap<>();
            content.put("content", "testRequestReplyMessage");

            for (int i = 0; i < MESSAGE_SIZE; i++) {
                eventMeshGrpcProducer.requestReply(buildCloudEvent(content,
                        ExampleConstants.EVENTMESH_GRPC_RR_TEST_TOPIC), EventMeshCommon.DEFAULT_TIME_OUT_MILLS);
                ThreadUtils.sleep(1, TimeUnit.SECONDS);
            }

            ThreadUtils.sleep(30, TimeUnit.SECONDS);
        }
    }
}
