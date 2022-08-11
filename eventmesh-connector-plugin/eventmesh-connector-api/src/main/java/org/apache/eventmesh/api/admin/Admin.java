package org.apache.eventmesh.api.admin;

import org.apache.eventmesh.api.LifeCycle;
import org.apache.eventmesh.spi.EventMeshExtensionType;
import org.apache.eventmesh.spi.EventMeshSPI;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

@EventMeshSPI(isSingleton = false, eventMeshExtensionType = EventMeshExtensionType.CONNECTOR)
public interface Admin extends LifeCycle {
    void init(Properties keyValue) throws Exception;

    List<TopicProperties> getTopic() throws Exception;

    void createTopic(String topicName) throws Exception;

    void deleteTopic(String topicName) throws Exception;

    List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception;

    void publish(CloudEvent cloudEvent) throws Exception;
}
