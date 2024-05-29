package com.apache.eventmesh.admin.server.web.db.mapper;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author sodafang
* @description 针对表【event_mesh_job_info】的数据库操作Mapper
* @createDate 2024-05-09 15:51:45
* @Entity com.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo
*/
@Mapper
public interface EventMeshJobInfoMapper extends BaseMapper<EventMeshJobInfo> {

}




