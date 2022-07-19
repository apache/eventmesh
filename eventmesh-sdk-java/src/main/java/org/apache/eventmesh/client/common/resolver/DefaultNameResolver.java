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

package org.apache.eventmesh.client.common.resolver;


import org.apache.eventmesh.client.common.EventMeshAddress;

import java.net.URI;
import java.util.Set;

import com.google.common.collect.Sets;

public class DefaultNameResolver implements EventMeshNameResolver<EventMeshAddress> {

    private URI uri;

    private ServerListener listener;


    @Override
    public void init(URI target) {
        this.uri = target;
    }

    @Override
    public void start(ServerListener serverListener) {
        this.listener = serverListener;

        final EventMeshAddress address = new EventMeshAddress();
        address.setHost(this.uri.getHost());
        address.setPort(this.uri.getPort());

        final Set<EventMeshAddress> addressSet = Sets.newConcurrentHashSet();
        addressSet.add(address);

        this.listener.refresh(addressSet);
    }

    @Override
    public void shutdown() {

    }


}
