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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.eventmesh.sink.connector.rocketmq.config;

import org.apache.eventmesh.connector.api.config.SinkConfig;

public class RocketMQSinkConfig extends SinkConfig {

    String connectorName;

    String sinkNameserver;

    String sinkTopic;

    String sinkGroup;

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public String getSinkNameserver() {
        return sinkNameserver;
    }

    public void setSinkNameserver(String sinkNameserver) {
        this.sinkNameserver = sinkNameserver;
    }

    public String getSinkTopic() {
        return sinkTopic;
    }

    public void setSinkTopic(String sinkTopic) {
        this.sinkTopic = sinkTopic;
    }

    public String getSinkGroup() {
        return sinkGroup;
    }

    public void setSinkGroup(String sinkGroup) {
        this.sinkGroup = sinkGroup;
    }
}
