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

package org.apache.eventmesh.trace.api.config;

import org.apache.eventmesh.common.config.Config;
import org.apache.eventmesh.common.config.ConfigFiled;
import org.apache.eventmesh.common.utils.PropertiesUtils;
import org.apache.eventmesh.trace.api.common.EventMeshTraceConstants;

import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * to load the properties form exporter.properties
 */
@Config(prefix = "eventmesh.trace", path = "classPath://exporter.properties")
public class ExporterConfiguration {

    @ConfigFiled(field = "max.export.size")
    private int eventMeshTraceMaxExportSize = 512;

    @ConfigFiled(field = "max.queue.size")
    private int eventMeshTraceMaxQueueSize = 2048;

    @ConfigFiled(field = "export.timeout")
    private int eventMeshTraceExportTimeout = 30;

    @ConfigFiled(field = "export.interval")
    private int eventMeshTraceExportInterval = 5;

    public int getEventMeshTraceMaxExportSize() {
        return eventMeshTraceMaxExportSize;
    }

    public void setEventMeshTraceMaxExportSize(int eventMeshTraceMaxExportSize) {
        this.eventMeshTraceMaxExportSize = eventMeshTraceMaxExportSize;
    }

    public int getEventMeshTraceMaxQueueSize() {
        return eventMeshTraceMaxQueueSize;
    }

    public void setEventMeshTraceMaxQueueSize(int eventMeshTraceMaxQueueSize) {
        this.eventMeshTraceMaxQueueSize = eventMeshTraceMaxQueueSize;
    }

    public int getEventMeshTraceExportTimeout() {
        return eventMeshTraceExportTimeout;
    }

    public void setEventMeshTraceExportTimeout(int eventMeshTraceExportTimeout) {
        this.eventMeshTraceExportTimeout = eventMeshTraceExportTimeout;
    }

    public int getEventMeshTraceExportInterval() {
        return eventMeshTraceExportInterval;
    }

    public void setEventMeshTraceExportInterval(int eventMeshTraceExportInterval) {
        this.eventMeshTraceExportInterval = eventMeshTraceExportInterval;
    }
}
