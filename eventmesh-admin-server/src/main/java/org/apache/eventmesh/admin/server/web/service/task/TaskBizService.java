package org.apache.eventmesh.admin.server.web.service.task;

import org.apache.eventmesh.admin.server.web.db.service.EventMeshTaskInfoService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskBizService {
    @Autowired
    private EventMeshTaskInfoService taskInfoService;


}
