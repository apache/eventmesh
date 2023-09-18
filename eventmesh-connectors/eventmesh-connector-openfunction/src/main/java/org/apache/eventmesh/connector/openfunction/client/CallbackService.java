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

package org.apache.eventmesh.connector.openfunction.client;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import io.grpc.stub.StreamObserver;

import com.google.protobuf.Timestamp;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CallbackService extends CallbackServiceGrpc.CallbackServiceImplBase {

    @Override
    public void onTopicEvent(CloudEvent cloudEvent, StreamObserver<CloudEvent> responseObserver) {
        log.info("cloudevents: {}|data: {}", cloudEvent, cloudEvent.getTextData());

        Instant instant = now();
        CloudEvent.Builder builder = CloudEvent.newBuilder();
        builder.putAttributes(ProtocolKey.GRPC_RESPONSE_CODE,
                CloudEventAttributeValue.newBuilder().setCeString(StatusCode.SUCCESS.getRetCode()).build())
                .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                        CloudEventAttributeValue.newBuilder().setCeString(StatusCode.SUCCESS.getErrMsg()).build())
                .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
                        .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private static Instant now() {
        return OffsetDateTime.of(LocalDateTime.now(ZoneId.systemDefault()), ZoneOffset.UTC).toInstant();
    }
}
