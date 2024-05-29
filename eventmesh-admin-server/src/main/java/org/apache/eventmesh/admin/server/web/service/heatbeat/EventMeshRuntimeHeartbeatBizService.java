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

package org.apache.eventmesh.admin.server.web.service.heatbeat;

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHeartbeat;
import org.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHistory;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshRuntimeHeartbeatService;
import org.apache.eventmesh.admin.server.web.db.service.EventMeshRuntimeHistoryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @description 针对表【event_mesh_runtime_heartbeat】的数据库操作Service实现
 * @createDate 2024-05-14 17:15:03
 */
@Service
@Slf4j
public class EventMeshRuntimeHeartbeatBizService {

    @Autowired
    EventMeshRuntimeHistoryService historyService;

    @Autowired
    EventMeshRuntimeHeartbeatService heartbeatService;

    public boolean saveOrUpdateByRuntimeAddress(EventMeshRuntimeHeartbeat entity) {
        EventMeshRuntimeHeartbeat old = heartbeatService.getOne(Wrappers.<EventMeshRuntimeHeartbeat>query().eq(
            "runtimeAddr",
            entity.getRuntimeAddr()));
        if (old == null) {
            return heartbeatService.save(entity);
        } else {
            if (Long.parseLong(old.getReportTime()) >= Long.parseLong(entity.getReportTime())) {
                log.info("update heartbeat record ignore, current report time late than db, job " +
                    "[{}], remote [{}]", entity.getJobID(), entity.getRuntimeAddr());
                return true;
            }
            try {
                return heartbeatService.update(entity, Wrappers.<EventMeshRuntimeHeartbeat>update().eq("updateTime",
                    old.getUpdateTime()));
            } finally {
                if (old.getJobID() != null && !old.getJobID().equals(entity.getJobID())) {
                    EventMeshRuntimeHistory history = new EventMeshRuntimeHistory();
                    history.setAddress(old.getAdminAddr());
                    history.setJob(old.getJobID());
                    try {
                        historyService.save(history);
                    } catch (Exception e) {
                        log.warn("save runtime job changed history fail", e);
                    }

                    log.info("runtime [{}] changed job, old job [{}], now [{}]", entity.getRuntimeAddr(), old.getJobID(),
                        entity.getJobID());
                }
            }
        }
    }
}




