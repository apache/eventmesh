package org.apache.eventmesh.connector.standalone.admin;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;

import io.cloudevents.CloudEvent;
import io.cloudevents.CloudEventData;
import io.cloudevents.SpecVersion;
import io.cloudevents.core.data.BytesCloudEventData;

public class MockCloudEvent implements CloudEvent {

    @Override
    public CloudEventData getData() {
        return BytesCloudEventData.wrap("mockData".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public SpecVersion getSpecVersion() {
        return SpecVersion.V1;
    }

    @Override
    public String getId() {
        return "mockId";
    }

    @Override
    public String getType() {
        return "mockType";
    }

    @Override
    public URI getSource() {
        return URI.create("mockSource");
    }

    @Override
    public String getDataContentType() {
        return null;
    }

    @Override
    public URI getDataSchema() {
        return URI.create("mockDataSchema");
    }

    @Override
    public String getSubject() {
        return "mockSubject";
    }

    @Override
    public OffsetDateTime getTime() {
        return null;
    }

    @Override
    public Object getAttribute(String attributeName) throws IllegalArgumentException {
        return null;
    }

    @Override
    public Object getExtension(String extensionName) {
        return null;
    }

    @Override
    public Set<String> getExtensionNames() {
        return Collections.emptySet();
    }
}
