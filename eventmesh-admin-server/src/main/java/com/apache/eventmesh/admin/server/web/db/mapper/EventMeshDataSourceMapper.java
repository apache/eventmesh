package com.apache.eventmesh.admin.server.web.db.mapper;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshDataSource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author sodafang
* @description 针对表【event_mesh_data_source】的数据库操作Mapper
* @createDate 2024-05-09 15:52:49
* @Entity com.apache.eventmesh.admin.server.web.db.entity.EventMeshDataSource
*/
@Mapper
public interface EventMeshDataSourceMapper extends BaseMapper<EventMeshDataSource> {

}




