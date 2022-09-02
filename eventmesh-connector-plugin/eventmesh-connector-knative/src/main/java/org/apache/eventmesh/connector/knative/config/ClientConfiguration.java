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

package org.apache.eventmesh.connector.knative.config;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Preconditions;

public class ClientConfiguration {

    public String emurl = "";
    public String serviceAddr = "";

    public void init() {
        String serviceAddrStr = ConfigurationWrapper.getProp(ConfKeys.KEYS_EVENTMESH_KNATIVE_SERVICE_ADDR);
        Preconditions.checkState(StringUtils.isNotEmpty(serviceAddrStr),
            String.format("%s error", ConfKeys.KEYS_EVENTMESH_KNATIVE_SERVICE_ADDR));
        serviceAddr = StringUtils.trim(serviceAddrStr);
        String[] temp = serviceAddr.split(";");
        emurl = temp[0];
        serviceAddr = temp[1];
    }

    static class ConfKeys {

        public static final String KEYS_EVENTMESH_KNATIVE_SERVICE_ADDR = "eventMesh.server.knative.service";

    }
}
