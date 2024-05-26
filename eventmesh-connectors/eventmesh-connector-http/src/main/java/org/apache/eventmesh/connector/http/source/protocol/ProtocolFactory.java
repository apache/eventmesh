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

package org.apache.eventmesh.connector.http.source.protocol;

import org.apache.eventmesh.connector.http.source.config.SourceConnectorConfig;
import org.apache.eventmesh.connector.http.source.protocol.impl.CloudEventProtocol;
import org.apache.eventmesh.connector.http.source.protocol.impl.CommonProtocol;
import org.apache.eventmesh.connector.http.source.protocol.impl.GitHubProtocol;

import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>
 * Protocol factory.
 * <p/>
 * This class is responsible for storing and creating instances of {@link Protocol} classes.
 */
public class ProtocolFactory {

    // protocol name -> protocol class
    private final ConcurrentHashMap<String, Class<?>> classes = new ConcurrentHashMap<>();

    // protocol name -> protocol instance
    private final ConcurrentHashMap<String, Protocol> instances = new ConcurrentHashMap<>();

    private final SourceConnectorConfig sourceConnectorConfig;


    public ProtocolFactory(SourceConnectorConfig sourceConnectorConfig) {
        this.sourceConnectorConfig = sourceConnectorConfig;
        // register all protocols
        this.registerProtocol(CloudEventProtocol.PROTOCOL_NAME, CloudEventProtocol.class);
        this.registerProtocol(GitHubProtocol.PROTOCOL_NAME, GitHubProtocol.class);
        this.registerProtocol(CommonProtocol.PROTOCOL_NAME, CommonProtocol.class);
    }


    /**
     * Register a protocol
     *
     * @param name  name of the protocol
     * @param clazz class of the protocol
     */
    public void registerProtocol(String name, Class<?> clazz) {
        name = name.toLowerCase(); // case insensitive
        if (Protocol.class.isAssignableFrom(clazz)) {
            classes.put(name, clazz);
        } else {
            throw new IllegalArgumentException("Class " + clazz.getName() + " does not implement Protocol interface");
        }
    }

    /**
     * Get an instance of a protocol, if it is not already created, create a new instance
     *
     * @param name name of the protocol
     * @return instance of the protocol
     */
    public Protocol getInstance(String name) {
        name = name.toLowerCase(); // case insensitive
        if (instances.containsKey(name)) {
            return instances.get(name);
        }

        if (classes.containsKey(name)) {
            synchronized (this) {
                // double check
                if (instances.containsKey(name)) {
                    return instances.get(name);
                }
                try {
                    // create a new instance
                    Protocol protocol = (Protocol) classes.get(name).newInstance();
                    // initialize the protocol
                    protocol.initialize(sourceConnectorConfig);
                    // put it into the instances map
                    instances.put(name, protocol);
                    return protocol;
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException("Failed to instantiate protocol " + name, e);
                }
            }
        }
        throw new IllegalArgumentException("Protocol " + name + " is not registered");
    }

}
