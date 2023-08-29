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

package org.apache.eventmesh.connector.knative.sink.connector;

import org.apache.eventmesh.connector.knative.sink.config.KnativeSinkConfig;
import org.apache.eventmesh.openconnect.api.config.Config;
import org.apache.eventmesh.openconnect.api.connector.ConnectorContext;
import org.apache.eventmesh.openconnect.api.connector.SinkConnectorContext;
import org.apache.eventmesh.openconnect.api.sink.Sink;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;
import org.apache.eventmesh.openconnect.util.CloudEventUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cloudevents.CloudEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KnativeSinkConnector implements Sink {

    private KnativeSinkConfig sinkConfig;

    private static final AtomicBoolean started = new AtomicBoolean(false);

    @Override
    public Class<? extends Config> configClass() {
        return KnativeSinkConfig.class;
    }

    @Override
    public void init(Config config) throws Exception {
    }

    @Override
    public void init(ConnectorContext connectorContext) throws Exception {
        SinkConnectorContext sinkConnectorContext = (SinkConnectorContext) connectorContext;
        this.sinkConfig = (KnativeSinkConfig) sinkConnectorContext.getSinkConfig();
    }

    @Override
    public void start() throws Exception {
        started.compareAndSet(false, true);
    }

    @Override
    public void commit(ConnectRecord record) {

    }

    @Override
    public String name() {
        return this.sinkConfig.getConnectorConfig().getConnectorName();
    }

    @Override
    public void stop() {
        started.compareAndSet(true, false);
    }

    @Override
    public void put(List<ConnectRecord> sinkRecords) {
        for (ConnectRecord connectRecord : sinkRecords) {
            CloudEvent cloudEvent = CloudEventUtil.convertRecordToEvent(connectRecord);
//            try {
//
//            } catch (InterruptedException e) {
//                Thread currentThread = Thread.currentThread();
//                log.warn("[KnativeSinkConnector] Interrupting thread {} due to exception {}", currentThread.getName(), e.getMessage());
//                currentThread.interrupt();
//            } catch (Exception e) {
//                log.error("[KnativeSinkConnector] sendResult has error : ", e);
//            }
        }
    }
}
