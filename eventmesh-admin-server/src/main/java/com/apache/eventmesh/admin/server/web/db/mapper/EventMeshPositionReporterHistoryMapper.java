package com.apache.eventmesh.admin.server.web.db.mapper;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshPositionReporterHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author sodafang
* @description 针对表【event_mesh_position_reporter_history(记录position上报者变更时，老记录)】的数据库操作Mapper
* @createDate 2024-05-14 17:15:03
* @Entity com.apache.eventmesh.admin.server.web.db.entity.EventMeshPositionReporterHistory
*/
@Mapper
public interface EventMeshPositionReporterHistoryMapper extends BaseMapper<EventMeshPositionReporterHistory> {

}




