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
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.Builder;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.ConsumerServiceGrpc;
import org.apache.eventmesh.connector.openfunction.client.CallbackServiceGrpc;
import org.apache.eventmesh.connector.openfunction.client.CallbackServiceGrpc.CallbackServiceBlockingStub;
import org.apache.eventmesh.connector.openfunction.config.OpenFunctionServerConfig;
import org.apache.eventmesh.connector.openfunction.sink.connector.OpenFunctionSinkConnector;
import org.apache.eventmesh.openconnect.api.data.ConnectRecord;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.cloudevents.SpecVersion;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConsumerService extends ConsumerServiceGrpc.ConsumerServiceImplBase {

    public OpenFunctionSinkConnector openFunctionSinkConnector;

    public BlockingQueue<ConnectRecord> queue;

    public OpenFunctionServerConfig config;

    private final transient ManagedChannel channel = ManagedChannelBuilder.forAddress(config.getTargetAddress(), config.getTargetPort()).usePlaintext().build();

    private CallbackServiceBlockingStub publisherClient = CallbackServiceGrpc.newBlockingStub(channel);

    private final ExecutorService handleService = Executors.newSingleThreadExecutor();


    public ConsumerService(OpenFunctionSinkConnector openFunctionSinkConnector, OpenFunctionServerConfig serverConfig) {
        this.openFunctionSinkConnector = openFunctionSinkConnector;
        this.queue = openFunctionSinkConnector.queue();
        this.config = serverConfig;
        handleService.execute(this::startHandleConsumeEvents);
    }

    private void startHandleConsumeEvents() {
        while (openFunctionSinkConnector.isRunning()) {
            ConnectRecord connectRecord = queue.poll();
            if (connectRecord != null) {
                CloudEvent response = publisherClient.onTopicEvent(convertRecordToEvent(connectRecord));
            }
        }

    }

    private CloudEvent convertRecordToEvent(ConnectRecord connectRecord) {
        Builder cloudEventBuilder = CloudEvent.newBuilder();
        cloudEventBuilder.setId(connectRecord.getExtension("id"));
        cloudEventBuilder.setSource(connectRecord.getExtension("source"));
        cloudEventBuilder.setSpecVersion(SpecVersion.V1.toString());
        cloudEventBuilder.setType(connectRecord.getExtension("type"));
        cloudEventBuilder.setTextData(new String((byte[]) connectRecord.getData()));
        for (String extensionKey : connectRecord.getExtensions().keySet()) {
            if (!StringUtils.equalsAny(extensionKey, "id", "source", "type")) {
                cloudEventBuilder.putAttributes(extensionKey,
                    CloudEventAttributeValue.newBuilder().setCeString(connectRecord.getExtension(extensionKey)).build());
            }
        }
        return cloudEventBuilder.build();
    }


}
