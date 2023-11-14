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

package org.apache.eventmesh.admin.config;

import static org.apache.eventmesh.admin.constant.ConfigConst.META_TYPE_ETCD;
import static org.apache.eventmesh.admin.constant.ConfigConst.META_TYPE_NACOS;

import org.apache.eventmesh.admin.service.ConnectionService;
import org.apache.eventmesh.admin.service.SubscriptionService;
import org.apache.eventmesh.admin.service.impl.EtcdConnectionService;
import org.apache.eventmesh.admin.service.impl.EtcdSubscriptionService;
import org.apache.eventmesh.admin.service.impl.NacosConnectionService;
import org.apache.eventmesh.admin.service.impl.NacosSubscriptionService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use different registry SDK depending on the configured meta type
 */
@Configuration
public class MetaTypeConfig {

    private final AdminProperties adminProperties;

    public MetaTypeConfig(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Bean
    public ConnectionService connectionService() {
        switch (adminProperties.getMeta().getType()) {
            case META_TYPE_NACOS:
                return new NacosConnectionService(adminProperties);
            case META_TYPE_ETCD:
                return new EtcdConnectionService();
            default:
                throw new IllegalArgumentException("Unsupported eventmesh meta type: " + adminProperties.getMeta().getType());
        }
    }

    @Bean
    public SubscriptionService subscriptionService() {
        switch (adminProperties.getMeta().getType()) {
            case META_TYPE_NACOS:
                return new NacosSubscriptionService(adminProperties);
            case META_TYPE_ETCD:
                return new EtcdSubscriptionService();
            default:
                throw new IllegalArgumentException("Unsupported eventmesh meta type: " + adminProperties.getMeta().getType());
        }
    }
}