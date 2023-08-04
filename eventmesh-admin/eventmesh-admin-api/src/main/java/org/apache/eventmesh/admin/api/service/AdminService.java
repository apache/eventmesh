package org.apache.eventmesh.admin.api.service;

import org.apache.eventmesh.admin.api.response.TopicResponse;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

@EventMeshSPI(isSingleton = false, eventMeshExtensionType = EventMeshExtensionType.ADMIN)
public interface AdminService {

    TopicResponse createTopic(String topic);
}
