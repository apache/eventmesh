package org.apache.eventmesh.api.registry.bo;

import java.util.Set;

public class EventMeshServicePubTopicInfo {

    String service;
    Set<String> topics;
    String token;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public Set<String> getTopics() {
        return topics;
    }

    public void setTopics(Set<String> topics) {
        this.topics = topics;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public String toString() {
        return "EventMeshServicePubTopicInfo{"
            + "service='" + service + '\''
            + ", topics=" + topics
            + '}';
    }
}