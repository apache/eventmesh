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

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.file.FileSourceConfig;
import org.apache.eventmesh.common.remote.offset.RecordPartition;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.common.remote.offset.file.FileRecordPartition;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileSourceConnector implements Source {
    private static final int BUFFER_SIZE = 8192;
    private FileSourceConfig sourceConfig;
    private String filePath;
    private String fileName;
    private BufferedReader bufferedReader;

    @Override
    public Class<? extends Config> configClass() {
        return FileSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for hdfs source connector
        this.sourceConfig = (FileSourceConfig) config;
        this.filePath = ((FileSourceConfig) config).getConnectorConfig().getFilePath();
        this.fileName = getFileName(filePath);
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (FileSourceConfig) sourceConnectorContext.getSourceConfig();
    }

    @Override
    public void start() throws Exception {
        if (filePath == null || filePath.isEmpty()) {
            this.bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        } else {
            Path path = Paths.get(filePath);
            this.bufferedReader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8), BUFFER_SIZE);
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sourceConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        } catch (Exception e) {
            log.error("Error closing resources: {}", e.getMessage());
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>();
        RecordPartition recordPartition = convertToRecordPartition(fileName);
        try {
            int bytesRead;
            char[] buffer = new char[BUFFER_SIZE];
            while ((bytesRead = bufferedReader.read(buffer)) != -1) {
                String line = new String(buffer, 0, bytesRead);
                long timeStamp = System.currentTimeMillis();
                ConnectRecord connectRecord = new ConnectRecord(recordPartition, null, timeStamp, line);
                connectRecords.add(connectRecord);
            }
        } catch (IOException e) {
            log.error("Error reading data from the file: {}", e.getMessage());
        }
        return connectRecords;
    }

    public static RecordPartition convertToRecordPartition(String fileName) {
        FileRecordPartition fileRecordPartition = new FileRecordPartition();
        fileRecordPartition.setFileName(fileName);
        fileRecordPartition.setClazz(fileRecordPartition.getRecordPartitionClass());
        return fileRecordPartition;
    }

    private static String getFileName(String filePath) throws NullPointerException {
        File file = new File(filePath);
        return file.getName();
    }
}
