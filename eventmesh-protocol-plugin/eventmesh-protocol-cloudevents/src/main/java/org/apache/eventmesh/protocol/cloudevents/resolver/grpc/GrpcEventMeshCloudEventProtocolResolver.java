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

package org.apache.eventmesh.protocol.cloudevents.resolver.grpc;


import java.util.Objects;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;

import com.google.protobuf.InvalidProtocolBufferException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GrpcEventMeshCloudEventProtocolResolver {

    private static final EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(ProtobufFormat.PROTO_CONTENT_TYPE);

    public static io.cloudevents.CloudEvent buildEvent(CloudEvent cloudEvent) {
        return Objects.requireNonNull(eventFormat).deserialize(cloudEvent.toByteArray());
    }

    public static List<io.cloudevents.CloudEvent> buildBatchEvents(CloudEventBatch cloudEventBatch) {
        if (cloudEventBatch == null || cloudEventBatch.getEventsCount() < 1) {
            return new ArrayList<>(0);
        }
        return cloudEventBatch.getEventsList().stream().map(cloudEvent -> Objects.requireNonNull(eventFormat).deserialize(cloudEvent.toByteArray()))
            .collect(Collectors.toList());
    }

    public static EventMeshCloudEventWrapper buildEventMeshCloudEvent(io.cloudevents.CloudEvent cloudEvent) {
        if (null == cloudEvent) {
            return new EventMeshCloudEventWrapper(null);
        }
        try {
            return new EventMeshCloudEventWrapper(CloudEvent.parseFrom(Objects.requireNonNull(eventFormat).serialize(cloudEvent)));
        } catch (InvalidProtocolBufferException e) {
            log.error("Build Event Mesh CloudEvent from io.cloudevents.CloudEvent error", e);
        }
        return null;
    }
}
