/*
 * Licensed to Apache Software Foundation (ASF) under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Apache Software Foundation (ASF) licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.eventmesh.runtime.core.plugin;

import org.apache.eventmesh.api.consumer.MeshMQPushConsumer;
import org.apache.eventmesh.api.producer.MeshMQProducer;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

public class PluginFactory {

    public static MeshMQProducer getMeshMQProducer(String connectorPluginName) {
        return EventMeshExtensionFactory.getExtension(MeshMQProducer.class, connectorPluginName);
    }

    public static MeshMQPushConsumer getMeshMQPushConsumer(String connectorPluginName) {
        return EventMeshExtensionFactory.getExtension(MeshMQPushConsumer.class, connectorPluginName);
    }

    private static <T> T getPlugin(Class<T> pluginType, String pluginName) {
        return EventMeshExtensionFactory.getExtension(pluginType, pluginName);
    }
}
