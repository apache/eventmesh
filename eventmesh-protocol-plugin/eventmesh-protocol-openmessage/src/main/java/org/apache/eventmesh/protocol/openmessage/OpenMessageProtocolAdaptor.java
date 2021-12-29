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

package org.apache.eventmesh.protocol.openmessage;

import java.util.List;

import io.cloudevents.CloudEvent;
import io.openmessaging.api.Message;

import org.apache.eventmesh.common.protocol.ProtocolTransportObject;
import org.apache.eventmesh.protocol.api.ProtocolAdaptor;
import org.apache.eventmesh.protocol.api.exception.ProtocolHandleException;

/**
 * OpenMessage protocol adaptor, used to transform protocol between
 * {@link CloudEvent} with {@link Message}.
 *
 * @since 1.3.0
 */
public class OpenMessageProtocolAdaptor<T extends ProtocolTransportObject> implements ProtocolAdaptor<ProtocolTransportObject> {

    @Override
    public CloudEvent toCloudEvent(ProtocolTransportObject message) {
        return null;
    }

    @Override
    public List<CloudEvent> toBatchCloudEvent(ProtocolTransportObject protocol) throws ProtocolHandleException {
        return null;
    }

    @Override
    public ProtocolTransportObject fromCloudEvent(CloudEvent cloudEvent) {
        return null;
    }

    @Override
    public String getProtocolType() {
        return OpenMessageProtocolConstant.PROTOCOL_NAME;
    }
}
