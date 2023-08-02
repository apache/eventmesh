package org.apache.eventmesh.admin.api.service;

import org.apache.eventmesh.admin.api.response.TopicResponse;

public interface AdminService {

    TopicResponse createTopic(String topic);
}
