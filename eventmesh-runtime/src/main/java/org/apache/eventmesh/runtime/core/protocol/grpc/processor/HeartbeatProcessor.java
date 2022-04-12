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
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat;
import org.apache.eventmesh.common.protocol.grpc.protos.Heartbeat.ClientType;
import org.apache.eventmesh.common.protocol.grpc.protos.RequestHeader;
import org.apache.eventmesh.common.protocol.grpc.protos.Response;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.ConsumerManager;
import org.apache.eventmesh.runtime.core.protocol.grpc.consumer.consumergroup.ConsumerGroupClient;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeartbeatProcessor {
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private final Logger aclLogger = LoggerFactory.getLogger("acl");

    private final EventMeshGrpcServer eventMeshGrpcServer;

    public HeartbeatProcessor(EventMeshGrpcServer eventMeshGrpcServer) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
    }

    public void process(Heartbeat heartbeat, EventEmitter<Response> emitter) throws Exception {
        RequestHeader header = heartbeat.getHeader();

        if (!ServiceUtils.validateHeader(header)) {
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR, emitter);
            return;
        }

        if (!ServiceUtils.validateHeartBeat(heartbeat)) {
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        try {
            doAclCheck(heartbeat);
        } catch (AclException e) {
            aclLogger.warn("CLIENT HAS NO PERMISSION, HeartbeatProcessor failed", e);
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_ACL_ERR, e.getMessage(), emitter);
            return;
        }

        // only handle heartbeat for consumers
        ClientType clientType = heartbeat.getClientType();
        if (!ClientType.SUB.equals(clientType)) {
            ServiceUtils.sendRespAndDone(StatusCode.EVENTMESH_PROTOCOL_BODY_ERR, emitter);
            return;
        }

        ConsumerManager consumerManager = eventMeshGrpcServer.getConsumerManager();

        String consumerGroup = heartbeat.getConsumerGroup();

        // update clients' timestamp in the heartbeat items
        for (Heartbeat.HeartbeatItem item : heartbeat.getHeartbeatItemsList()) {
            ConsumerGroupClient hbClient = ConsumerGroupClient.builder()
                .env(header.getEnv())
                .idc(header.getIdc())
                .sys(header.getSys())
                .ip(header.getIp())
                .pid(header.getPid())
                .consumerGroup(consumerGroup)
                .topic(item.getTopic())
                .lastUpTime(new Date())
                .build();
            consumerManager.updateClientTime(hbClient);
        }

        ServiceUtils.sendRespAndDone(StatusCode.SUCCESS, "heartbeat success", emitter);
    }

    private void doAclCheck(Heartbeat heartbeat) throws AclException {
        RequestHeader header = heartbeat.getHeader();
        if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().eventMeshServerSecurityEnable) {
            String remoteAdd = header.getIp();
            String user = header.getUsername();
            String pass = header.getPassword();
            String sys = header.getSys();
            int requestCode = Integer.valueOf(RequestCode.HEARTBEAT.getRequestCode());
            for (Heartbeat.HeartbeatItem item : heartbeat.getHeartbeatItemsList()) {
                Acl.doAclCheckInHttpHeartbeat(remoteAdd, user, pass, sys, item.getTopic(), requestCode);
            }
        }
    }
}
