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
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.PublisherServiceGrpc;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.BatchPublishCloudEventProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.PublishCloudEventsProcessor;
import org.apache.eventmesh.runtime.core.protocol.grpc.processor.RequestCloudEventProcessor;

import java.util.concurrent.ThreadPoolExecutor;

import io.grpc.stub.StreamObserver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublisherService extends PublisherServiceGrpc.PublisherServiceImplBase {

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final ThreadPoolExecutor threadPoolExecutor;

    public PublisherService(EventMeshGrpcServer eventMeshGrpcServer,
        ThreadPoolExecutor threadPoolExecutor) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.threadPoolExecutor = threadPoolExecutor;
    }

    /**
     * <pre>
     * Sync publish event
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void publish(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
        String clientId = EventMeshCloudEventUtils.getIp(request);
        log.info("cmd={}|{}|client2eventMesh|from={}|to={}", "publish", EventMeshConstants.PROTOCOL_GRPC,
            EventMeshCloudEventUtils.getIp(request), eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());
        eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordReceiveMsgFromClient(clientId);

        EventEmitter<CloudEvent> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            PublishCloudEventsProcessor publishCloudEventsProcessor = new PublishCloudEventsProcessor(eventMeshGrpcServer);
            try {
                publishCloudEventsProcessor.process(request, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getRetCode(),
                    StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR.getErrMsg(), e);
                ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR, e.getMessage(), emitter);
            }
        });
    }

    /**
     * <pre>
     * publish event with reply
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void requestReply(CloudEvent request, StreamObserver<CloudEvent> responseObserver) {
        String clientIp = EventMeshCloudEventUtils.getIp(request);
        log.info("cmd={}|{}|client2eventMesh|from={}|to={}", "RequestReply",
            EventMeshConstants.PROTOCOL_GRPC, EventMeshCloudEventUtils.getIp(request),
            eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());
        eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordReceiveMsgFromClient(clientIp);

        EventEmitter<CloudEvent> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            RequestCloudEventProcessor requestMessageProcessor = new RequestCloudEventProcessor(eventMeshGrpcServer);
            try {
                requestMessageProcessor.process(request, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR.getRetCode(),
                    StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR.getErrMsg(), e);
                ServiceUtils.sendStreamResponseCompleted(request, StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR, e.getMessage(), emitter);
            }
        });
    }

    /**
     * <pre>
     * publish batch event
     * </pre>
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void batchPublish(CloudEventBatch request, StreamObserver<CloudEvent> responseObserver) {
        String clientIp = EventMeshCloudEventUtils.getIp(request.getEvents(0));
        log.info("cmd={}|{}|client2eventMesh|from={}|to={}", "BatchPublish",
            EventMeshConstants.PROTOCOL_GRPC, null,
            eventMeshGrpcServer.getEventMeshGrpcConfiguration().getEventMeshIp());
        eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordReceiveMsgFromClient(request.getEventsCount(), clientIp);

        EventEmitter<CloudEvent> emitter = new EventEmitter<>(responseObserver);
        threadPoolExecutor.submit(() -> {
            BatchPublishCloudEventProcessor batchPublishMessageProcessor = new BatchPublishCloudEventProcessor(eventMeshGrpcServer);
            try {
                batchPublishMessageProcessor.process(request, emitter);
            } catch (Exception e) {
                log.error("Error code {}, error message {}", StatusCode.EVENTMESH_BATCH_PUBLISH_ERR.getRetCode(),
                    StatusCode.EVENTMESH_BATCH_PUBLISH_ERR.getErrMsg(), e);
                ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_BATCH_PUBLISH_ERR, e.getMessage(), emitter);
            }
        });
    }
}
