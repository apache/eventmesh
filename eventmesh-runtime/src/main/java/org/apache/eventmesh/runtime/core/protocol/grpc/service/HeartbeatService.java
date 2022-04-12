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

import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.HeartbeatServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.HeartbeatProcessor;

import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

public class HeartbeatService extends HeartbeatServiceGrpc.HeartbeatServiceImplBase {

    private final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ThreadPoolExecutor threadPoolExecutor;

    public HeartbeatService(EventMeshGrpcServer eventMeshGrpcServer,
                            ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    public void heartbeat(Heartbeat request, StreamObserver<Response> responseObserver) {
        logger.info("cmd={}|{}|client2eventMesh|from={}|to={}",
            "heartbeat", EventMeshConstants.PROTOCOL_GRPC,
            request.getHeader().getIp(), eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            HeartbeatProcessor heartbeatProcessor = new HeartbeatProcessor(eventMeshGrpcServer);
            try {
                heartbeatProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_HEARTBEAT_ERR.getRetCode(),
                    StatusCode.EVENTMESH_HEARTBEAT_ERR.getErrMsg(), e);
                ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_HEARTBEAT_ERR, e.getMessage(), emitter);
            }
        });
    }
}
