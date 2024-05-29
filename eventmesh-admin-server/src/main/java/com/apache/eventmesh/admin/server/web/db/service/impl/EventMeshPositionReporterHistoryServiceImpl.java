package com.apache.eventmesh.admin.server.web.db.service.impl;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshPositionReporterHistory;
import com.apache.eventmesh.admin.server.web.db.mapper.EventMeshPositionReporterHistoryMapper;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshPositionReporterHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
* @author sodafang
* @description 针对表【event_mesh_position_reporter_history(记录position上报者变更时，老记录)】的数据库操作Service实现
* @createDate 2024-05-14 17:15:03
*/
@Service
public class EventMeshPositionReporterHistoryServiceImpl extends ServiceImpl<EventMeshPositionReporterHistoryMapper, EventMeshPositionReporterHistory>
    implements EventMeshPositionReporterHistoryService{

}




