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
import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.api.exception.OnExceptionContext;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent.CloudEventAttributeValue;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.ProtocolKey;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.apache.eventmesh.runtime.core.protocol.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.producer.SendMessageContext;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReplyMessageProcessor {

    private static final Logger ACL_LOGGER = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final Acl acl;

    public ReplyMessageProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.acl = eventMeshGrpcServer.getAcl();
    }

    public void process(CloudEvent message, EventEmitter<CloudEvent> emitter) throws Exception {

        if (!ServiceUtils.validateCloudEventAttributes(message)) {
            ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateCloudEventData(message)) {
            ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        try {
            doAclCheck(message);
        } catch (Exception e) {
            ACL_LOGGER.warn("CLIENT HAS NO PERMISSION,RequestReplyMessageProcessor reply failed", e);
            ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
            return;
        }

        // control flow rate limit
        if (!eventMeshGrpcServer.getMsgRateLimiter()
            .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            log.error("Send message speed over limit.");
            ServiceUtils.sendStreamResponseCompleted(message, StatusCode.EVENTMESH_SEND_MESSAGE_SPEED_OVER_LIMIT_ERR, emitter);
            return;
        }

        String seqNum = EventMeshCloudEventUtils.getSeqNum(message);
        String uniqueId = EventMeshCloudEventUtils.getUniqueId(message);
        String producerGroup = EventMeshCloudEventUtils.getProducerGroup(message);

        // set reply topic for this message
        String mqCluster = EventMeshCloudEventUtils.getCluster(message, "defaultCluster");
        String replyTopic = mqCluster + "-" + EventMeshConstants.RR_REPLY_TOPIC;
        final CloudEvent messageReply = CloudEvent.newBuilder(message)
            .putAttributes(ProtocolKey.SUBJECT, CloudEventAttributeValue.newBuilder().setCeString(replyTopic).build()).build();

        String protocolType = EventMeshCloudEventUtils.getProtocolType(messageReply);
        ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        io.cloudevents.CloudEvent cloudEvent = grpcCommandProtocolAdaptor.toCloudEvent(new EventMeshCloudEventWrapper(messageReply));

        ProducerManager producerManager = eventMeshGrpcServer.getProducerManager();
        EventMeshProducer eventMeshProducer = producerManager.getEventMeshProducer(producerGroup);

        SendMessageContext sendMessageContext = new SendMessageContext(seqNum, cloudEvent, eventMeshProducer, eventMeshGrpcServer);

        eventMeshGrpcServer.getEventMeshGrpcMetricsManager().recordSendMsgToQueue();
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
                ServiceUtils.sendStreamResponseCompleted(messageReply, StatusCode.EVENTMESH_REPLY_MSG_ERR,
                    EventMeshUtil.stackTrace(onExceptionContext.getException(), 2), emitter);
                long endTime = System.currentTimeMillis();
                log.error("message|mq2eventmesh|REPLY|ReplyToServer|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, replyTopic, seqNum, uniqueId, onExceptionContext.getException());
            }
        });
    }

    private void doAclCheck(CloudEvent message) throws AclException {

        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().isEventMeshServerSecurityEnable()) {
            String remoteAdd = EventMeshCloudEventUtils.getIp(message);
            String user = EventMeshCloudEventUtils.getUserName(message);
            String pass = EventMeshCloudEventUtils.getPassword(message);
            String subsystem = EventMeshCloudEventUtils.getSys(message);
            String topic = EventMeshCloudEventUtils.getSubject(message);
            this.acl.doAclCheckInHttpSend(remoteAdd, user, pass, subsystem, topic, RequestCode.REPLY_MESSAGE.getRequestCode());
        }
    }
}
