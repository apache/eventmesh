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

package org.apache.eventmesh.connector.pulsar.config;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClientConfiguration {

    private String serviceAddr;

    public void init() {
        String serviceAddrStr = ConfigurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_PULSAR_SERVICE_ADDR);
        Preconditions.checkState(StringUtils.isNotEmpty(serviceAddrStr),
                String.format("%s error", ConfKeys.KEYS_EVENTMESH_PULSAR_SERVICE_ADDR));
        serviceAddr = StringUtils.trim(serviceAddrStr);
    }

    static class ConfKeys {
        public static final String KEYS_EVENTMESH_PULSAR_SERVICE_ADDR = "eventMesh.server.pulsar.service";
    }
}