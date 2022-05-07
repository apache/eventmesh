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
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.BatchPublishMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.RequestMessageProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.SendAsyncMessageProcessor;

import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.stub.StreamObserver;

public class ProducerService extends PublisherServiceGrpc.PublisherServiceImplBase {

    private final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final Logger cmdLogger = LoggerFactory.getLogger("cmd");

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ThreadPoolExecutor threadPoolExecutor;

    public ProducerService(EventMeshGrpcServer eventMeshGrpcServer,
                           ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    public void publish(SimpleMessage request, StreamObserver<Response> responseObserver) {
        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", "AsyncPublish",
            EventMeshConstants.PROTOCOL_GRPC, request.getHeader().getIp(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            SendAsyncMessageProcessor sendAsyncMessageProcessor = new SendAsyncMessageProcessor(eventMeshGrpcServer);
            try {
                sendAsyncMessageProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode(),
                    StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg(), e);
                ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR, e.getMessage(), emitter);
            }
        });
    }

    public void requestReply(SimpleMessage request, StreamObserver<SimpleMessage> responseObserver) {
        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", "RequestReply",
            EventMeshConstants.PROTOCOL_GRPC, request.getHeader().getIp(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<SimpleMessage> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            RequestMessageProcessor requestMessageProcessor = new RequestMessageProcessor(eventMeshGrpcServer);
            try {
                requestMessageProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR.getRetCode(),
                    StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR.getErrMsg(), e);
                ServiceUtils.sendStreamRespAndDone(request.getHeader(), StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR, e.getMessage(), emitter);
            }
        });
    }

    public void batchPublish(BatchMessage request, StreamObserver<Response> responseObserver) {
        cmdLogger.info("cmd={}|{}|client2eventMesh|from={}|to={}", "BatchPublish",
            EventMeshConstants.PROTOCOL_GRPC, request.getHeader().getIp(),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshIp);

        EventEmitter<Response> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            BatchPublishMessageProcessor batchPublishMessageProcessor = new BatchPublishMessageProcessor(eventMeshGrpcServer);
            try {
                batchPublishMessageProcessor.process(request, emitter);
            } catch (Exception e) {
                logger.error("Error code {}, error message {}", StatusCode.EVENTMESH_BATCH_PUBLISH_ERR.getRetCode(),
                    StatusCode.EVENTMESH_BATCH_PUBLISH_ERR.getErrMsg(), e);
                ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_BATCH_PUBLISH_ERR, e.getMessage(), emitter);
            }
        });
    }

}
