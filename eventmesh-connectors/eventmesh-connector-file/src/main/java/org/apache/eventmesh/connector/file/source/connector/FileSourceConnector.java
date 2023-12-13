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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import org.apache.eventmesh.connector.file.source.config.FileSourceConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SourceConnectorContext;
import org.apache.eventmesh.openconnect.api.source.Source;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordOffset;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.RecordPartition;
import org.apache.eventmesh.openconnect.offsetmgmt.api.storage.OffsetStorageReader;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileSourceConnector implements Source {

    private FileSourceConfig sourceConfig;

    private OffsetStorageReader offsetStorageReader;

    private String filePath;
    private String fileName;
    private InputStream inputStream;
    @Override
    public Class<? extends Config> configClass() {
        return FileSourceConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for hdfs source connector
        this.sourceConfig = (FileSourceConfig) config;
        this.filePath = buildFilePath();
        this.fileName = buildFileName();
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SourceConnectorContext sourceConnectorContext = (SourceConnectorContext) connectorContext;
        this.sourceConfig = (FileSourceConfig) sourceConnectorContext.getSourceConfig();
        this.offsetStorageReader = sourceConnectorContext.getOffsetStorageReader();

    }

    @Override
    public void start() throws Exception {
        if (fileName == null || fileName.length() == 0 || filePath == null || filePath.length() == 0){
            this.inputStream = System.in;
        }
        else{
            this.inputStream = Files.newInputStream(Paths.get(filePath+fileName), StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.READ);
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
            inputStream.close();
        } catch (Exception e){
            log.error("Error closing resources: {}", e.getMessage());
        }
    }

    @Override
    public List<ConnectRecord> poll() {
        List<ConnectRecord> connectRecords = new ArrayList<>();
        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream,StandardCharsets.UTF_8));
            String line;
            while ((line = bufferedReader.readLine())!=null){
                ConnectRecord connectRecord = new ConnectRecord(new RecordPartition(), new RecordOffset(),System.currentTimeMillis(),line);
                connectRecords.add(connectRecord);
            }
        } catch (IOException e){
            log.error("Error reading data from the file: {}",e.getMessage());
        }
        return connectRecords;
    }

    private String buildFileName(){
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        long currentTime = calendar.getTime().getTime();
        return sourceConfig.getConnectorConfig().getTopic()+"-"+calendar.get(Calendar.HOUR_OF_DAY)+"-"+currentTime;
    }
    private String buildFilePath(){
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DATE);
        String filePath = sourceConfig.getConnectorConfig().getTopic()+ File.separator+year+File.separator+month+File.separator+day+File.separator;
        File path = new File(filePath);
        if (!path.exists()){
            if(!path.mkdirs()){
                log.error("make file dir {} error", filePath);
            }
        }
        return filePath;
    }
}
