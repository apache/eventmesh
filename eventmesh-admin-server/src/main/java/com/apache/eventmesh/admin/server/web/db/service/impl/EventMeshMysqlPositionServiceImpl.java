package com.apache.eventmesh.admin.server.web.db.service.impl;

import com.apache.eventmesh.admin.server.web.db.entity.EventMeshMysqlPosition;
import com.apache.eventmesh.admin.server.web.db.mapper.EventMeshMysqlPositionMapper;
import com.apache.eventmesh.admin.server.web.db.service.EventMeshMysqlPositionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
* @author sodafang
* @description 针对表【event_mesh_mysql_position】的数据库操作Service实现
* @createDate 2024-05-14 17:15:03
*/
@Service
@Slf4j
public class EventMeshMysqlPositionServiceImpl extends ServiceImpl<EventMeshMysqlPositionMapper, EventMeshMysqlPosition>
    implements EventMeshMysqlPositionService{
}




