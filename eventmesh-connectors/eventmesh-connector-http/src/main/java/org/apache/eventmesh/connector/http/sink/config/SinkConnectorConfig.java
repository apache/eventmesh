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

package org.apache.eventmesh.connector.http.sink.config;

import lombok.Data;

@Data
public class SinkConnectorConfig {

    private String connectorName;

    private String host;

    private int port;

    private String path;

    // whether the connector is HTTPS connector
    private boolean ssl;

    // whether the connector is a webhook connector
    private boolean webhook;

    // timeunit: ms
    private int connectionTimeout;

    // timeunit: ms
    private int idleTimeout;


    /**
     * Fill in default values for fields that have no set values
     *
     * @param config SinkConnectorConfig
     */
    public static void fillDefault(SinkConnectorConfig config) {
        // Common HttpSinkHandler default values
        final int commonHttpIdleTimeout = 5000;
        final int commonHttpConnectionTimeout = 5000;

        // Webhook HttpSinkHandler default values
        final int webhookHttpIdleTimeout = 10000;
        final int webhookHttpConnectionTimeout = 15000;

        // Set default values for idleTimeout
        if (config.getIdleTimeout() == 0) {
            int idleTimeout = config.isWebhook() ? webhookHttpIdleTimeout : commonHttpIdleTimeout;
            config.setIdleTimeout(idleTimeout);
        }
        // Set default values for connectionTimeout
        if (config.getConnectionTimeout() == 0) {
            int connectionTimeout = config.isWebhook() ? webhookHttpConnectionTimeout : commonHttpConnectionTimeout;
            config.setConnectionTimeout(connectionTimeout);
        }
    }
}
