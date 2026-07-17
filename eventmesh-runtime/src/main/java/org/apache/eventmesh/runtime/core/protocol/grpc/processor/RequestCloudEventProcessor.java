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

import org.apache.eventmesh.api.RequestReplyCallback;
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
public class RequestCloudEventProcessor extends AbstractPublishCloudEventProcessor {

    public RequestCloudEventProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
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
        int ttl = Integer.parseInt(EventMeshCloudEventUtils.getTtl(message));

        ProducerManager producerManager = eventMeshGrpcServer.getProducerManager();
        EventMeshProducer eventMeshProducer = producerManager.getEventMeshProducer(producerGroup);

        // Apply Ingress Pipeline to the REQUEST (Filter -> Transformer -> Router)
        String pipelineKey = producerGroup + "-" + topic;
        io.cloudevents.CloudEvent processedRequest = eventMeshGrpcServer.getEventMeshServer()
            .getIngressProcessor().process(cloudEvent, pipelineKey);

        if (processedRequest == null) {
            // Request filtered by pipeline - return error (request needs response)
            ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR,
                "Request message filtered by pipeline", emitter);
            log.info("message|grpc|request|filtered|topic={}|seqNum={}|uniqueId={}", topic, seqNum, uniqueId);
            return;
        }

        // Topic may have been changed by Router
        final String finalTopic = processedRequest.getSubject();
        SendMessageContext sendMessageContext = new SendMessageContext(seqNum, processedRequest, eventMeshProducer, eventMeshGrpcServer);

        eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordSendMsgToQueue();
        long startTime = System.currentTimeMillis();
        eventMeshProducer.request(sendMessageContext, new RequestReplyCallback() {

            @Override
            public void onSuccess(io.cloudevents.CloudEvent event) {
                try {
                    // Apply Egress Pipeline to the RESPONSE (Filter -> Transformer, no Router)
                    String responsePipelineKey = producerGroup + "-" + event.getSubject();
                    io.cloudevents.CloudEvent processedResponse = eventMeshGrpcServer.getEventMeshServer()
                        .getEgressProcessor().process(event, responsePipelineKey);

                    if (processedResponse == null) {
                        // Response filtered by pipeline - return error
                        ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR,
                            "Response message filtered by pipeline", emitter);
                        log.info("message|grpc|response|filtered|topic={}|seqNum={}|uniqueId={}",
                            event.getSubject(), seqNum, uniqueId);
                        return;
                    }

                    eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordReceiveMsgFromQueue();
                    EventMeshCloudEventWrapper wrapper = (EventMeshCloudEventWrapper) grpcCommandProtocolAdaptor.fromCloudEvent(processedResponse);

                    emitter.onNext(wrapper.getMessage());
                    emitter.onCompleted();

                    long endTime = System.currentTimeMillis();
                    log.info("message|eventmesh2client|REPLY|RequestReply|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, finalTopic, seqNum, uniqueId);
                    eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordSendMsgToClient(EventMeshCloudEventUtils.getIp(wrapper.getMessage()));
                } catch (Exception e) {
                    ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR, EventMeshUtil.stackTrace(e, 2),
                        emitter);
                    long endTime = System.currentTimeMillis();
                    log.error("message|mq2eventmesh|REPLY|RequestReply|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                        endTime - startTime, finalTopic, seqNum, uniqueId, e);
                }
            }

            @Override
            public void onException(Throwable e) {
                ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_REQUEST_REPLY_MSG_ERR, EventMeshUtil.stackTrace(e, 2),
                    emitter);
                long endTime = System.currentTimeMillis();
                log.error("message|eventMesh2mq|REPLY|RequestReply|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, finalTopic, seqNum, uniqueId, e);
            }
        }, ttl);
    }
}
