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

package org.apache.eventmesh.runtime.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EventMeshCloudEventWriterTest {

    @Test
    public void testURIAsValueWithContextAttribute() throws URISyntaxException {
        String key = "testKey";
        EventMeshCloudEventWriter eventMeshCloudEventWriter = new EventMeshCloudEventWriter();

        eventMeshCloudEventWriter.withContextAttribute(key, new URI("file://foo/bar"));

        Map<String, Object> extensionMap = eventMeshCloudEventWriter.getExtensionMap();
        Assertions.assertEquals(extensionMap.get(key), "file://foo/bar");
    }

    @Test
    public void testOffsetDateTimeAsValueWithContextAttribute() {
        String key = "testKey";
        EventMeshCloudEventWriter eventMeshCloudEventWriter = new EventMeshCloudEventWriter();

        eventMeshCloudEventWriter.withContextAttribute(key, OffsetDateTime.of(LocalDateTime.of(
            LocalDate.of(2023, 6, 17), LocalTime.MIDNIGHT), ZoneOffset.ofTotalSeconds(32400)));

        Map<String, Object> extensionMap = eventMeshCloudEventWriter.getExtensionMap();
        Assertions.assertEquals(extensionMap.get(key), "2023-06-17T00:00:00+09:00");
    }

    @Test
    public void testIntegerAsValueWithContextAttribute() {
        String key = "testKey";
        EventMeshCloudEventWriter eventMeshCloudEventWriter = new EventMeshCloudEventWriter();

        eventMeshCloudEventWriter.withContextAttribute(key, 123);

        Map<String, Object> extensionMap = eventMeshCloudEventWriter.getExtensionMap();
        Assertions.assertEquals(extensionMap.get(key), "123");
    }

    @Test
    public void testBooleanAsValueWithContextAttribute() {
        String key = "testKey";
        EventMeshCloudEventWriter eventMeshCloudEventWriter = new EventMeshCloudEventWriter();

        eventMeshCloudEventWriter.withContextAttribute(key, Boolean.FALSE);

        Map<String, Object> extensionMap = eventMeshCloudEventWriter.getExtensionMap();
        Assertions.assertEquals(extensionMap.get(key), "false");
    }

    @Test
    public void testByteArrayAsValueWithContextAttribute() {
        String key = "testKey";
        EventMeshCloudEventWriter eventMeshCloudEventWriter = new EventMeshCloudEventWriter();

        eventMeshCloudEventWriter.withContextAttribute(key, "bytesArray".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> extensionMap = eventMeshCloudEventWriter.getExtensionMap();
        String base64EncodedValue = "Ynl0ZXNBcnJheQ==";
        Assertions.assertEquals(extensionMap.get(key), base64EncodedValue);
    }

}
