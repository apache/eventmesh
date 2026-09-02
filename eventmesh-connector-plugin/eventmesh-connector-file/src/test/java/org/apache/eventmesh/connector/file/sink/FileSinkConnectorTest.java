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

package org.apache.eventmesh.connector.file.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

class FileSinkConnectorTest {

    /**
     * put() writes one line per event, in order, to the configured file.
     */
    @Test
    void putWritesEventsAsLines(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("sink.txt");
        FileSinkConnector sink = new FileSinkConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        sink.init(props);
        try {
            sink.put(Arrays.asList(event("alpha"), event("beta"), event("gamma")));

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(3, lines.size());
            assertEquals("alpha", lines.get(0));
            assertEquals("beta", lines.get(1));
            assertEquals("gamma", lines.get(2));
        } finally {
            sink.closeOutQuietly();
        }
    }

    /**
     * put() with an empty list is a no-op (writes nothing).
     */
    @Test
    void putWithEmptyListIsNoOp(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("sink-empty.txt");
        FileSinkConnector sink = new FileSinkConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        sink.init(props);
        try {
            sink.put(Collections.emptyList());

            assertTrue(Files.exists(file));
            assertEquals(0, Files.size(file));
        } finally {
            sink.closeOutQuietly();
        }
    }

    /**
     * put() with an event whose data is null writes an empty line (does not NPE).
     */
    @Test
    void putWithNullDataWritesEmptyLine(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("sink-null.txt");
        FileSinkConnector sink = new FileSinkConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        sink.init(props);
        try {
            sink.put(Collections.singletonList(event(null)));

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            assertEquals(1, lines.size());
            assertEquals("", lines.get(0));
        } finally {
            sink.closeOutQuietly();
        }
    }

    /**
     * init() with an explicit temp path must not throw.
     */
    @Test
    void initWithExplicitPathDoesNotThrow(@TempDir Path tmp) {
        Path file = tmp.resolve("sink-init.txt");
        FileSinkConnector sink = new FileSinkConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        try {
            // Should not throw.
            sink.init(props);
        } finally {
            sink.closeOutQuietly();
        }
    }

    /**
     * commit() is a no-op; the runtime only requires the contract to exist.
     */
    @Test
    void commitIsNoOp() {
        FileSinkConnector sink = new FileSinkConnector();
        sink.commit(Collections.emptyList());
        sink.commit(Arrays.asList(event("x")));
    }

    private static CloudEvent event(String data) {
        CloudEventBuilder b = CloudEventBuilder.v1()
            .withId("id-" + (data == null ? "null" : data))
            .withSource(URI.create("test"))
            .withType("test.type");
        if (data != null) {
            b.withDataContentType("text/plain").withData(data.getBytes(StandardCharsets.UTF_8));
        }
        return b.build();
    }
}