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

package org.apache.eventmesh.connector;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

/**
 * Connector definition received from the runtime via {@code /control/start}. Mirrors the runtime's
 * {@code ConnectorDef} JSON shape — the two modules share only the JSON contract, not code.
 *
 * @see org.apache.eventmesh.connector.ConnectorManager#startConnector
 */
@Data
public class ConnectorDef {

    private String id;
    private String className;
    private String mode;
    private String topic;
    private String clientId;
    private String sinkClass;
    private Map<String, String> config = new LinkedHashMap<>();
}
