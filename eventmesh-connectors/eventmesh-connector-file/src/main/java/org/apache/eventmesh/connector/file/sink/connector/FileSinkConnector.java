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

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.file.FileSinkConfig;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileSinkConnector implements Sink {

    private static final AtomicInteger fileSize = new AtomicInteger(0);

    private String filePath;

    private String fileName;

    private int flushSize;

    private boolean hourlyFlushEnabled;

    private FileSinkConfig sinkConfig;

    private PrintStream outputStream;

    @Override
    public Class<? extends Config> configClass() {
        return FileSinkConfig.class;
    }

    @Override
    public void init(Config config) {
        // init config for hdfs source connector
        this.sinkConfig = (FileSinkConfig) config;
        this.filePath = buildFilePath();
        this.fileName = buildFileName();
        this.flushSize = sinkConfig.getFlushSize();
        this.hourlyFlushEnabled = sinkConfig.isHourlyFlushEnabled();
    }

    @Override
    public void init(ConnectorContext connectorContext) {
        // init config for hdfs source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (FileSinkConfig) sinkConnectorContext.getSinkConfig();
        this.fileName = buildFileName();
        this.filePath = buildFilePath();
        this.flushSize = sinkConfig.getFlushSize();
        this.hourlyFlushEnabled = sinkConfig.isHourlyFlushEnabled();
    }

    @Override
    public void start() throws Exception {
        if (fileName == null || fileName.length() == 0 || filePath == null || filePath.length() == 0) {
            this.outputStream = System.out;
        } else {
            this.outputStream =
                new PrintStream(Files.newOutputStream(Paths.get(filePath + fileName), StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                    false, StandardCharsets.UTF_8.name());
        }
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        outputStream.flush();
        outputStream.close();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            // the file data exceed the flushSize create the new file or
            // hourlyFlushEnabled && time on the hour
            if (fileSize.get() >= flushSize || (hourlyFlushEnabled && LocalDateTime.now().getHour() == 0)) {
                log.info("flush the file and open");
                outputStream.flush();
                outputStream.close();
                try {
                    fileSize.set(0);
                    this.outputStream = openWithNewFile();
                } catch (IOException e) {
                    log.error("create file under path {} error", filePath);
                    throw new RuntimeException(e);
                }
            }
            outputStream.println(new String((byte[]) connectRecord.getData(), StandardCharsets.UTF_8));
            fileSize.addAndGet(1);
        }
    }

    private String buildFilePath() {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DATE);
        String filePath = sinkConfig.getConnectorConfig().getTopic()
            + File.separator + year + File.separator + month + File.separator + day + File.separator;
        File path = new File(filePath);
        if (!path.exists()) {
            if (!path.mkdirs()) {
                log.error("make file dir {} error", filePath);
            }
        }
        return filePath;
    }

    private String buildFileName() {
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        long currentTime = calendar.getTime().getTime();
        return sinkConfig.getConnectorConfig().getTopic() + "-" + calendar.get(Calendar.HOUR_OF_DAY) + "-" + currentTime;
    }

    private PrintStream openWithNewFile() throws IOException {
        this.filePath = buildFilePath();
        this.fileName = buildFileName();
        if (fileName.length() == 0 || filePath == null || filePath.length() == 0) {
            return System.out;
        }
        return new PrintStream(Files.newOutputStream(Paths.get(filePath + fileName),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND),
            false, StandardCharsets.UTF_8.name());
    }
}
