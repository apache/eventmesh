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

package org.apache.eventmesh.connector.file.source.connector;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.eventmesh.common.config.connector.file.FileSourceConfig;
import org.apache.eventmesh.common.config.connector.file.SourceConnectorConfig;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class FileSourceConnectorTest {

    private FileSourceConnector fileSourceConnector;
    @Mock
    private FileSourceConfig fileSourceConfig;

    @Test
    void testFileSourceConnector() throws Exception {
        String directoryPath = "d/g/";
        Path directory = Paths.get(directoryPath);
        Files.createDirectories(directory);
        Path newFilePath = directory.resolve("foo.txt");
        Files.createFile(newFilePath);
        fileSourceConfig = mock(FileSourceConfig.class);
        SourceConnectorConfig connectorConfig = mock(SourceConnectorConfig.class);
        when(fileSourceConfig.getConnectorConfig()).thenReturn(connectorConfig);
        when(fileSourceConfig.getConnectorConfig().getFilePath()).thenReturn("d/g/foo.txt");
        String filePath = fileSourceConfig.getConnectorConfig().getFilePath();
        Path mockPath = Paths.get(filePath);
        String content = "line1\nline2\nline3";
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        Files.write(mockPath, contentBytes);
        fileSourceConnector = new FileSourceConnector();
        fileSourceConnector.init(fileSourceConfig);
        fileSourceConnector.start();
        List<ConnectRecord> connectRecords = fileSourceConnector.poll();
        fileSourceConnector.stop();
        Files.delete(newFilePath);
        Assertions.assertEquals(content, connectRecords.get(0).getData().toString());
    }
}
