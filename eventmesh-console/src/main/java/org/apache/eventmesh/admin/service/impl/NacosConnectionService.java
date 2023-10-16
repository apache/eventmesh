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

package org.apache.eventmesh.admin.service.impl;

import org.apache.eventmesh.admin.config.AdminProperties;
import org.apache.eventmesh.admin.service.ConnectionService;

import java.util.Properties;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NacosConnectionService implements ConnectionService {

    Properties properties = new Properties();

    public NacosConnectionService(AdminProperties adminProperties) {
        properties.setProperty("serverAddr", adminProperties.getMeta().getNacos().getAddr());
        properties.setProperty("username", adminProperties.getMeta().getNacos().getUsername());
        properties.setProperty("password", adminProperties.getMeta().getNacos().getPassword());
        properties.setProperty("namespace", adminProperties.getMeta().getNacos().getNamespace());
        properties.setProperty("timeoutMs", String.valueOf(adminProperties.getConfig().getTimeoutMs()));
    }

}
