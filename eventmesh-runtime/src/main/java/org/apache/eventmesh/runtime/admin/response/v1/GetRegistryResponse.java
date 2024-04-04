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

package org.apache.eventmesh.runtime.admin.response.v1;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GetRegistryResponse {

    private String eventMeshClusterName;
    private String eventMeshName;
    private String endpoint;
    private long lastUpdateTimestamp;
    private String metadata;

    @JsonCreator
    public GetRegistryResponse(
        @JsonProperty("eventMeshClusterName") String eventMeshClusterName,
        @JsonProperty("eventMeshName") String eventMeshName,
        @JsonProperty("endpoint") String endpoint,
        @JsonProperty("lastUpdateTimestamp") long lastUpdateTimestamp,
        @JsonProperty("metadata") String metadata) {
        super();
        this.eventMeshClusterName = eventMeshClusterName;
        this.eventMeshName = eventMeshName;
        this.endpoint = endpoint;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.metadata = metadata;
    }
}
