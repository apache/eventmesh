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

package org.apache.eventmesh.webhook.receive.protocol;

import org.apache.eventmesh.webhook.receive.ManufacturerProtocol;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProtocolManager {

    /**
     * ManufacturerProtocol pool
     */
    private final transient Map<String, ManufacturerProtocol> protocolMap = new HashMap<>();

    {
        this.register(new GithubProtocol());
    }

    void register(final ManufacturerProtocol manufacturerProtocol) {
        Objects.requireNonNull(manufacturerProtocol, "manufacturerProtocol can not be null");

        protocolMap.put(manufacturerProtocol.getManufacturerName(), manufacturerProtocol);
    }

    public ManufacturerProtocol getManufacturerProtocol(final String manufacturerName) {
        return protocolMap.get(manufacturerName);
    }
}
