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

import org.apache.eventmesh.connector.SinkConnector;
import org.apache.eventmesh.connector.SourceConnector;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Adapts the existing connector SPI ({@link SinkConnector} / {@link SourceConnector}) into
 * {@link AgentTool}s, turning every shipped connector plugin into a capability the LLM can call.
 *
 * <ul>
 *   <li>{@link #sinkTool}: a <b>write</b> tool — each invocation wraps the arguments object as one
 *       CloudEvent and pushes it through the sink ({@code put} + {@code commit}).</li>
 *   <li>{@link #sourceTool}: a <b>read</b> tool — each invocation polls one batch
 *       ({@code poll()}) and returns it as a JSON array of events.</li>
 * </ul>
 *
 * <p>The connector instance is initialized once and reused across invocations (same lifecycle as
 * inside the connector runtime).</p>
 */
public final class ConnectorToolAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String PERMISSIVE_SCHEMA = "{\"type\":\"object\",\"properties\":{},"
        + "\"additionalProperties\":true,\"description\":\"Arguments delivered as the event payload\"}";

    private ConnectorToolAdapter() {
    }

    /** Build a write tool on top of a {@link SinkConnector}; the connector is initialized eagerly. */
    public static AgentTool sinkTool(String name, String description, Class<? extends SinkConnector> sinkClass,
                                     Properties props) {
        return new SinkTool(name, description, newInstance(SinkConnector.class, sinkClass), props);
    }

    /** Build a write tool on top of a pre-built {@link SinkConnector} instance (already initialized). */
    public static AgentTool sinkTool(String name, String description, SinkConnector sink) {
        return new SinkTool(name, description, sink, null);
    }

    /** Build a read tool on top of a {@link SourceConnector}; the connector is initialized eagerly. */
    public static AgentTool sourceTool(String name, String description, Class<? extends SourceConnector> sourceClass,
                                       Properties props) {
        return new SourceTool(name, description, newInstance(SourceConnector.class, sourceClass), props);
    }

    /** Build a read tool on top of a pre-built {@link SourceConnector} instance (already initialized). */
    public static AgentTool sourceTool(String name, String description, SourceConnector source) {
        return new SourceTool(name, description, source, null);
    }

    private static <T> T newInstance(Class<T> spi, Class<? extends T> impl) {
        try {
            return impl.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("cannot instantiate connector " + impl.getName(), e);
        }
    }

    private static final class SinkTool implements AgentTool {

        private final String name;
        private final String description;
        private final SinkConnector sink;

        private SinkTool(String name, String description, SinkConnector sink, Properties props) {
            this.name = name;
            this.description = description;
            this.sink = sink;
            if (props != null) {
                sink.init(props);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String parametersJsonSchema() {
            return PERMISSIVE_SCHEMA;
        }

        @Override
        public String invoke(Map<String, Object> args) throws Exception {
            CloudEvent event = CloudEventBuilder.v1()
                .withId(UUID.randomUUID().toString())
                .withSource(URI.create("urn:eventmesh:agent-tool:" + name))
                .withType("org.apache.eventmesh.agent.tool.invoke")
                .withDataContentType("application/json")
                .withData(MAPPER.writeValueAsBytes(args == null ? Map.of() : args))
                .build();
            sink.put(List.of(event));
            sink.commit(List.of(event));
            return "delivered";
        }
    }

    private static final class SourceTool implements AgentTool {

        private final String name;
        private final String description;
        private final SourceConnector source;

        private SourceTool(String name, String description, SourceConnector source, Properties props) {
            this.name = name;
            this.description = description;
            this.source = source;
            if (props != null) {
                source.init(props);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String parametersJsonSchema() {
            return PERMISSIVE_SCHEMA;
        }

        @Override
        public String invoke(Map<String, Object> args) throws Exception {
            List<CloudEvent> batch = source.poll();
            List<String> payloads = new ArrayList<>();
            for (CloudEvent event : batch) {
                payloads.add(event.getData() == null ? "{}"
                    : new String(event.getData().toBytes(), StandardCharsets.UTF_8));
            }
            if (!batch.isEmpty()) {
                source.commit(batch.get(batch.size() - 1));
            }
            return MAPPER.writeValueAsString(payloads);
        }
    }
}
