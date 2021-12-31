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
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.common.utils.JsonUtils;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.meshmessage.resolver.http.SendMessageBatchProtocolResolver;
import org.apache.eventmesh.protocol.meshmessage.resolver.http.SendMessageBatchV2ProtocolResolver;
import org.apache.eventmesh.protocol.meshmessage.resolver.http.SendMessageRequestProtocolResolver;
import org.apache.eventmesh.protocol.meshmessage.resolver.tcp.TcpMessageProtocolResolver;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cloudevents.CloudEvent;

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
        } else {
            throw new ProtocolHandleException(String.format("protocol class: %s", protocol.getClass()));
        }
    }

    private CloudEvent deserializeTcpProtocol(Header header, String bodyJson) throws ProtocolHandleException {
        return TcpMessageProtocolResolver.buildEvent(header, JsonUtils.deserialize(bodyJson, EventMeshMessage.class));
    }

    private CloudEvent deserializeHttpProtocol(String requestCode,
                                               org.apache.eventmesh.common.protocol.http.header.Header header,
                                               Body body) throws ProtocolHandleException {

        if (String.valueOf(RequestCode.MSG_BATCH_SEND.getRequestCode()).equals(requestCode)) {
            return SendMessageBatchProtocolResolver.buildEvent(header, body);
        } else if (String.valueOf(RequestCode.MSG_BATCH_SEND_V2.getRequestCode()).equals(requestCode)) {
            return SendMessageBatchV2ProtocolResolver.buildEvent(header, body);
        } else if (String.valueOf(RequestCode.MSG_SEND_SYNC.getRequestCode()).equals(requestCode)) {
            return SendMessageRequestProtocolResolver.buildEvent(header, body);
        } else if (String.valueOf(RequestCode.MSG_SEND_ASYNC.getRequestCode()).equals(requestCode)) {
            return SendMessageRequestProtocolResolver.buildEvent(header, body);
        } else {
            throw new ProtocolHandleException(String.format("unsupported requestCode: %s", requestCode));
        }

    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        return null;
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        String protocolDesc = cloudEvent.getExtension(Constants.PROTOCOL_DESC).toString();

        if (StringUtils.equals("http", protocolDesc)) {
            HttpCommand httpCommand = new HttpCommand();
            Body body = new Body() {
                final Map<String, Object> map = new HashMap<>();

                @Override
                public Map<String, Object> toMap() {
                    map.put("content", new String(cloudEvent.getData().toBytes(), StandardCharsets.UTF_8));
                    return map;
                }
            };
            body.toMap();
            httpCommand.setBody(body);
            return httpCommand;
        } else if (StringUtils.equals("tcp", protocolDesc)) {
            return TcpMessageProtocolResolver.buildEventMeshMessage(cloudEvent);
        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolDesc: %s", protocolDesc));
        }
    }

    @Override
    public String getProtocolType() {
        return MeshMessageProtocolConstant.PROTOCOL_NAME;
    }
}
