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

package org.apache.eventmesh.connector.file.source.config;

import org.apache.eventmesh.openconnect.api.config.SourceConfig;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class FileSourceConfig extends SourceConfig {

    public SourceConnectorConfig connectorConfig;
    private String fileName;
    private String filePath;
    Calendar calendar = Calendar.getInstance(Locale.CHINA);

    public String getFileName() {
        long currentTime = calendar.getTime().getTime();
        return connectorConfig.getTopic() + "-" + calendar.get(Calendar.HOUR_OF_DAY) + "-" + currentTime;
    }

    public String getFilePath() {
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DATE);
        String filePath = connectorConfig.getTopic()
            + File.separator + year + File.separator + month + File.separator + day + File.separator;
        File path = new File(filePath);
        if (!path.exists()) {
            if (!path.mkdirs()) {
                log.error("make file dir {} error", filePath);
            }
        }
        return filePath;
    }

}
