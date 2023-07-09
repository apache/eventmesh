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

import org.apache.eventmesh.api.SendCallback;
import org.apache.eventmesh.api.SendResult;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.BatchEventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BatchPublishCloudEventProcessor extends AbstractPublishBatchCloudEventProcessor {


    public BatchPublishCloudEventProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
        super(eventMeshGrpcServer, eventMeshGrpcServer.getAcl());
    }

    public void handleCloudEvent(CloudEventBatch cloudEventBatch, EventEmitter<CloudEvent> emitter) throws Exception {

        CloudEvent cloudEvent = cloudEventBatch.getEvents(0);
        String topic = EventMeshCloudEventUtils.getSubject(cloudEvent);
        String producerGroup = EventMeshCloudEventUtils.getProducerGroup(cloudEvent);

        String protocolType = EventMeshCloudEventUtils.getProtocolType(cloudEvent);
        ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        List<io.cloudevents.CloudEvent> cloudEvents = grpcCommandProtocolAdaptor.toBatchCloudEvent(
            new BatchEventMeshCloudEventWrapper(cloudEventBatch));

        for (io.cloudevents.CloudEvent event : cloudEvents) {
            String seqNum = event.getId();
            String uniqueId = (event.getExtension(ProtocolKey.UNIQUE_ID) == null) ? "" : event.getExtension(ProtocolKey.UNIQUE_ID).toString();
            ProducerManager producerManager = eventMeshGrpcServer.getProducerManager();
            EventMeshProducer eventMeshProducer = producerManager.getEventMeshProducer(producerGroup);

            SendMessageContext sendMessageContext = new SendMessageContext(seqNum, event, eventMeshProducer, eventMeshGrpcServer);

            eventMeshGrpcServer.getMetricsManager().recordSendMsgToQueue();
            long startTime = System.currentTimeMillis();
            eventMeshProducer.send(sendMessageContext, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    long endTime = System.currentTimeMillis();
                    log.info("message|eventMesh2mq|REQ|BatchSend|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, topic, seqNum, uniqueId);
                }

                @Override
                public void onException(OnExceptionContext context) {
                    long endTime = System.currentTimeMillis();
                    log.error("message|eventMesh2mq|REQ|BatchSend|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, topic, seqNum, uniqueId, context.getException());
                }
            });
        }
        ServiceUtils.sendResponseCompleted(StatusCode.SUCCESS, "batch publish success", emitter);
    }


}
