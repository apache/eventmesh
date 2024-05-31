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

package org.apache.eventmesh.registry;


import org.apache.eventmesh.registry.exception.RegistryException;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.List;

/**
 * RegistryService
 */
@EventMeshSPI(eventMeshExtensionType = EventMeshExtensionType.REGISTRY)
public interface RegistryService {
    void init() throws RegistryException;

    void shutdown() throws RegistryException;

    void subscribe(RegistryListener registryListener, String serviceName);

    void unsubscribe(RegistryListener registryListener, String serviceName);

    List<RegisterServerInfo> selectInstances(QueryInstances serverInfo);

    boolean register(RegisterServerInfo registerInfo) throws RegistryException;

    boolean unRegister(RegisterServerInfo registerInfo) throws RegistryException;
}
