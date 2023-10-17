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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = Constants.ADMIN_PROPS_PREFIX)
public class AdminProperties {

    private MetaProperties meta = new MetaProperties();

    private ConfigProperties config = new ConfigProperties();

    @Data
    public static class MetaProperties {

        private String type = Constants.META_TYPE_NACOS;

        private NacosProperties nacos = new NacosProperties();

        private EtcdProperties etcd = new EtcdProperties();

        @Data
        public static class NacosProperties {

            private String addr = "127.0.0.1:8848";

            private String namespace = "";

            private boolean authEnabled = false;

            private String protocol = "http";

            private String username;

            private String password;

            private String accessKey;

            private String secretKey;

        }

        @Data
        public static class EtcdProperties {

            private String addr;

        }
    }

    @Data
    public static class ConfigProperties {

        private int timeoutMs = 5000;

    }
}