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

<<<<<<<< HEAD:eventmesh-common/src/main/java/org/apache/eventmesh/common/protocol/tcp/Package.java
package org.apache.eventmesh.common.protocol.tcp;


import org.apache.eventmesh.common.protocol.ProtocolTransportObject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class Package implements ProtocolTransportObject {

    private Header header;
    private Object body;

    public Package(Header header) {
        this.header = header;
========
package org.apache.eventmesh.runtime.metrics.http;

import com.codahale.metrics.MetricRegistry;

import org.apache.eventmesh.runtime.boot.EventMeshHTTPServer;

public class TopicMetrics {

    private EventMeshHTTPServer eventMeshHTTPServer;
    private MetricRegistry metricRegistry;

    public TopicMetrics(EventMeshHTTPServer eventMeshHTTPServer, MetricRegistry metricRegistry) {
        this.eventMeshHTTPServer = eventMeshHTTPServer;
        this.metricRegistry = metricRegistry;
>>>>>>>> e4cff57da85093ca7a917f7edd86fa434000d5dc:eventmesh-runtime/src/main/java/org/apache/eventmesh/runtime/metrics/http/TopicMetrics.java
    }

}
