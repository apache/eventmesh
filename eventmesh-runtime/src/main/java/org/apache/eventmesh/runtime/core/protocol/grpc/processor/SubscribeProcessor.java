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

import org.apache.eventmesh.api.exception.AclException;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.grpc.protos.Subscription;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class SubscribeProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final Logger aclLogger = LoggerFactory.getLogger("acl");

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final GrpcType grpcType = GrpcType.WEBHOOK;

    public SubscribeProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(Subscription subscription, EventEmitter<Response> emitter) throws Exception {
        RequestHeader header = subscription.getHeader();

        if (!ServiceUtils.validateHeader(header)) {
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateSubscription(grpcType, subscription)) {
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        try {
            doAclCheck(subscription);
        } catch (AclException e) {
            aclLogger.warn("CLIENT HAS NO PERMISSION to Subscribe. failed", e);
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
            return;
        }

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        String consumerGroup = subscription.getConsumerGroup();
        String url = subscription.getUrl();
        List<Subscription.SubscriptionItem> subscriptionItems = subscription.getSubscriptionItemsList();

        // Collect new clients in the subscription
        List<ConsumerGroupClient> newClients = new LinkedList<>();
        for (Subscription.SubscriptionItem item : subscriptionItems) {
            ConsumerGroupClient newClient = ConsumerGroupClient.builder()
                .env(header.getEnv())
                .idc(header.getIdc())
                .sys(header.getSys())
                .ip(header.getIp())
                .pid(header.getPid())
                .consumerGroup(consumerGroup)
                .topic(item.getTopic())
                .grpcType(grpcType)
                .subscriptionMode(item.getMode())
                .url(url)
                .lastUpTime(new Date())
                .build();
            newClients.add(newClient);
        }

        // register new clients into ConsumerManager
        for (ConsumerGroupClient newClient : newClients) {
            consumerManager.registerClient(newClient);
        }

        // register new clients into EventMeshConsumer
        EventMeshConsumer eventMeshConsumer = consumerManager.getEventMeshConsumer(consumerGroup);

        boolean requireRestart = false;
        for (ConsumerGroupClient newClient : newClients) {
            if (eventMeshConsumer.registerClient(newClient)) {
                requireRestart = true;
            }
        }

        // restart consumer group if required
        if (requireRestart) {
            logger.info("ConsumerGroup {} topic info changed, restart EventMesh Consumer", consumerGroup);
            consumerManager.restartEventMeshConsumer(consumerGroup);
        } else {
            logger.warn("EventMesh consumer [{}] didn't restart.", consumerGroup);
        }

        ServiceUtils.sendRespAndDone(StatusCode.SUCCESS, "subscribe success", emitter);
    }

    private void doAclCheck(Subscription subscription) throws AclException {
        RequestHeader header = subscription.getHeader();
        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshServerSecurityEnable) {
            String remoteAdd = header.getIp();
            String user = header.getUsername();
            String pass = header.getPassword();
            String subsystem = header.getSys();
            for (Subscription.SubscriptionItem item : subscription.getSubscriptionItemsList()) {
                Acl.doAclCheckInHttpReceive(remoteAdd, user, pass, subsystem, item.getTopic(),
                        RequestCode.SUBSCRIBE.getRequestCode());
            }
        }
    }
}