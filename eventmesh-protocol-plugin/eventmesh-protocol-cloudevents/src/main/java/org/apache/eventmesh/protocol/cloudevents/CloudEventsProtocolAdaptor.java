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

package org.apache.eventmesh.protocol.cloudevents;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.grpc.cloudevents.CloudEventBatch;
import org.apache.eventmesh.common.protocol.grpc.common.BatchEventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.grpc.common.EventMeshCloudEventWrapper;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.cloudevents.resolver.grpc.GrpcEventMeshCloudEventProtocolResolver;
import org.apache.eventmesh.protocol.cloudevents.resolver.http.SendMessageBatchProtocolResolver;
import org.apache.eventmesh.protocol.cloudevents.resolver.http.SendMessageBatchV2ProtocolResolver;
import org.apache.eventmesh.protocol.cloudevents.resolver.http.SendMessageRequestProtocolResolver;
import org.apache.eventmesh.protocol.cloudevents.resolver.tcp.TcpMessageProtocolResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;

import com.google.common.base.Preconditions;

/**
 * CloudEvents protocol adaptor, used to transform CloudEvents message to CloudEvents message.
 *
 * @since 1.3.0
 */
public class CloudEventsProtocolAdaptor<T extends ProtocolTransportObject>
    implements
        ProtocolAdaptor<ProtocolTransportObject> {

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject cloudEvent) throws ProtocolHandleException {

        if (cloudEvent instanceof Package) {
            Package tcpPackage = (Package) cloudEvent;
            Header header = tcpPackage.getHeader();
            String cloudEventJson = tcpPackage.getBody().toString();

            return deserializeTcpProtocol(header, cloudEventJson);

        } else if (cloudEvent instanceof HttpCommand) {
            org.apache.eventmesh.common.protocol.http.header.Header header = ((HttpCommand) cloudEvent).getHeader();
            Body body = ((HttpCommand) cloudEvent).getBody();
            String requestCode = ((HttpCommand) cloudEvent).getRequestCode();

            return deserializeHttpProtocol(requestCode, header, body);
        } else if (cloudEvent instanceof EventMeshCloudEventWrapper) {
            EventMeshCloudEventWrapper ce = (EventMeshCloudEventWrapper) cloudEvent;
            return GrpcEventMeshCloudEventProtocolResolver.buildEvent(ce.getMessage());
        } else {
            throw new ProtocolHandleException(String.format("protocol class: %s", cloudEvent.getClass()));
        }
    }

    private CloudEvent deserializeTcpProtocol(Header header, String cloudEventJson) throws ProtocolHandleException {
        return TcpMessageProtocolResolver.buildEvent(header, cloudEventJson);
    }

    private CloudEvent deserializeHttpProtocol(String requestCode,
                                               org.apache.eventmesh.common.protocol.http.header.Header header,
                                               Body body)
        throws ProtocolHandleException {

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
        Preconditions.checkNotNull(cloudEvent, "cloudEvent cannot be null");
        String protocolDesc = Objects.requireNonNull(cloudEvent.getExtension(Constants.PROTOCOL_DESC)).toString();
        switch (protocolDesc) {
            case "http":
                HttpCommand httpCommand = new HttpCommand();
                Body body = new Body() {

                    final Map<String, Object> map = new HashMap<>();

                    @Override
                    public Map<String, Object> toMap() {
                        byte[] eventByte =
                            Objects.requireNonNull(EventFormatProvider.getInstance()
                                .resolveFormat(JsonFormat.CONTENT_TYPE)).serialize(cloudEvent);
                        map.put("content", new String(eventByte, Constants.DEFAULT_CHARSET));
                        return map;
                    }
                };
                body.toMap();
                httpCommand.setBody(body);
                return httpCommand;
            case "tcp":
                Package pkg = new Package();
                String dataContentType = cloudEvent.getDataContentType();
                Preconditions.checkNotNull(dataContentType, "DateContentType cannot be null");
                EventFormat eventFormat = EventFormatProvider.getInstance().resolveFormat(dataContentType);
                Preconditions.checkNotNull(eventFormat,
                    String.format("DateContentType:%s is not supported", dataContentType));
                pkg.setBody(eventFormat.serialize(cloudEvent));
                return pkg;
            case CloudEventsProtocolConstant.PROTOCOL_DESC_GRPC_CLOUD_EVENT:
                return GrpcEventMeshCloudEventProtocolResolver.buildEventMeshCloudEvent(cloudEvent);
            default:
                throw new ProtocolHandleException(String.format("Unsupported protocolDesc: %s", protocolDesc));
        }

    }

    @Override
    public String getProtocolType() {
        return CloudEventsProtocolConstant.PROTOCOL_NAME;
    }
}
