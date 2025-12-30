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
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublishCloudEventsProcessor extends AbstractPublishCloudEventProcessor {

    public PublishCloudEventsProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
        super(eventMeshGrpcServer, eventMeshGrpcServer.getAcl());
    }

    @Override
    public void handleCloudEvent(CloudEvent message, EventEmitter<CloudEvent> emitter) throws Exception {

        String protocolType = EventMeshCloudEventUtils.getProtocolType(message);
        ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        io.cloudevents.CloudEvent cloudEvent = grpcCommandProtocolAdaptor.toCloudEvent(new EventMeshCloudEventWrapper(message));

        String seqNum = EventMeshCloudEventUtils.getSeqNum(message);
        String uniqueId = EventMeshCloudEventUtils.getUniqueId(message);
        String topic = EventMeshCloudEventUtils.getSubject(message);
        String producerGroup = EventMeshCloudEventUtils.getProducerGroup(message);

        ProducerManager producerManager = eventMeshGrpcServer.getProducerManager();
        EventMeshProducer eventMeshProducer = producerManager.getEventMeshProducer(producerGroup);

        // Apply Ingress Pipeline (Filter -> Transformer -> Router)
        String pipelineKey = producerGroup + "-" + topic;
        io.cloudevents.CloudEvent processedEvent = eventMeshGrpcServer.getEventMeshServer()
            .getIngressProcessor().process(cloudEvent, pipelineKey);

        if (processedEvent == null) {
            // Message filtered by pipeline - return success
            ServiceUtils.sendResponseCompleted(StatusCode.SUCCESS, "Message filtered by pipeline", emitter);
            log.info("message|grpc|publish|filtered|topic={}|seqNum={}|uniqueId={}", topic, seqNum, uniqueId);
            return;
        }

        // Topic may have been changed by Router
        final String finalTopic = processedEvent.getSubject();
        SendMessageContext sendMessageContext = new SendMessageContext(seqNum, processedEvent, eventMeshProducer, eventMeshGrpcServer);

        eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordSendMsgToQueue();
        long startTime = System.currentTimeMillis();
        eventMeshProducer.send(sendMessageContext, new SendCallback() {

            @Override
            public void onSuccess(SendResult sendResult) {
                ServiceUtils.sendResponseCompleted(StatusCode.SUCCESS, sendResult.toString(), emitter);
                long endTime = System.currentTimeMillis();
                log.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, finalTopic, seqNum, uniqueId);
                eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordSendMsgToClient(EventMeshCloudEventUtils.getIp(message));
            }

            @Override
            public void onException(OnExceptionContext context) {
                ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR,
                    EventMeshUtil.stackTrace(context.getException(), 2), emitter);
                long endTime = System.currentTimeMillis();
                log.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, finalTopic, seqNum, uniqueId, context.getException());
            }
        });
    }

}
