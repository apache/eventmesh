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

package org.apache.eventmesh.common.config.connector.mcp;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

@Data
public class SourceConnectorConfig {

    private String connectorName;

    private String path = "/";

    private int port;

    // timeunit: ms, default 5000ms
    private int idleTimeout = 5000;

    /**
     * <ul>
     *     <li>The maximum size allowed for form attributes when Content-Type is application/x-www-form-urlencoded or multipart/form-data </li>
     *     <li>Default is 1MB (1024 * 1024 bytes). </li>
     *     <li>If you receive a "size exceed allowed maximum capacity" error, you can increase this value. </li>
     *     <li>Note: This applies only when handling form data submissions.</li>
     * </ul>
     */
    private int maxFormAttributeSize = 1024 * 1024;

    // max size of the queue, default 1000
    private int maxStorageSize = 1000;

    // batch size, default 10
    private int batchSize = 10;

    // protocol, default CloudEvent
    private String protocol = "Mcp";

    // extra config, e.g. GitHub secret
    private Map<String, String> extraConfig = new HashMap<>();

    // data consistency enabled, default true
    private boolean dataConsistencyEnabled = false;

    private String forwardPath;
}
