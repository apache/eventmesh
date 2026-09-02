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

package org.apache.eventmesh.connector.file.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.cloudevents.CloudEvent;

class FileSourceConnectorTest {

    /**
     * Three lines in the source file → first poll() returns 3 CloudEvents.
     */
    @Test
    void pollReadsLinesAsCloudEvents(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("source.txt");
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("alpha");
            w.newLine();
            w.write("beta");
            w.newLine();
            w.write("gamma");
        }

        FileSourceConnector connector = new FileSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        connector.init(props);
        try {
            List<CloudEvent> batch = connector.poll();

            assertEquals(3, batch.size(), "all three lines poll into the batch");
            for (CloudEvent event : batch) {
                assertEquals("file.line", event.getType());
                assertEquals("text/plain", event.getDataContentType());
                assertNotNull(event.getId());
            }
            String firstData = new String(batch.get(0).getData().toBytes(), StandardCharsets.UTF_8);
            String secondData = new String(batch.get(1).getData().toBytes(), StandardCharsets.UTF_8);
            String thirdData = new String(batch.get(2).getData().toBytes(), StandardCharsets.UTF_8);
            assertEquals("alpha", firstData);
            assertEquals("beta", secondData);
            assertEquals("gamma", thirdData);
        } finally {
            // Avoid Windows file lock that blocks TempDir cleanup.
            connector.closeReaderQuietly();
        }
    }

    /**
     * Empty file → empty batch (no NPE).
     */
    @Test
    void pollOnEmptyFileReturnsEmptyBatch(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("empty.txt");
        Files.createFile(file);

        FileSourceConnector connector = new FileSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        connector.init(props);
        try {
            List<CloudEvent> batch = connector.poll();

            assertTrue(batch.isEmpty());
        } finally {
            connector.closeReaderQuietly();
        }
    }

    /**
     * Two consecutive polls drain it line-by-line.
     */
    @Test
    void consecutivePollsDrainFile(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("twolines.txt");
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("one");
            w.newLine();
            w.write("two");
        }

        FileSourceConnector connector = new FileSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        connector.init(props);
        try {
            List<CloudEvent> first = connector.poll();
            List<CloudEvent> second = connector.poll();

            assertEquals(2, first.size());
            assertTrue(second.isEmpty(), "all lines read in first pass; second poll returns empty");
        } finally {
            connector.closeReaderQuietly();
        }
    }

    /**
     * poll() called before init() returns empty list rather than NPE.
     */
    @Test
    void pollBeforeInitReturnsEmpty() {
        FileSourceConnector connector = new FileSourceConnector();
        assertTrue(connector.poll().isEmpty());
    }

    /**
     * commit() is a no-op; the runtime only requires the contract to exist.
     */
    @Test
    void commitIsNoOp(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("noop.txt");
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("x");
        }
        FileSourceConnector connector = new FileSourceConnector();
        Properties props = new Properties();
        props.setProperty("connector.filePath", file.toAbsolutePath().toString());
        connector.init(props);
        try {
            // Should not throw; behaviour is intentionally a no-op.
            CloudEvent event = connector.poll().get(0);
            connector.commit(event);
        } finally {
            connector.closeReaderQuietly();
        }
    }
}