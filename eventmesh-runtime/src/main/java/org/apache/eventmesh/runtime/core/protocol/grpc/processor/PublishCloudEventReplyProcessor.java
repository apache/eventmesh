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
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PublishCloudEventReplyProcessor extends AbstractPublishCloudEventProcessor {

    public PublishCloudEventReplyProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
        super(eventMeshGrpcServer, eventMeshGrpcServer.getAcl());
    }

    @Override
    public void handleCloudEvent(CloudEvent message, EventEmitter<CloudEvent> emitter) throws Exception {

        String seqNum = EventMeshCloudEventUtils.getSeqNum(message);
        String uniqueId = EventMeshCloudEventUtils.getUniqueId(message);
        String producerGroup = EventMeshCloudEventUtils.getProducerGroup(message);

        // set reply topic for ths message
        String mqCluster = EventMeshCloudEventUtils.getCluster(message, "defaultCluster");
        String replyTopic = mqCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC;
        final CloudEvent rebulidCloudEvent = CloudEvent.newBuilder(message)
            .putAttributes(ProtocolKey.SUBJECT, CloudEventAttributeValue.newBuilder().setCeString(replyTopic).build()).build();

        String protocolType = EventMeshCloudEventUtils.getProtocolType(rebulidCloudEvent);
        ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        io.cloudevents.CloudEvent cloudEvent = grpcCommandProtocolAdaptor.toCloudEvent(new EventMeshCloudEventWrapper(rebulidCloudEvent));

        ProducerManager producerManager = eventMeshGrpcServer.getProducerManager();
        EventMeshProducer eventMeshProducer = producerManager.getEventMeshProducer(producerGroup);

        SendMessageContext sendMessageContext = new SendMessageContext(seqNum, cloudEvent, eventMeshProducer, eventMeshGrpcServer);

        eventMeshGrpcServer.getMetricsMonitor().recordSendMsgToQueue();
        long startTime = System.currentTimeMillis();
        eventMeshProducer.reply(sendMessageContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                long endTime = System.currentTimeMillis();
                log.info("message|mq2eventmesh|REPLY|ReplyToServer|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, replyTopic, seqNum, uniqueId);
            }

            @Override
            public void onException(OnExceptionContext onExceptionContext) {
                ServiceUtils.streamCompleted(rebulidCloudEvent, StatusCode.EVENTMESH_REPLY_MSG_ERR,
                    EventMeshUtil.stackTrace(onExceptionContext.getException(), 2), emitter);
                long endTime = System.currentTimeMillis();
                log.error("message|mq2eventmesh|REPLY|ReplyToServer|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, replyTopic, seqNum, uniqueId, onExceptionContext.getException());
            }
        });
    }

}
