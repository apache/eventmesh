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

package org.apache.eventmesh.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.eventmesh.connector.SinkConnector;
import org.apache.eventmesh.connector.SourceConnector;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

/** Covers the sink (write) and source (read) adapter shapes against fake connectors. */
class ConnectorToolAdapterTest {

    private static final java.util.UUID ID = java.util.UUID.randomUUID();

    static final class RecordingSink implements SinkConnector {

        volatile List<CloudEvent> lastWritten;

        @Override
        public void init(Properties props) {
        }

        @Override
        public void put(List<CloudEvent> events) {
            lastWritten = events;
        }

        @Override
        public void commit(List<CloudEvent> written) {
        }
    }

    static final class OneBatchSource implements SourceConnector {

        volatile boolean polled;

        @Override
        public void init(Properties props) {
        }

        @Override
        public List<CloudEvent> poll() {
            if (polled) {
                return List.of();
            }
            polled = true;
            return List.of(
                CloudEventBuilder.v1().withId(ID.toString()).withSource(java.net.URI.create("urn:test"))
                    .withType("t1").withDataContentType("application/json")
                    .withData("{\"k\":\"v1\"}".getBytes(StandardCharsets.UTF_8)).build(),
                CloudEventBuilder.v1().withId(ID.toString()).withSource(java.net.URI.create("urn:test"))
                    .withType("t2").withDataContentType("application/json")
                    .withData("{\"k\":\"v2\"}".getBytes(StandardCharsets.UTF_8)).build());
        }

        @Override
        public void commit(CloudEvent lastPublished) {
        }
    }

    @Test
    void sinkToolDeliversArgsAsCloudEvent() throws Exception {
        AgentTool tool = ConnectorToolAdapter.sinkTool("notify",
            "Deliver a payload", RecordingSink.class, new Properties());
        assertThat(tool.name()).isEqualTo("notify");
        assertThat(tool.parametersJsonSchema()).contains("\"object\"");

        String result = tool.invoke(Map.of("text", "high risk order"));
        assertThat(result).isEqualTo("delivered");
    }

    @Test
    void sinkToolWritesTheArgumentsObjectAsEventData() throws Exception {
        RecordingSink sink = new RecordingSink();
        AgentTool tool = ConnectorToolAdapter.sinkTool("notify", "d", sink);
        tool.invoke(Map.of("order", 42, "risk", "high"));
        assertThat(sink.lastWritten).hasSize(1);
        assertThat(new String(sink.lastWritten.get(0).getData().toBytes(), StandardCharsets.UTF_8))
            .contains("\"order\":42").contains("\"risk\":\"high\"");
    }

    @Test
    void sourceToolReturnsBatchAsJsonArray() throws Exception {
        AgentTool tool = ConnectorToolAdapter.sourceTool("poll-orders",
            "Poll the next batch", OneBatchSource.class, new Properties());
        String first = tool.invoke(Map.of());
        // payloads are JSON strings inside a JSON array, so inner quotes are escaped
        assertThat(first).contains("{\\\"k\\\":\\\"v1\\\"}").contains("{\\\"k\\\":\\\"v2\\\"}");
        String second = tool.invoke(Map.of());
        assertThat(second).isEqualTo("[]");
    }
}
