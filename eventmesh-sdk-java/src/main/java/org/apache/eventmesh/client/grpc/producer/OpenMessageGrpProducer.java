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

package org.apache.eventmesh.client.grpc.producer;

import org.apache.eventmesh.client.grpc.config.EventMeshGrpcClientConfig;
import org.apache.eventmesh.client.grpc.util.EventMeshClientUtil;
import org.apache.eventmesh.common.enums.EventMeshProtocolType;
import org.apache.eventmesh.common.protocol.grpc.protos.BatchMessage;
import org.apache.eventmesh.common.protocol.grpc.protos.PublisherServiceGrpc.PublisherServiceBlockingStub;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.SimpleMessage;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;


import io.openmessaging.api.Message;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenMessageGrpProducer implements GrpcProducer<Message> {

    private static final EventMeshProtocolType PROTOCOL_TYPE = EventMeshProtocolType.OPEN_MESSAGE;

    private final EventMeshGrpcClientConfig clientConfig;

    private final PublisherServiceBlockingStub publisherClient;

    public OpenMessageGrpProducer(EventMeshGrpcClientConfig clientConfig, PublisherServiceBlockingStub publisherClient) {
        this.clientConfig = clientConfig;
        this.publisherClient = publisherClient;
    }

    @Override
    public Response publish(Message message) {

        if (null == message) {
            return null;
        }

        if (log.isDebugEnabled()) {
            log.info("Publish message: {}", message);
        }
        SimpleMessage simpleMessage = EventMeshClientUtil.buildSimpleMessage(message, clientConfig, PROTOCOL_TYPE);
        try {
            Response response = publisherClient.publish(simpleMessage);
            if (log.isInfoEnabled()) {
                log.info("Received response:{}", response);
            }
            return response;
        } catch (Exception e) {
            log.error("Error in publishing message {}", message, e);
        }
        return null;
    }

    @Override
    public Response publish(List<Message> messages) {

        if (CollectionUtils.isEmpty(messages)) {
            return null;
        }
        BatchMessage batchMessage = EventMeshClientUtil.buildBatchMessages(messages, clientConfig, PROTOCOL_TYPE);
        try {
            Response response = publisherClient.batchPublish(batchMessage);
            if (log.isInfoEnabled()) {
                log.info("Received response:{}", response);
            }
            return response;
        } catch (Exception e) {
            if (log.isErrorEnabled()) {
                log.error("Error in BatchPublish message {}", messages, e);
            }
        }
        return null;
    }

    @Override
    public Message requestReply(final Message message, long timeout) {
        if (log.isInfoEnabled()) {
            log.info("RequestReply message:{}", message);
        }

        SimpleMessage simpleMessage = EventMeshClientUtil.buildSimpleMessage(message, clientConfig, PROTOCOL_TYPE);
        try {
            SimpleMessage reply = publisherClient.withDeadlineAfter(timeout, TimeUnit.MILLISECONDS).requestReply(simpleMessage);
            if (log.isInfoEnabled()) {
                log.info("Received reply message:{}", reply);
            }

            final Object msg = EventMeshClientUtil.buildMessage(reply, PROTOCOL_TYPE);
            if (msg instanceof Message) {
                return (Message) msg;
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (log.isErrorEnabled()) {
                log.error("Error in RequestReply message {}", message, e);
            }
        }
        return null;
    }
}
