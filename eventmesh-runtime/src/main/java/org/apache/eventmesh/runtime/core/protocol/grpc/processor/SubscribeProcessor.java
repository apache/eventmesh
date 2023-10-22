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
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.EventMeshConsumer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.GrpcType;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubscribeProcessor {

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private static final GrpcType grpcType = GrpcType.WEBHOOK;

    private final Acl acl;

    public SubscribeProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.acl = eventMeshGrpcServer.getAcl();
    }

    public void process(final CloudEvent subscription, final EventEmitter<CloudEvent> emitter) throws Exception {
        Objects.requireNonNull(subscription, "subscription can not be null");
        Objects.requireNonNull(emitter, "emitter can not be null");

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
            if (log.isWarnEnabled()) {
                log.warn("CLIENT HAS NO PERMISSION to Subscribe. failed", e);
            }
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
            return;
        }

        final ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        final String consumerGroup = EventMeshCloudEventUtils.getConsumerGroup(subscription);
        // Collect new clients in the subscription
        List<SubscriptionItem> subscriptionItems = JsonUtils.parseTypeReferenceObject(subscription.getTextData(),
            new TypeReference<List<SubscriptionItem>>() {
            });

        Objects.requireNonNull(subscriptionItems, "subscriptionItems must not be null");
        final String env = EventMeshCloudEventUtils.getEnv(subscription);
        final String idc = EventMeshCloudEventUtils.getIdc(subscription);
        final String sys = EventMeshCloudEventUtils.getSys(subscription);
        final String ip = EventMeshCloudEventUtils.getIp(subscription);
        final String pid = EventMeshCloudEventUtils.getPid(subscription);
        final String url = EventMeshCloudEventUtils.getURL(subscription);
        final List<ConsumerGroupClient> newClients = new LinkedList<>();
        for (final SubscriptionItem item : subscriptionItems) {
            final ConsumerGroupClient newClient = ConsumerGroupClient.builder()
                .env(env)
                .idc(idc)
                .sys(sys)
                .ip(ip)
                .pid(pid)
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
        newClients.forEach(consumerManager::registerClient);

        // register new clients into EventMeshConsumer
        final EventMeshConsumer eventMeshConsumer = consumerManager.getEventMeshConsumer(consumerGroup);

        boolean requireRestart = false;
        for (final ConsumerGroupClient newClient : newClients) {
            if (eventMeshConsumer.registerClient(newClient)) {
                requireRestart = true;
            }
        }

        // restart consumer group if required
        if (requireRestart) {
            if (log.isInfoEnabled()) {
                log.info("ConsumerGroup {} topic info changed, restart EventMesh Consumer", consumerGroup);
            }
            consumerManager.restartEventMeshConsumer(consumerGroup);
        } else {
            if (log.isWarnEnabled()) {
                log.warn("EventMesh consumer [{}] didn't restart.", consumerGroup);
            }
        }
        ServiceUtils.sendResponseCompleted(StatusCode.SUCCESS, "subscribe success", emitter);
    }

    private void doAclCheck(final CloudEvent subscription) throws AclException {
        List<SubscriptionItem> subscriptionItems = JsonUtils.parseTypeReferenceObject(subscription.getTextData(),
            new TypeReference<List<SubscriptionItem>>() {
            });
        Objects.requireNonNull(subscriptionItems, "subscriptionItems must not be null");
        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().isEventMeshServerSecurityEnable()) {
            for (final SubscriptionItem item : subscriptionItems) {
                this.acl.doAclCheckInHttpReceive(EventMeshCloudEventUtils.getConsumerGroup(subscription),
                    EventMeshCloudEventUtils.getUserName(subscription), EventMeshCloudEventUtils.getPassword(subscription),
                    EventMeshCloudEventUtils.getSys(subscription), item.getTopic(), RequestCode.SUBSCRIBE.getRequestCode());
            }
        }
    }
}
