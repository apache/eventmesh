package com.apache.eventmesh.admin.server.web.db.service.impl;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHeartbeat;
import com.apache.eventmesh.admin.server.web.db.mapper.EventMeshRuntimeHeartbeatMapper;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshRuntimeHeartbeatService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
* @author sodafang
* @description 针对表【event_mesh_runtime_heartbeat】的数据库操作Service实现
* @createDate 2024-05-14 17:15:03
*/
@Service
@Slf4j
public class EventMeshRuntimeHeartbeatServiceImpl extends ServiceImpl<EventMeshRuntimeHeartbeatMapper, EventMeshRuntimeHeartbeat>
    implements EventMeshRuntimeHeartbeatService{
}




