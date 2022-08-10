package org.apache.eventmesh.connector.standalone.admin;

import org.apache.eventmesh.api.admin.Admin;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

public class StandaloneAdminAdaptor implements Admin {

    private StandaloneAdmin admin;

    public StandaloneAdminAdaptor() {
    }

    @Override
    public boolean isStarted() {
        return admin.isStarted();
    }

    @Override
    public boolean isClosed() {
        return admin.isClosed();
    }

    @Override
    public void start() {
        admin.start();
    }

    @Override
    public void shutdown() {
        admin.shutdown();
    }

    @Override
    public void init(Properties keyValue) throws Exception {
        admin = new StandaloneAdmin(keyValue);
    }

    @Override
    public List<String> getTopic() throws Exception {
        return admin.getTopic();
    }

    @Override
    public List<CloudEvent> getEvent(String topicName, int offset, int length) throws Exception {
        return admin.getEvent(topicName, offset, length);
    }

    @Override
    public void publish(CloudEvent cloudEvent) throws Exception {
        admin.publish(cloudEvent);
    }
}
