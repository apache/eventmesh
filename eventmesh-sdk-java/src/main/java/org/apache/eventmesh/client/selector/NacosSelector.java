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

package org.apache.eventmesh.client.selector;

import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.utils.PropertiesUtils;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;

public class NacosSelector implements Selector {
    private static final Logger logger = LoggerFactory.getLogger(NacosSelector.class);
    private NamingService namingService;

    public void init() throws Exception {
        try {
            Properties properties = PropertiesUtils.readPropertiesFile(Constants.CONFIG_FILE_NAME);
            namingService = NamingFactory.createNamingService(properties.getProperty(Constants.EVENTMESH_SELECTOR_NACOS_ADDRESS));
        } catch (NacosException e) {
            logger.error("[NacosSelector][init] error", e);
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public ServiceInstance selectOne(String serviceName) throws Exception {
        try {
            Instance instance = namingService.selectOneHealthyInstance(serviceName);
            if (instance == null) {
                return null;
            }
            ServiceInstance serviceInstance = new ServiceInstance();
            serviceInstance.setHost(instance.getIp());
            serviceInstance.setPort(instance.getPort());
            serviceInstance.setHealthy(instance.isHealthy());
            serviceInstance.setMetadata(instance.getMetadata());
            return serviceInstance;
        } catch (NacosException e) {
            logger.error("[NacosSelector][selectOne] error", e);
            throw new Exception(e.getMessage());
        }
    }

    public void setNamingService(NamingService namingService) {
        this.namingService = namingService;
    }

    public NamingService getNamingService() {
        return namingService;
    }
}
