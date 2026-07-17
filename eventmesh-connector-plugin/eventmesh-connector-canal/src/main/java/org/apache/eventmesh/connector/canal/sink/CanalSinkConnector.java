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

package org.apache.eventmesh.connector.canal.sink;

import org.apache.eventmesh.connector.SinkConnector;

import java.util.List;
import java.util.Properties;

import io.cloudevents.CloudEvent;

/**
 * Canal sink connector (new architecture stub). Implements {@link SinkConnector} directly.
 * TODO: implement put() with real canal client logic (reference: KafkaSinkConnector template).
 */
public class CanalSinkConnector implements SinkConnector {

    @Override
    public void init(Properties props) {
        // TODO: init canal client
    }

    @Override
    public void put(List<CloudEvent> events) {
        // TODO: write CloudEvents → canal
    }

    @Override
    public void commit(List<CloudEvent> written) {
        // TODO: checkpoint
    }
}
