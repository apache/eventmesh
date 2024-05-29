package com.apache.eventmesh.admin.server.web.db.mapper;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHeartbeat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author sodafang
* @description 针对表【event_mesh_runtime_heartbeat】的数据库操作Mapper
* @createDate 2024-05-14 17:15:03
* @Entity com.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHeartbeat
*/
@Mapper
public interface EventMeshRuntimeHeartbeatMapper extends BaseMapper<EventMeshRuntimeHeartbeat> {

}




