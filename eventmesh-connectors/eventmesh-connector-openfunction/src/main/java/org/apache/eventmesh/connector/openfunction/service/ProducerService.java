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

package org.apache.eventmesh.connector.openfunction.service;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.connector.openfunction.config.OpenFunctionServerConfig;
import org.apache.eventmesh.connector.openfunction.source.connector.OpenFunctionSourceConnector;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;

import io.grpc.stub.StreamObserver;

import com.google.protobuf.Timestamp;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProducerService extends PublisherServiceGrpc.PublisherServiceImplBase {

    private final OpenFunctionSourceConnector openFunctionSourceConnector;

    private final BlockingQueue<ConnectRecord> queue;

    private final OpenFunctionServerConfig config;

    public ProducerService(OpenFunctionSourceConnector openFunctionSourceConnector, OpenFunctionServerConfig serverConfig) {
        this.openFunctionSourceConnector = openFunctionSourceConnector;
        this.queue = openFunctionSourceConnector.queue();
        this.config = serverConfig;
    }

    /**
     * publish event to eventmesh
     *
     * @param event
     * @param responseObserver
     */
    @Override
    public void publish(CloudEvent event, StreamObserver<CloudEvent> responseObserver) {
        log.info("receive cloudevents {}", event);
        Instant instant = now();
        CloudEvent.Builder builder = CloudEvent.newBuilder();
        ConnectRecord connectRecord = convertCloudEventToConnectorRecord(event);
        try {
            // put record to source connector
            queue.put(connectRecord);
            builder.putAttributes(ProtocolKey.GRPC_RESPONSE_CODE,
                    CloudEventAttributeValue.newBuilder().setCeString(StatusCode.SUCCESS.getRetCode()).build())
                    .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                            CloudEventAttributeValue.newBuilder().setCeString(StatusCode.SUCCESS.getErrMsg()).build())
                    .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
                            .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());
        } catch (InterruptedException e) {
            log.error("publish event error {}", e.getMessage());
            builder.putAttributes(ProtocolKey.GRPC_RESPONSE_CODE,
                    CloudEventAttributeValue.newBuilder().setCeString(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode()).build())
                    .putAttributes(ProtocolKey.GRPC_RESPONSE_MESSAGE,
                            CloudEventAttributeValue.newBuilder().setCeString(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg()).build())
                    .putAttributes(ProtocolKey.GRPC_RESPONSE_TIME, CloudEventAttributeValue.newBuilder()
                            .setCeTimestamp(Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build()).build());
            Thread.currentThread().interrupt();
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();

    }

    private ConnectRecord convertCloudEventToConnectorRecord(CloudEvent event) {
        ConnectRecord connectRecord = new ConnectRecord(null, null, System.currentTimeMillis(), event.getTextData());
        for (String extensionName : event.getAttributesMap().keySet()) {
            connectRecord.addExtension(extensionName, Objects.requireNonNull(event.getAttributesOrThrow(extensionName)).getCeString());
        }
        connectRecord.addExtension("id", event.getId());
        connectRecord.addExtension("source", event.getSource());
        connectRecord.addExtension("type", event.getType());
        return connectRecord;
    }

    /**
     * publish eventBatch to eventmesh
     *
     * @param eventBatch
     * @param responseObserver
     */
    @Override
    public void batchPublish(CloudEventBatch eventBatch, StreamObserver<CloudEvent> responseObserver) {

    }

    private static Instant now() {
        return OffsetDateTime.of(LocalDateTime.now(ZoneId.systemDefault()), ZoneOffset.UTC).toInstant();
    }

}
