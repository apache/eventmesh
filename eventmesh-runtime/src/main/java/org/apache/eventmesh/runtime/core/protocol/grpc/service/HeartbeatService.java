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

package org.apache.eventmesh.runtime.core.protocol.grpc.service;

import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.HeartbeatProcessor;

import java.util.concurrent.ThreadPoolExecutor;

import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HeartbeatService extends HeartbeatServiceGrpc.HeartbeatServiceImplBase {

    private final transient EventMeshGrpcServer eventMeshGrpcServer;

    private final transient ThreadPoolExecutor threadPoolExecutor;

    public HeartbeatService(final EventMeshGrpcServer eventMeshGrpcServer,
        final ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    @Override
    public void heartbeat(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
        log.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "heartbeat", EventMeshConstants.PROTOCOL_GRPC, EventMeshCloudEventUtils.getIp(request),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());

        EventEmitter<CloudEvent> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            HeartbeatProcessor heartbeatProcessor = new HeartbeatProcessor(eventMeshGrpcServer);
            try {
                heartbeatProcessor.process(request, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_HEARTBEAT_ERR.getRetCode(),
                    StatusCode.EVENTMESH_HEARTBEAT_ERR.getErrMsg(), e);
                ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_HEARTBEAT_ERR, e.getMessage(), emitter);
            }
        });
    }
}
