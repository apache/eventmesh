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

package org.apache.eventmesh.runtime.connector;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Data;

/**
 * Connector definition stored in Meta by {@link ConnectorScheduler} and pushed to workers over HTTP.
 * The worker (connector-runtime) reflects the same shape ({@code org.apache.eventmesh.connector.ConnectorDef})
 * — they share only the JSON contract, not code (the two modules are decoupled).
 *
 * <ul>
 *   <li>{@code className} — source connector class (also used as sink when {@code mode=both} and
 *       {@code sinkClass} is null).</li>
 *   <li>{@code mode} — {@code source} | {@code sink} | {@code both}.</li>
 *   <li>{@code topic} — source: topic to publish into EventMesh.</li>
 *   <li>{@code clientId} — sink: clientId to poll with.</li>
 *   <li>{@code sinkClass} — sink class (mode {@code sink}/ {@code both}).</li>
 *   <li>{@code config} — init properties forwarded to the connector's {@code init(Properties)}.</li>
 * </ul>
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
