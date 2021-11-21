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

import io.cloudevents.CloudEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.common.protocol.http.HttpCommand;
import org.apache.eventmesh.common.protocol.http.body.Body;
import org.apache.eventmesh.common.protocol.http.common.RequestCode;
import org.apache.eventmesh.common.protocol.tcp.Header;
import org.apache.eventmesh.common.protocol.tcp.Package;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;
import org.apache.eventmesh.protocol.cloudevents.resolver.http.SendMessageBatchProtocolResolver;
import org.apache.eventmesh.protocol.cloudevents.resolver.http.SendMessageBatchV2ProtocolResolver;
import org.apache.eventmesh.protocol.cloudevents.resolver.http.SendMessageRequestProtocolResolver;
import org.apache.eventmesh.protocol.cloudevents.resolver.tcp.TcpMessageProtocolResolver;

import java.util.List;

/**
 * CloudEvents protocol adaptor, used to transform CloudEvents message to CloudEvents message.
 *
 * @since 1.3.0
 */
public class CloudEventsProtocolAdaptor<T extends ProtocolTransportObject>
    implements ProtocolAdaptor<ProtocolTransportObject> {

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject cloudEvent) throws ProtocolHandleException {

        if (cloudEvent instanceof Package) {
            Header header = ((Package) cloudEvent).getHeader();
            Object body = ((Package) cloudEvent).getBody();

            return deserializeTcpProtocol(header, body);

        } else if (cloudEvent instanceof HttpCommand) {
            org.apache.eventmesh.common.protocol.http.header.Header header = ((HttpCommand) cloudEvent).getHeader();
            Body body = ((HttpCommand) cloudEvent).getBody();
            String requestCode = ((HttpCommand) cloudEvent).getRequestCode();

            return deserializeHttpProtocol(requestCode, header, body);
        } else {
            throw new ProtocolHandleException(String.format("protocol class: %s", cloudEvent.getClass()));
        }
    }

    private CloudEvent deserializeTcpProtocol(Header header, Object body) throws ProtocolHandleException {
        return TcpMessageProtocolResolver.buildEvent(header, body);
    }

    private CloudEvent deserializeHttpProtocol(String requestCode, org.apache.eventmesh.common.protocol.http.header.Header header, Body body) throws ProtocolHandleException {

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
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol)
        throws ProtocolHandleException {
        return null;
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) throws ProtocolHandleException {
        String protocolDesc = cloudEvent.getExtension(Constants.PROTOCOL_DESC).toString();
        if (StringUtils.equals("http", protocolDesc)) {
            // todo: return command, set cloudEvent.getData() to content?
            return null;
//            return new String(cloudEvent.getData().toBytes(), StandardCharsets.UTF_8);
        } else if (StringUtils.equals("tcp", protocolDesc)) {
            Package pkg = new Package();
            pkg.setBody(cloudEvent);
            return pkg;
        } else {
            throw new ProtocolHandleException(String.format("Unsupported protocolDesc: %s", protocolDesc));
        }

    }

    @Override
    public String getProtocolType() {
        return CloudEventsProtocolConstant.PROTOCOL_NAME;
    }
}
