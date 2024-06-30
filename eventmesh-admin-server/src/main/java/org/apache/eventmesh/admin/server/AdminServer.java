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

package org.apache.eventmesh.admin.server;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.eventmesh.common.Constants;
import org.apache.eventmesh.common.config.CommonConfiguration;
import org.apache.eventmesh.common.config.ConfigService;
import org.apache.eventmesh.common.remote.Task;
import org.apache.eventmesh.common.remote.exception.ErrorCode;
import org.apache.eventmesh.common.remote.request.ReportHeartBeatRequest;
import org.apache.eventmesh.common.utils.IPUtils;
import org.apache.eventmesh.common.utils.PagedList;
import org.apache.eventmesh.registry.RegisterServerInfo;
import org.apache.eventmesh.registry.RegistryFactory;
import org.apache.eventmesh.registry.RegistryService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@Slf4j
public class AdminServer implements Admin, ApplicationListener<ApplicationReadyEvent> {

    private final RegistryService registryService;

    private final RegisterServerInfo adminServeInfo;

    private final CommonConfiguration configuration;

    public AdminServer(AdminServerProperties properties) {
        configuration =
            ConfigService.getInstance().buildConfigInstance(CommonConfiguration.class);
        if (configuration == null) {
            throw new AdminServerRuntimeException(ErrorCode.STARTUP_CONFIG_MISS, "common configuration file miss");
        }
        this.adminServeInfo = new RegisterServerInfo();

        adminServeInfo.setHealth(true);
        adminServeInfo.setAddress(IPUtils.getLocalAddress() + ":" + properties.getPort());
        String name = Constants.ADMIN_SERVER_REGISTRY_NAME;
        if (StringUtils.isNotBlank(properties.getServiceName())) {
            name = properties.getServiceName();
        }
        adminServeInfo.setServiceName(name);
        registryService = RegistryFactory.getInstance(configuration.getEventMeshRegistryPluginType());
    }


    @Override
    public boolean createOrUpdateTask(Task task) {
        return false;
    }

    @Override
    public boolean deleteTask(Long id) {
        return false;
    }

    @Override
    public Task getTask(Long id) {
        return null;
    }

    @Override
    public PagedList<Task> getTaskPaged(Task task) {
        return null;
    }

    @Override
    public void reportHeartbeat(ReportHeartBeatRequest heartBeat) {

    }

    @Override
    @PostConstruct
    public void start() {
        if (configuration.isEventMeshRegistryPluginEnabled()) {
            registryService.init();
        }
    }

    @Override
    public void stop() {
        if (configuration.isEventMeshRegistryPluginEnabled()) {
            registryService.unRegister(adminServeInfo);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignore) {
                log.warn("interrupted when sleep");
                Thread.currentThread().interrupt();
            }
            registryService.shutdown();
        }
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (configuration.isEventMeshRegistryPluginEnabled()) {
            log.info("application is started and registry plugin is enabled, it's will register admin self");
            registryService.register(adminServeInfo);
        }
    }
}
