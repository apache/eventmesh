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
import org.apache.eventmesh.common.protocol.grpc.common.SimpleMessageWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.ProtocolPluginFactory;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.EventMeshProducer;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.ProducerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.producer.SendMessageContext;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.apache.eventmesh.runtime.util.EventMeshUtil;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudevents.CloudEvent;

public class SendAsyncMessageProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final Logger aclLogger = LoggerFactory.getLogger("acl");

    private final EventMeshGrpcServer eventMeshGrpcServer;

    public SendAsyncMessageProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(SimpleMessage message, EventEmitter<Response> emitter) throws Exception {
        RequestHeader requestHeader = message.getHeader();

        if (!ServiceUtils.validateHeader(requestHeader)) {
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateMessage(message)) {
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        String seqNum = message.getSeqNum();
        String uniqueId = message.getUniqueId();
        String topic = message.getTopic();
        String producerGroup = message.getProducerGroup();

        try {
            doAclCheck(message);
        } catch (Exception e) {
            aclLogger.warn("CLIENT HAS NO PERMISSION,SendAsyncMessageProcessor send failed", e);
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
            return;
        }

        // control flow rate limit
        if (!eventMeshGrpcServer.getMsgRateLimiter()
            .tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            logger.error("Send message speed over limit.");
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_BATCH_SPEED_OVER_LIMIT_ERR, emitter);
            return;
        }

        String protocolType = requestHeader.getProtocolType();
        ProtocolAdaptor<ProtocolTransportObject> grpcCommandProtocolAdaptor = ProtocolPluginFactory.getProtocolAdaptor(protocolType);
        CloudEvent cloudEvent = grpcCommandProtocolAdaptor.toCloudEvent(new SimpleMessageWrapper(message));

        ProducerManager producerManager = eventMeshGrpcServer.getProducerManager();
        EventMeshProducer eventMeshProducer = producerManager.getEventMeshProducer(producerGroup);

        SendMessageContext sendMessageContext = new SendMessageContext(message.getSeqNum(), cloudEvent, eventMeshProducer, eventMeshGrpcServer);

        long startTime = System.currentTimeMillis();
        eventMeshProducer.send(sendMessageContext, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                ServiceUtils.sendRespAndDone(StatusCode.SUCCESS, sendResult.toString(), emitter);
                long endTime = System.currentTimeMillis();
                logger.info("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, topic, seqNum, uniqueId);
            }

            @Override
            public void onException(OnExceptionContext context) {
                ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_SEND_ASYNC_MSG_ERR,
                    EventMeshUtil.stackTrace(context.getException(), 2), emitter);
                long endTime = System.currentTimeMillis();
                logger.error("message|eventMesh2mq|REQ|ASYNC|send2MQCost={}ms|topic={}|bizSeqNo={}|uniqueId={}",
                    endTime - startTime, topic, seqNum, uniqueId, context.getException());
            }
        });
    }

    private void doAclCheck(SimpleMessage message) throws AclException {
        RequestHeader requestHeader = message.getHeader();
        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshServerSecurityEnable) {
            String remoteAdd = requestHeader.getIp();
            String user = requestHeader.getUsername();
            String pass = requestHeader.getPassword();
            String subsystem = requestHeader.getSys();
            String topic = message.getTopic();
            Acl.doAclCheckInHttpSend(remoteAdd, user, pass, subsystem, topic, RequestCode.MSG_SEND_ASYNC.getRequestCode());
        }
    }
}
