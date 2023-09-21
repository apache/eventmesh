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

package org.apache.eventmesh.protocol.meshmessage;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.BatchEventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.meshmessage.resolver.grpc.GrpcEventMeshCloudEventProtocolResolver;
import org.apache.eventmesh.protocol.meshmessage.resolver.http.SendMessageBatchProtocolResolver;
import org.apache.eventmesh.protocol.meshmessage.resolver.http.SendMessageBatchV2ProtocolResolver;
import org.apache.eventmesh.protocol.meshmessage.resolver.http.SendMessageRequestProtocolResolver;
import org.apache.eventmesh.protocol.meshmessage.resolver.tcp.TcpMessageProtocolResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;

import com.google.common.base.Preconditions;

public class MeshMessageProtocolAdaptor implements ProtocolAdaptor<ProtocolTransportObject> {

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        if (protocol instanceof Package) {
            Package tcpPackage = (Package) protocol;
            Header header = tcpPackage.getHeader();
            String bodyJson = (String) tcpPackage.getBody();

            return deserializeTcpProtocol(header, bodyJson);

        } else if (protocol instanceof HttpCommand) {
            org.apache.eventmesh.common.protocol.http.header.Header header = ((HttpCommand) protocol).getHeader();
            Body body = ((HttpCommand) protocol).getBody();
            String requestCode = ((HttpCommand) protocol).getRequestCode();
            return deserializeHttpProtocol(requestCode, header, body);
        } else if (protocol instanceof EventMeshCloudEventWrapper) {
            EventMeshCloudEventWrapper wrapper = (EventMeshCloudEventWrapper) protocol;
            return GrpcEventMeshCloudEventProtocolResolver.buildEvent(wrapper.getMessage());
        } else {
            throw new ProtocolHandleException(String.format("protocol class: %s", protocol.getClass()));
        }
    }

    private CloudEvent deserializeTcpProtocol(Header header, String bodyJson) throws ProtocolHandleException {
        return TcpMessageProtocolResolver.buildEvent(header, JsonUtils.parseObject(bodyJson, EventMeshMessage.class));
    }

    private CloudEvent deserializeHttpProtocol(String requestCode,
        org.apache.eventmesh.common.protocol.http.header.Header header,
        Body body) throws ProtocolHandleException {

        switch (RequestCode.get(Integer.parseInt(requestCode))) {
            case MSG_BATCH_SEND:
                return SendMessageBatchProtocolResolver.buildEvent(header, body);
            case MSG_BATCH_SEND_V2:
                return SendMessageBatchV2ProtocolResolver.buildEvent(header, body);
            case MSG_SEND_SYNC:
            case MSG_SEND_ASYNC:
                return SendMessageRequestProtocolResolver.buildEvent(header, body);
            default:
                throw new ProtocolHandleException(String.format("unsupported requestCode: %s", requestCode));
        }

    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        if (protocol instanceof BatchEventMeshCloudEventWrapper) {
            CloudEventBatch cloudEventBatch = ((BatchEventMeshCloudEventWrapper) protocol).getMessage();
            return GrpcEventMeshCloudEventProtocolResolver.buildBatchEvents(cloudEventBatch);
        } else {
            throw new ProtocolHandleException(String.format("protocol class: %s", protocol.getClass()));
        }
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        validateCloudEvent(cloudEvent);
        String protocolDesc =
            cloudEvent.getExtension(Constants.PROTOCOL_DESC) == null ? null : cloudEvent.getExtension(Constants.PROTOCOL_DESC).toString();

        switch (Objects.requireNonNull(protocolDesc)) {
            case MeshMessageProtocolConstant.PROTOCOL_DESC_HTTP:
                HttpCommand httpCommand = new HttpCommand();
                Body body = new Body() {

                    final Map<String, Object> map = new HashMap<>();

                    @Override
                    public Map<String, Object> toMap() {
                        if (cloudEvent.getData() == null) {
                            return map;
                        }
                        map.put(MeshMessageProtocolConstant.PROTOCOL_KEY_CONTENT,
                            new String(cloudEvent.getData().toBytes(), Constants.DEFAULT_CHARSET));
                        return map;
                    }
                };
                body.toMap();
                httpCommand.setBody(body);
                return httpCommand;
            case MeshMessageProtocolConstant.PROTOCOL_DESC_TCP:
                return TcpMessageProtocolResolver.buildEventMeshMessage(cloudEvent);
            case MeshMessageProtocolConstant.PROTOCOL_DESC_GRPC_CLOUD_EVENT:
                return GrpcEventMeshCloudEventProtocolResolver.buildEventMeshCloudEvent(cloudEvent);
            default:
                throw new ProtocolHandleException(String.format("Unsupported protocolDesc: %s", protocolDesc));
        }
    }

    @Override
    public String getProtocolType() {
        return MeshMessageProtocolConstant.PROTOCOL_NAME;
    }

    private void validateCloudEvent(CloudEvent cloudEvent) {
        Preconditions.checkNotNull(cloudEvent, "CloudEvent cannot be null");
    }
}
