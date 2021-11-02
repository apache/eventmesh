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

package org.apache.eventmesh.api.factory;

import org.apache.eventmesh.api.consumer.Consumer;
import org.apache.eventmesh.api.producer.Producer;
import org.apache.eventmesh.spi.EventMeshExtensionFactory;

/**
 * The factory to get connector {@link Producer} and {@link Consumer}
 */
public class ConnectorPluginFactory {

    /**
     * Get MeshMQProducer instance by plugin name
     *
     * @param connectorPluginName plugin name
     * @return MeshMQProducer instance
     */
    public static Producer getMeshMQProducer(String connectorPluginName) {
        return EventMeshExtensionFactory.getExtension(Producer.class, connectorPluginName);
    }

    /**
     * Get MeshMQPushConsumer instance by plugin name
     *
     * @param connectorPluginName plugin name
     * @return MeshMQPushConsumer instance
     */
    public static Consumer getMeshMQPushConsumer(String connectorPluginName) {
        return EventMeshExtensionFactory.getExtension(Consumer.class, connectorPluginName);
    }

    private static <T> T getPlugin(Class<T> pluginType, String pluginName) {
        return EventMeshExtensionFactory.getExtension(pluginType, pluginName);
    }
}
