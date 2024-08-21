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

package org.apache.eventmesh.connector.canal.sink.connector;

import org.apache.eventmesh.common.config.connector.Config;
import org.apache.eventmesh.common.config.connector.rdb.canal.CanalSinkConfig;
import org.apache.eventmesh.common.remote.job.JobType;
import org.apache.eventmesh.openconnect.api.ConnectorCreateService;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CanalSinkConnector implements Sink, ConnectorCreateService<Sink> {

    private CanalSinkConfig sinkConfig;

    private Sink sink;

    @Override
    public Class<? extends Config> configClass() {
        return CanalSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
        // init config for canal source connector
        this.sinkConfig = (CanalSinkConfig) config;
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        // init config for canal source connector
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        if (sinkConnectorContext.getJobType().equals(JobType.FULL)) {
            this.sink = new CanalSinkFullConnector();
        } else if (sinkConnectorContext.getJobType().equals(JobType.INCREASE)) {
            this.sink = new CanalSinkIncrementConnector();
        } else if (sinkConnectorContext.getJobType().equals(JobType.CHECK)) {
            this.sink = new CanalSinkCheckConnector();
        } else {
            throw new RuntimeException("unsupported job type " + sinkConnectorContext.getJobType());
        }
        this.sink.init(sinkConnectorContext);
    }

    @Override
    public void start() throws Exception {
        this.sink.start();
    }

    @Override
    public void commit(ConnectRecord record) {
        this.sink.commit(record);
    }

    @Override
    public String name() {
        return this.sink.name();
    }

    @Override
    public void onException(ConnectRecord record) {
        this.sink.onException(record);
    }

    @Override
    public void stop() throws Exception {
        this.sink.stop();
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        this.sink.put(sinkRecords);
    }

    @Override
    public Sink create() {
        return new CanalSinkConnector();
    }

}
