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

package org.apache.eventmesh.client.tcp;

import org.apache.eventmesh.client.IProducer;
import org.apache.eventmesh.client.tcp.conf.EventMeshTCPClientConfig;
import org.apache.eventmesh.client.tcp.impl.eventmeshmessage.EventMeshMessageTCPClient;
import org.apache.eventmesh.common.protocol.tcp.EventMeshMessage;

public class EventMeshTcpClientAdapter extends EventMeshMessageTCPClient implements IProducer {

    public EventMeshTcpClientAdapter(EventMeshTCPClientConfig eventMeshTcpClientConfig) {
        super(eventMeshTcpClientConfig);
    }

    @Override
    public <T> void publish(T message) {
        if (message instanceof EventMeshMessage) {
            publish((EventMeshMessage) message, 100);
        } else {
            throw new IllegalArgumentException("message type invalid, type=" + message.getClass().getName());
        }
    }
}
