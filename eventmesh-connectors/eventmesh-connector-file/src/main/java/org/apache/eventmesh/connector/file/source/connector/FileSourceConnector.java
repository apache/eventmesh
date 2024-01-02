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

import org.apache.eventmesh.connector.file.source.config.FileSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageReader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileSourceConnector implements Source {

    private FileSourceConfig sourceConfig;

    private OffsetStorageReader offsetStorageReader;

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
        this.filePath = buildFilePath(((FileSourceConfig) config).getConnectorConfig().getFilePath());
        this.fileName = buildFileName(((FileSourceConfig) config).getConnectorConfig().getFileName());
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (FileSourceConfig) sourceConnectorContext.getSourceConfig();
        this.offsetStorageReader = sourceConnectorContext.getOffsetStorageReader();
    }

    @Override
    public void start() throws Exception {
        if (fileName == null || fileName.isEmpty() || filePath == null || filePath.isEmpty()) {
            this.bufferedReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        } else {
            this.bufferedReader = Files.newBufferedReader(Paths.get(filePath, fileName), StandardCharsets.UTF_8);
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
        try {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                ConnectRecord connectRecord = new ConnectRecord(new RecordPartition(), new RecordOffset(), System.currentTimeMillis(), line);
                connectRecords.add(connectRecord);
            }
        } catch (IOException e) {
            log.error("Error reading data from the file: {}", e.getMessage());
        }
        return connectRecords;
    }

    private String buildFilePath(String filePath) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        String formattedDate = dateFormat.format(calendar.getTime());
        String filePath1 = filePath.replace("%s", formattedDate);
        File path = new File(filePath1);
        if (!path.exists()) {
            if (!path.mkdirs()) {
                log.error("make file dir {} error", filePath);
            }
        }
        return filePath1;
    }

    private String buildFileName(String fileName) {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        long currentTime = calendar.getTime().getTime();
        String formattedDate = calendar.get(Calendar.HOUR_OF_DAY) + "-" + currentTime;
        return fileName.replace("%s", formattedDate);
    }
}
