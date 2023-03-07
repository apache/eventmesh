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

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;
import org.apache.eventmesh.common.config.convert.converter.ListConverter.ListConverterSemi;

import java.util.List;

import lombok.Data;

@Data
@Config(prefix = "eventMesh.server.knative", path = "classPath://knative-client.properties")
public class ClientConfiguration {

    @ConfigFiled(field = "service", converter = ListConverterSemi.class)
    public List<String> service;

    /**
     * In keeping with the old way of configuration parsing, the value is taken from the service field [0]
     */
    @ConfigFiled(reload = true)
    public String emurl = "";

    /**
     * In keeping with the old way of configuration parsing, the value is taken from the service field [1]
     */
    @ConfigFiled(reload = true)
    public String serviceAddr = "";

    public void reload() {
        emurl = this.service.get(0);
        serviceAddr = this.service.get(1);
    }
}
