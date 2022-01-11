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

package org.apache.eventmesh.runtime.core.protocol.grpc.processor;

import io.cloudevents.CloudEvent;
import io.grpc.stub.StreamObserver;
import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.EventMeshMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.apache.eventmesh.runtime.util.EventMeshUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendAsyncMessageProcessor {

    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private EventMeshGrpcServer eventMeshGrpcServer;

    public SendAsyncMessageProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(EventMeshMessage message, StreamObserver<Response> responseObserver) throws Exception {
        RequestHeader requestHeader = message.getHeader();

        if (!ServiceUtils.validateHeader(requestHeader)) {
            ServiceUtils.sendResp(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, responseObserver);
            return;
        }

        if (!ServiceUtils.validateMessage(message)) {
            ServiceUtils.sendResp(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, responseObserver);
            return;
        }

        String protocolType = requestHeader.getProtocolType();
        ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent cloudEvent = grpcCommandProtocolAdaptor.toCloudEvent(new EventMeshMessageWrapper(message));

        String seqNum = message.getSeqNum();
        String uniqueId = message.getUniqueId();
        String topic = message.getTopic();
        String producerGroup = message.getProducerGroup();

        ProducerManager producerManager = eventMeshGrpcServer.getProducerManager();
        EventMeshProducer eventMeshProducer = producerManager.getEventMeshProducer(producerGroup);

        SendMessageContext sendMessageContext = new SendMessageContext(message.getSeqNum(), cloudEvent, eventMeshProducer, eventMeshGrpcServer);

        long startTime = System.currentTimeMillis();
        eventMeshProducer.send(sendMessageContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                ServiceUtils.sendResp(StatusCode.SUCCESS, sendResult.toString(), responseObserver);
                long endTime = System.currentTimeMillis();
                logger.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, topic, seqNum, uniqueId);
            }

            @Override
            public void onException(OnExceptionContext context) {
                ServiceUtils.sendResp(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR,
                    EventMeshUtil.stackTrace(context.getException(), 2), responseObserver);
                long endTime = System.currentTimeMillis();
                logger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, topic, seqNum, uniqueId, context.getException());
            }
        });

    }
}
