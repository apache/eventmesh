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

package org.apache.eventmesh.grpc.sub;

import org.apache.eventmesh.client.grpc.consumer.EventMeshGrpcConsumer;
import org.apache.eventmesh.client.grpc.consumer.ReceiveMsgHook;
import org.apache.eventmesh.common.EventMeshMessage;
import org.apache.eventmesh.common.ExampleConstants;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.SubscriptionMode;
import org.apache.eventmesh.common.protocol.SubscriptionType;
import org.apache.eventmesh.common.utils.LogUtils;
import org.apache.eventmesh.common.utils.ThreadUtils;
import org.apache.eventmesh.grpc.GrpcAbstractDemo;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventMeshSubscribeBroadcast extends GrpcAbstractDemo implements ReceiveMsgHook<EventMeshMessage> {

    public static void main(String[] args) throws InterruptedException, IOException {

        SubscriptionItem subscriptionItem = new SubscriptionItem();
        subscriptionItem.setTopic(ExampleConstants.EVENTMESH_GRPC_BROADCAT_TEST_TOPIC);
        subscriptionItem.setMode(SubscriptionMode.BROADCASTING);
        subscriptionItem.setType(SubscriptionType.ASYNC);

        try (EventMeshGrpcConsumer eventMeshGrpcConsumer = new EventMeshGrpcConsumer(
            initEventMeshGrpcClientConfig(ExampleConstants.DEFAULT_EVENTMESH_TEST_CONSUMER_GROUP))) {

            eventMeshGrpcConsumer.init();

            eventMeshGrpcConsumer.registerListener(new EventMeshSubscribeBroadcast());

            eventMeshGrpcConsumer.subscribe(Collections.singletonList(subscriptionItem));

            ThreadUtils.sleep(1, TimeUnit.MINUTES);
            eventMeshGrpcConsumer.unsubscribe(Collections.singletonList(subscriptionItem));
        }
    }

    @Override
    public Optional<EventMeshMessage> handle(final EventMeshMessage msg) {
        LogUtils.info(log, "receive async broadcast msg: {}", msg);
        return Optional.empty();
    }

    @Override
    public EventMeshProtocolType getProtocolType() {
        return EventMeshProtocolType.EVENT_MESH_MESSAGE;
    }
}
