package com.apache.eventmesh.admin.server.web.db.service.impl;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshRuntimeHistory;
import com.apache.eventmesh.admin.server.web.db.mapper.EventMeshRuntimeHistoryMapper;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshRuntimeHistoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
* @author sodafang
* @description 针对表【event_mesh_runtime_history(记录runtime上运行任务的变更)】的数据库操作Service实现
* @createDate 2024-05-14 17:15:03
*/
@Service
public class EventMeshRuntimeHistoryServiceImpl extends ServiceImpl<EventMeshRuntimeHistoryMapper, EventMeshRuntimeHistory>
    implements EventMeshRuntimeHistoryService{

}




