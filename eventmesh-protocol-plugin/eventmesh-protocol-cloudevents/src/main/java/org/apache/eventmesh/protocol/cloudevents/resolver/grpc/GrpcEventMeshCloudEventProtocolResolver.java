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


import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;

public class GrpcEventMeshCloudEventProtocolResolver {

    private static final EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(ProtobufFormat.PROTO_CONTENT_TYPE);

    public static io.cloudevents.CloudEvent buildEvent(CloudEvent cloudEvent) {
        io.cloudevents.CloudEvent event = eventFormat.deserialize(cloudEvent.toByteArray());
        return event;
    }

    public static List<io.cloudevents.CloudEvent> buildBatchEvents(CloudEventBatch cloudEventBatch) {
        if (cloudEventBatch == null || cloudEventBatch.getEventsCount() < 1) {
            return new ArrayList<>(0);
        }
        return cloudEventBatch.getEventsList().stream().map(cloudEvent -> eventFormat.deserialize(cloudEvent.toByteArray()))
            .collect(Collectors.toList());
    }

}
