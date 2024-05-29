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

package org.apache.eventmesh.admin.server.web.service.position;

import lombok.extern.slf4j.Slf4j;

import org.apache.eventmesh.common.remote.job.DataSourceType;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PositionHandlerFactory implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<DataSourceType, PositionHandler> handlers =
        new ConcurrentHashMap<>();

    public PositionHandler getHandler(DataSourceType type) {
        return handlers.get(type);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        Map<String, PositionHandler> beans =
            event.getApplicationContext().getBeansOfType(PositionHandler.class);

        for (PositionHandler handler : beans.values()) {
            DataSourceType type = handler.getSourceType();
            if (handlers.containsKey(type)) {
                log.warn("data source type [{}] handler already exists", type);
                continue;
            }
            handlers.put(type, handler);
        }
    }
}
