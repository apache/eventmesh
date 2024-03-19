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

package org.apache.eventmesh.runtime.configuration;

import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Config(prefix = "eventMesh.server")
public class EventMeshMQTTConfiguration extends CommonConfiguration {

    @ConfigFiled(field = "mqtt.port", notNull = true, beNumber = true)
    private int mqttServerPort = 10305;

    @ConfigFiled(field = "mqtt.password")
    private boolean mqttPasswordMust = Boolean.FALSE;

    public int getEventMeshTcpServerPort() {
        return mqttServerPort;
    }

    public boolean isMqttPasswordMust() {
        return mqttPasswordMust;
    }
}
