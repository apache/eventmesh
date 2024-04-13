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
import org.apache.eventmesh.common.protocol.SubscriptionItem;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.GrpcType;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscribeStreamProcessor {

    private static final Logger ACL_LOGGER = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private static final GrpcType grpcType = GrpcType.STREAM;

    private final Acl acl;

    public SubscribeStreamProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.acl = eventMeshGrpcServer.getAcl();

    }

    public void process(CloudEvent subscription, EventEmitter<CloudEvent> emitter) throws Exception {

        if (!ServiceUtils.validateCloudEventAttributes(subscription)) {
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateSubscription(grpcType, subscription)) {
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        try {
            doAclCheck(subscription);
        } catch (AclException e) {
            ACL_LOGGER.warn("CLIENT HAS NO PERMISSION to Subscribe. failed", e);
            ServiceUtils.sendStreamResponseCompleted(subscription, StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
            return;
        }

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        String consumerGroup = EventMeshCloudEventUtils.getConsumerGroup(subscription);

        // Collect new clients in subscription
        final String env = EventMeshCloudEventUtils.getEnv(subscription);
        final String idc = EventMeshCloudEventUtils.getIdc(subscription);
        final String sys = EventMeshCloudEventUtils.getSys(subscription);
        final String ip = EventMeshCloudEventUtils.getIp(subscription);
        final String pid = EventMeshCloudEventUtils.getPid(subscription);
        List<ConsumerGroupClient> newClients = new LinkedList<>();
        List<SubscriptionItem> subscriptionItems = JsonUtils.parseTypeReferenceObject(subscription.getTextData(),
            new TypeReference<List<SubscriptionItem>>() {
            });
        for (SubscriptionItem item : Objects.requireNonNull(subscriptionItems)) {
            ConsumerGroupClient newClient = ConsumerGroupClient.builder()
                .env(env)
                .idc(idc)
                .sys(sys)
                .ip(ip)
                .pid(pid)
                .consumerGroup(consumerGroup)
                .topic(item.getTopic())
                .subscriptionMode(item.getMode())
                .grpcType(grpcType)
                .eventEmitter(emitter)
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
            log.info("ConsumerGroup {} topic info changed, restart EventMesh Consumer", consumerGroup);
            consumerManager.restartEventMeshConsumer(consumerGroup);
        } else {
            log.warn("EventMesh consumer [{}] didn't restart.", consumerGroup);
        }

        ServiceUtils.sendStreamResponse(subscription, StatusCode.SUCCESS, "subscribe success", emitter);
    }

    private void doAclCheck(CloudEvent subscription) throws AclException {

        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().isEventMeshServerSecurityEnable()) {
            String remoteAdd = EventMeshCloudEventUtils.getIp(subscription);
            String user = EventMeshCloudEventUtils.getUserName(subscription);
            String pass = EventMeshCloudEventUtils.getPassword(subscription);
            String subsystem = EventMeshCloudEventUtils.getSys(subscription);
            List<SubscriptionItem> subscriptionItems = JsonUtils.parseTypeReferenceObject(subscription.getTextData(),
                new TypeReference<List<SubscriptionItem>>() {
                });
            for (SubscriptionItem item : Objects.requireNonNull(subscriptionItems)) {
                this.acl.doAclCheckInHttpReceive(remoteAdd, user, pass, subsystem, item.getTopic(), RequestCode.SUBSCRIBE.getRequestCode());
            }
        }
    }
}
