///*
// * Licensed to the Apache Software Foundation (ASF) under one or more
// * contributor license agreements.  See the NOTICE file distributed with
// * this work for additional information regarding copyright ownership.
// * The ASF licenses this file to You under the Apache License, Version 2.0
// * (the "License"); you may not use this file except in compliance with
// * the License.  You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.apache.eventmesh.common.protocol.tcp;
//
//import org.apache.rocketmq.common.DataVersion;
//
//public class EventMeshClientInfo {
//    private String clientId;
//    private String consumerGroup;
//    private String endpoint;
//    private String language;
//    private long version;
//    private DataVersion dataVersion;
//    private long lastUpdateTimestamp;
//    private int protocolNumber;
//
//    public EventMeshClientInfo(String clientId, String consumerGroup, String endpoint, String language, long version,
//                               DataVersion dataVersion, long lastUpdateTimestamp, int protocolNumber) {
//        this.clientId = clientId;
//        this.endpoint = endpoint;
//        this.language = language;
//        this.version = version;
//        this.consumerGroup = consumerGroup;
//        this.dataVersion = dataVersion;
//        this.lastUpdateTimestamp = lastUpdateTimestamp;
//        this.protocolNumber = protocolNumber;
//    }
//
//    public void setClientId(String clientId) {
//        this.clientId = clientId;
//    }
//
//    public String getClientId() {
//        return clientId;
//    }
//
//    public void setDataVersion(DataVersion dataVersion) {
//        this.dataVersion = dataVersion;
//    }
//
//    public void setEndpoint(String endpoint) {
//        this.endpoint = endpoint;
//    }
//
//    public DataVersion getDataVersion() {
//        return dataVersion;
//    }
//
//    public String getEndpoint() {
//        return endpoint;
//    }
//
//    public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
//        this.lastUpdateTimestamp = lastUpdateTimestamp;
//    }
//
//    public long getLastUpdateTimestamp() {
//        return lastUpdateTimestamp;
//    }
//
//    public void setConsumerGroup(String consumerGroup) {
//        this.consumerGroup = consumerGroup;
//    }
//
//    public String getConsumerGroup() {
//        return consumerGroup;
//    }
//
//    public void setVersion(long version) {
//        this.version = version;
//    }
//
//    public void setLanguage(String language) {
//        this.language = language;
//    }
//
//    public String getLanguage() {
//        return language;
//    }
//
//    public long getVersion() {
//        return version;
//    }
//
//    public void setProtocolNumber(int protocolNumber) {
//        this.protocolNumber = protocolNumber;
//    }
//
//    public int getProtocolNumber() {
//        return protocolNumber;
//    }
//
//    @Override
//    public String toString() {
//        return "ClientId [clientId=" + clientId + ", consumerGroup=" + consumerGroup + ", endpoint=" + endpoint
//        + ", language=" + language + ", version=" + version + ", dataVersion=" + dataVersion
//        + ", lastUpdateTimestamp=" + lastUpdateTimestamp + "]";
//    }
//}
