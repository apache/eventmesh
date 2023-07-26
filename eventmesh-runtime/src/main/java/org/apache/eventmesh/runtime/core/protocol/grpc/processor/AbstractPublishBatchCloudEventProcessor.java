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
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEvent;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventUtils;
import org.apache.eventmesh.common.protocol.grpc.common.StatusCode;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshGrpcServer;
import org.apache.eventmesh.runtime.constants.EventMeshConstants;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.EventEmitter;
import org.apache.eventmesh.runtime.core.protocol.grpc.service.ServiceUtils;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPublishBatchCloudEventProcessor implements PublishProcessor<CloudEventBatch, CloudEvent> {

    private static final Logger aclLogger = LoggerFactory.getLogger("acl");

    protected final EventMeshGrpcServer eventMeshGrpcServer;

    protected final Acl acl;

    public AbstractPublishBatchCloudEventProcessor(final EventMeshGrpcServer eventMeshGrpcServer, final Acl acl) {
        this.eventMeshGrpcServer = eventMeshGrpcServer;
        this.acl = acl;
    }

    @Override
    public void process(CloudEventBatch cloudEvent, EventEmitter<CloudEvent> emitter) throws Exception {

        // control flow rate limit
        if (!eventMeshGrpcServer.getMsgRateLimiter().tryAcquire(EventMeshConstants.DEFAULT_FASTFAIL_TIMEOUT_IN_MILLISECONDS, TimeUnit.MILLISECONDS)) {
            log.error("Send message speed over limit.");
            ServiceUtils.sendResponseCompleted(StatusCode.EVENTMESH_SEND_MESSAGE_SPEED_OVER_LIMIT_ERR, emitter);
            return;
        }

        StatusCode cloudEventCheck = cloudEventBatchCheck(cloudEvent);
        if (cloudEventCheck != StatusCode.SUCCESS) {
            ServiceUtils.sendResponseCompleted(cloudEventCheck, emitter);
            return;
        }
        StatusCode aclCheck = this.aclCheck(cloudEvent.getEvents(0));
        if (aclCheck != StatusCode.SUCCESS) {
            ServiceUtils.sendResponseCompleted(aclCheck, emitter);
            return;
        }
        handleCloudEvent(cloudEvent, emitter);
    }

    public StatusCode cloudEventBatchCheck(CloudEventBatch cloudEventBatch) {
        if (!ServiceUtils.validateCloudEventBatchAttributes(cloudEventBatch)) {
            return StatusCode.EVENTMESH_PROTOCOL_HEADER_ERR;
        }

        if (!ServiceUtils.validateCloudEventBatchData(cloudEventBatch)) {
            return StatusCode.EVENTMESH_PROTOCOL_BODY_ERR;
        }
        return StatusCode.SUCCESS;
    }

    public StatusCode aclCheck(CloudEvent cloudEvent) {
        try {
            if (eventMeshGrpcServer.getEventMeshGrpcConfiguration().isEventMeshServerSecurityEnable()) {
                String remoteAdd = EventMeshCloudEventUtils.getIp(cloudEvent);
                String user = EventMeshCloudEventUtils.getUserName(cloudEvent);
                String pass = EventMeshCloudEventUtils.getPassword(cloudEvent);
                String subsystem = EventMeshCloudEventUtils.getSys(cloudEvent);
                String topic = EventMeshCloudEventUtils.getSubject(cloudEvent);
                this.acl.doAclCheckInHttpSend(remoteAdd, user, pass, subsystem, topic, RequestCode.MSG_SEND_ASYNC.getRequestCode());
            }
        } catch (AclException e) {
            aclLogger.warn("Client has no permission,AbstructPublishCloudEventProcessor send failed", e);
            return StatusCode.EVENTMESH_ACL_ERR;
        }
        return StatusCode.SUCCESS;
    }

    abstract void handleCloudEvent(CloudEventBatch cloudEventBatch, EventEmitter<CloudEvent> emitter) throws Exception;
}
