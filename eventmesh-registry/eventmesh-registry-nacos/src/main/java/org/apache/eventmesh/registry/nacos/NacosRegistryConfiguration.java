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

package org.apache.eventmesh.registry.nacos;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigField;

@Data
@NoArgsConstructor
@Config(prefix = "eventMesh.registry.nacos")
public class NacosRegistryConfiguration extends CommonConfiguration {

    @ConfigField(field = PropertyKeyConst.ENDPOINT)
    private String endpoint;

    @ConfigField(field = PropertyKeyConst.ENDPOINT_PORT)
    private String endpointPort;

    @ConfigField(field = PropertyKeyConst.ACCESS_KEY)
    private String accessKey;

    @ConfigField(field = PropertyKeyConst.SECRET_KEY)
    private String secretKey;

    @ConfigField(field = PropertyKeyConst.CLUSTER_NAME)
    private String clusterName;

    @ConfigField(field = PropertyKeyConst.NAMESPACE)
    private String namespace;

    @ConfigField(field = PropertyKeyConst.NAMING_POLLING_THREAD_COUNT)
    private Integer pollingThreadCount = Runtime.getRuntime().availableProcessors() / 2 + 1;

    @ConfigField(field = UtilAndComs.NACOS_NAMING_LOG_NAME)
    private String logFileName;

    @ConfigField(field = UtilAndComs.NACOS_NAMING_LOG_LEVEL)
    private String logLevel;

}
