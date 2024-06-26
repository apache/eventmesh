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

package org.apache.eventmesh.connector.http.source.protocol;

import org.apache.eventmesh.common.config.connector.http.SourceConnectorConfig;
import org.apache.eventmesh.connector.http.common.SynchronizedCircularFifoQueue;
import org.apache.eventmesh.openconnect.offsetmgmt.api.data.ConnectRecord;

import io.vertx.ext.web.Route;


/**
 * Protocol Interface.
 * All protocols should implement this interface.
 */
public interface Protocol {


    /**
     * Initialize the protocol.
     *
     * @param sourceConnectorConfig source connector config
     */
    void initialize(SourceConnectorConfig sourceConnectorConfig);


    /**
     * Handle the protocol message.
     *
     * @param route     route
     * @param queue queue info
     */
    void setHandler(Route route, SynchronizedCircularFifoQueue<Object> queue);


    /**
     * Convert the message to ConnectRecord.
     *
     * @param message message
     * @return ConnectRecord
     */
    ConnectRecord convertToConnectRecord(Object message);
}
