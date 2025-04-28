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

package org.apache.eventmesh.connector.file.sink.connector;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.config.connector.file.FileSinkConfig;
import org.apache.eventmesh.common.config.connector.file.SinkConnectorConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;


public class FileSinkConnectorTest {

    private FileSinkConnector fileSinkConnector;

    @Mock
    private FileSinkConfig fileSinkConfig;

    @Test
    void testFileSinkConnector() throws Exception {

        fileSinkConfig = mock(FileSinkConfig.class);
        SinkConnectorConfig connectorConfig = mock(SinkConnectorConfig.class);
        when(fileSinkConfig.getConnectorConfig()).thenReturn(connectorConfig);
        when(connectorConfig.getTopic()).thenReturn("test-topic");
        when(fileSinkConfig.getFlushSize()).thenReturn(10);
        when(fileSinkConfig.isHourlyFlushEnabled()).thenReturn(false);

        fileSinkConnector = new FileSinkConnector();
        fileSinkConnector.init(fileSinkConfig);
        fileSinkConnector.start();

        String content = "line1\nline2\nline3";
        ConnectRecord record = new ConnectRecord(null, null, System.currentTimeMillis(), content.getBytes(StandardCharsets.UTF_8));
        List<ConnectRecord> connectRecords = Collections.singletonList(record);
        fileSinkConnector.put(connectRecords);
        fileSinkConnector.stop();

        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DATE);
        Path topicPath = Paths.get("test-topic",
            String.valueOf(year),
            String.valueOf(month),
            String.valueOf(day));
        Assertions.assertTrue(Files.exists(topicPath), "Directory for topic should exist");

        Path outputPath = Files.list(topicPath)
            .filter(path -> path.toString().contains("test-topic"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No output file found with 'test-topic' in the name"));

        List<String> lines = Files.readAllLines(outputPath, StandardCharsets.UTF_8);
        String actualContent = String.join("\n", lines);
        Assertions.assertEquals(content, actualContent);

    }
}