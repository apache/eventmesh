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

package org.apache.eventmesh.registry.zookeeper.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh.registry.zookeeper")
public class ZKRegistryConfiguration {

    @ConfigFiled(field = "scheme")
    private String scheme;

    @ConfigFiled(field = "auth")
    private String auth;

    @ConfigFiled(field = "connectionTimeoutMs")
    private Integer connectionTimeoutMs = 5000;

    @ConfigFiled(field = "sessionTimeoutMs")
    private Integer sessionTimeoutMs = 40000;

    // Fully qualified name of RetryPolicy implementation
    @ConfigFiled(field = "retryPolicy.class")
    private String retryPolicyClass;

    @ConfigFiled(field = "retryPolicy.baseSleepTimeMs")
    private Integer baseSleepTimeMs = 1000;

    @ConfigFiled(field = "retryPolicy.maxRetries")
    private Integer maxRetries = 5;

    @ConfigFiled(field = "retryPolicy.maxSleepTimeMs")
    private Integer maxSleepTimeMs = 5000;

    @ConfigFiled(field = "retryPolicy.retryIntervalMs")
    private Integer retryIntervalTimeMs = 1000;

    @ConfigFiled(field = "retryPolicy.nTimes")
    private Integer retryNTimes = 10;

    @ConfigFiled(field = "retryPolicy.sleepMsBetweenRetries")
    private Integer sleepMsBetweenRetries = 1000;

}
