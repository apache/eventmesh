package org.apache.eventmesh.admin.server.web.db.service;

import org.apache.eventmesh.admin.server.web.db.entity.EventMeshJobInfo;

import java.util.List;

public interface EventMeshJobInfoExtService {
    int batchSave(List<EventMeshJobInfo> jobs);
}
