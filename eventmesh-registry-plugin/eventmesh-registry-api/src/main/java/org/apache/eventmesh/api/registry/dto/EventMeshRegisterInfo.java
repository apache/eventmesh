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

package org.apache.eventmesh.api.registry.dto;

import java.util.Map;

/**
 * EventMeshRegisterInfo
 */
public class EventMeshRegisterInfo {
    private String eventMeshClusterName;
    private String eventMeshName;
    private String endPoint;
    private Map<String, Map<String, Integer>> eventMeshInstanceNumMap;

    private Map<String, String> metadata;

    private String protocolType;

    public String getEventMeshClusterName() {
        return eventMeshClusterName;
    }

    public void setEventMeshClusterName(String eventMeshClusterName) {
        this.eventMeshClusterName = eventMeshClusterName;
    }

    public String getEventMeshName() {
        return eventMeshName;
    }

    public void setEventMeshName(String eventMeshName) {
        this.eventMeshName = eventMeshName;
    }

    public String getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(String endPoint) {
        this.endPoint = endPoint;
    }

    public Map<String, Map<String, Integer>> getEventMeshInstanceNumMap() {
        return eventMeshInstanceNumMap;
    }

    public void setEventMeshInstanceNumMap(Map<String, Map<String, Integer>> eventMeshInstanceNumMap) {
        this.eventMeshInstanceNumMap = eventMeshInstanceNumMap;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String getProtocolType() {
        return protocolType;
    }

    public void setProtocolType(String protocolType) {
        this.protocolType = protocolType;
    }
}
