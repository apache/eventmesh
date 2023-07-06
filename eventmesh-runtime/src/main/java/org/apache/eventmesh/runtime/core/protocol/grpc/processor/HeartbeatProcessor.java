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
import org.apache.eventmesh.common.protocol.HeartbeatItem;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

public class HeartbeatProcessor {

    private final Logger aclLogger = LoggerFactory.getLogger(EventMeshConstants.ACL);

    private final EventMeshGrpcServer eventMeshGrpcServer;

    private final Acl acl;

    public HeartbeatProcessor(final EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.acl = eventMeshGrpcServer.getAcl();
    }

    public void process(CloudEvent heartbeat, EventEmitter<CloudEvent> emitter) throws Exception {

        if (!ServiceUtils.validateCloudEventAttributes(heartbeat)) {
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateHeartBeat(heartbeat)) {
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        try {
            doAclCheck(heartbeat);
        } catch (AclException e) {
            aclLogger.warn("CLIENT HAS NO PERMISSION, HeartbeatProcessor failed", e);
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
            return;
        }

        // only handle heartbeat for consumers
        org.apache.eventmesh.common.protocol.grpc.common.ClientType clientType = EventMeshCloudEventUtils.getClientType(heartbeat);
        if (org.apache.eventmesh.common.protocol.grpc.common.ClientType.SUB != clientType) {
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        String consumerGroup = EventMeshCloudEventUtils.getConsumerGroup(heartbeat);
        final String env = EventMeshCloudEventUtils.getEnv(heartbeat);
        final String idc = EventMeshCloudEventUtils.getIdc(heartbeat);
        final String sys = EventMeshCloudEventUtils.getSys(heartbeat);
        final String ip = EventMeshCloudEventUtils.getIp(heartbeat);
        final String pid = EventMeshCloudEventUtils.getPid(heartbeat);
        // update clients' timestamp in the heartbeat items
        List<HeartbeatItem> heartbeatItems = JsonUtils.parseTypeReferenceObject(heartbeat.getTextData(),
            new TypeReference<List<HeartbeatItem>>() {
            });
        for (HeartbeatItem item : heartbeatItems) {
            ConsumerGroupClient hbClient = ConsumerGroupClient.builder()
                .env(env)
                .idc(idc)
                .sys(sys)
                .ip(ip)
                .pid(pid)
                .consumerGroup(consumerGroup)
                .topic(item.getTopic())
                .lastUpTime(new Date())
                .build();

            // consumer group client is lost, and the client needs to resubscribe.
            if (!consumerManager.updateClientTime(hbClient)) {
                ServiceUtils.sendResponseCompleted(StatusCode.CLIENT_RESUBSCRIBE, emitter);
                return;
            }
        }
        ServiceUtils.sendResponseCompleted(StatusCode.SUCCESS, "heartbeat success", emitter);
    }

    private void doAclCheck(CloudEvent heartbeat) throws AclException {

        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().isEventMeshServerSecurityEnable()) {
            String remoteAdd = EventMeshCloudEventUtils.getIp(heartbeat);
            String user = EventMeshCloudEventUtils.getUserName(heartbeat);
            String pass = EventMeshCloudEventUtils.getPassword(heartbeat);
            String sys = EventMeshCloudEventUtils.getSys(heartbeat);
            int requestCode = RequestCode.HEARTBEAT.getRequestCode();
            List<HeartbeatItem> heartbeatItems = JsonUtils.parseTypeReferenceObject(heartbeat.getTextData(),
                new TypeReference<List<HeartbeatItem>>() {
                });
            for (HeartbeatItem item : heartbeatItems) {
                this.acl.doAclCheckInHttpHeartbeat(remoteAdd, user, pass, sys, item.getTopic(), requestCode);
            }
        }
    }
}
