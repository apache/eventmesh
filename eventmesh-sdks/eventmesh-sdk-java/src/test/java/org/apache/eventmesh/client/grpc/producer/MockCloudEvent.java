/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.eventmesh.client.grpc.producer;

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
