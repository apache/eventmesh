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

package org.apache.eventmesh.runtime.core.protocol.mqtt.processor;

import org.apache.eventmesh.runtime.acl.Acl;
import org.apache.eventmesh.runtime.boot.EventMeshMQTTServer;
import org.apache.eventmesh.runtime.configuration.EventMeshMQTTConfiguration;
import org.apache.eventmesh.runtime.core.protocol.mqtt.client.ClientManager;

import java.util.concurrent.Executor;

public abstract class AbstractMqttProcessor implements MqttProcessor {

    protected final EventMeshMQTTServer eventMeshMQTTServer;

    private Executor executor;

    protected EventMeshMQTTConfiguration configuration;

    protected Acl acl;

    ClientManager clientManager = ClientManager.getInstance();

    public AbstractMqttProcessor(EventMeshMQTTServer eventMeshMQTTServer) {
        this.eventMeshMQTTServer = eventMeshMQTTServer;
        this.configuration = (EventMeshMQTTConfiguration) eventMeshMQTTServer.getConfiguration();
        this.acl = eventMeshMQTTServer.getAcl();
    }

    public AbstractMqttProcessor(EventMeshMQTTServer eventMeshMQTTServer, Executor executor) {
        this(eventMeshMQTTServer);
        this.executor = executor;
    }

    public EventMeshMQTTServer getEventMeshMQTTServer() {
        return eventMeshMQTTServer;
    }

    @Override
    public Executor executor() {
        return this.executor;
    }
}
